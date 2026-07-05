package net.explorviz.code.analysis.service;

import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import net.explorviz.code.analysis.FileLanguageResolver;
import net.explorviz.code.analysis.exceptions.DebugFileWriter;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.exceptions.PropertyNotDefinedException;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.export.FileDataExportFilter;
import net.explorviz.code.analysis.git.GitMetricCollector;
import net.explorviz.code.analysis.git.GitRepositoryHandler;
import net.explorviz.code.analysis.git.RepositoryFileUrlBuilder;
import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import net.explorviz.code.analysis.handler.CommitReportHandler;
import net.explorviz.code.analysis.handler.FallbackFileDataHandlerFactory;
import net.explorviz.code.analysis.handler.TextFileDataHandler;
import net.explorviz.code.analysis.listener.CommonFileDataListener;
import net.explorviz.code.analysis.parser.AntlrCParserService;
import net.explorviz.code.analysis.parser.AntlrCSharpParserService;
import net.explorviz.code.analysis.parser.AntlrCppParserService;
import net.explorviz.code.analysis.parser.AntlrGoParserService;
import net.explorviz.code.analysis.parser.AntlrKotlinParserService;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.analysis.parser.AntlrPhpParserService;
import net.explorviz.code.analysis.parser.AntlrPythonParserService;
import net.explorviz.code.analysis.parser.AntlrRustParserService;
import net.explorviz.code.analysis.parser.AntlrSwiftParserService;
import net.explorviz.code.analysis.parser.AntlrTypeScriptParserService;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.Triple;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;
import net.explorviz.code.proto.StateData;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for analyzing Git repositories and extracting code metrics.
 */
@ApplicationScoped
public class AnalysisService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);
  private static final Set<String> TEXT_FILE_EXTENSIONS = Set.of(
      // Plain text & docs
      "txt", "md", "rst", "adoc", "log", "license", "notice", "readme",

      // Configuration formats
      "conf", "cfg", "ini", "properties", "prefs",
      "env", "dotenv",
      "toml",
      "yaml", "yml",
      "json",
      "xml",

      // Infrastructure / tooling configs
      "gradle",
      "editorconfig",
      "gitignore", "gitattributes", "gitmodules",
      "dockerignore",
      "npmrc", "yarnrc", "pnpmrc",
      "eslintrc", "prettierrc", "stylelintrc",
      "babelrc",
      "htaccess",

      // CI / automation
      "workflow",

      // Data & text-based assets
      "csv", "tsv", "sql",

      // System / service configs
      "service", "socket", "timer");
  @Inject
  /* package */ GitRepositoryHandler gitRepositoryHandler;
  @Inject
  /* package */ AntlrParserService antlrParserService;
  @Inject
  /* package */ AntlrTypeScriptParserService tsParserService;
  @Inject
  /* package */ AntlrPythonParserService pythonParserService;
  @Inject
  /* package */ AntlrCppParserService cppParserService;
  @Inject
  /* package */ AntlrCParserService antlrCParserService;
  @Inject
  /* package */ AntlrGoParserService goParserService;
  @Inject
  /* package */ AntlrCSharpParserService csharpParserService;
  @Inject
  /* package */ AntlrRustParserService rustParserService;
  @Inject
  /* package */ AntlrKotlinParserService kotlinParserService;
  @Inject
  /* package */ AntlrPhpParserService phpParserService;
  @Inject
  /* package */ AntlrSwiftParserService swiftParserService;
  @Inject
  /* package */ AnalysisStatusService analysisStatusService;
  @Inject
  /* package */ GithubCollaborationFetcherService socialFetcherService;
  @Inject
  /* package */ ManagedExecutor managedExecutor;
  @ConfigProperty(name = "explorviz.gitanalysis.save-crashed_files")
  /* default */ boolean saveCrashedFilesProperty;
  @ConfigProperty(name = "explorviz.gitanalysis.file-analysis-parallelism", defaultValue = "0")
  /* default */ int fileAnalysisParallelismProperty;
  @ConfigProperty(name = "explorviz.gitanalysis.file-persist-concurrency", defaultValue = "8")
  /* default */ int filePersistConcurrencyProperty;
  @ConfigProperty(name = "explorviz.gitanalysis.file-persist-batch-size", defaultValue = "50")
  /* default */ int filePersistBatchSizeProperty;
  @ConfigProperty(name = "explorviz.gitanalysis.run-mode", defaultValue = "api")
  /* default */ String runModeProperty;

  private static String toErrorText(final String position, final String commitId,
      final String branchName) {
    return "The given " + position + " commit <" + commitId
        + "> was not found in the current branch <" + branchName + ">";
  }

  /**
   * Analyzes a Git repository and sends the results using the provided exporter.
   *
   * @param config   The analysis configuration
   * @param exporter The data exporter to use for sending results
   * @throws IOException                 If an I/O error occurs
   * @throws GitAPIException             If a Git operation fails
   * @throws NotFoundException           If a required resource is not found
   * @throws PropertyNotDefinedException If a required property is not defined
   */
  public void analyzeAndSendRepo(final AnalysisConfig config, final DataExporter exporter) // NOCS
      throws IOException, GitAPIException, NotFoundException, PropertyNotDefinedException { // NOPMD

    // start social analysis to run async while repo is being cloned
    fetchSocialData(config, exporter);

    try (Repository repository = this.gitRepositoryHandler.getGitRepository(config)) {

      final String fullBranch = repository.getFullBranch();
      final String branch = repository.getBranch();
      final String repositoryUrl = resolveRepositoryUrl(config, repository);

      preInitializeRemoteState(config, exporter, branch, repositoryUrl);

      // get fetch data from remote
      final AnalysisStartContext analysisStartContext =
          resolveAnalysisStartContext(config, exporter, branch, repositoryUrl);
      final Optional<String> startCommit = analysisStartContext.startCommit();

      final Optional<String> endCommit = exporter.isRemote() ? Optional.empty() : config.endCommit();

      checkIfCommitsAreReachable(startCommit, endCommit, fullBranch);

      final List<RevCommit> commitsInRange = collectCommitsInRange(repository, fullBranch, startCommit,
          endCommit, exporter.isRemote(), config.firstParentCommitsOnly());
      final int totalCommitsInRange = commitsInRange.size();

      int commitsToAnalyze = totalCommitsInRange;

      // If cloneDepth has been applied, the first commit has no parent and should be
      // skipped.
      if (!exporter.isRemote() && totalCommitsInRange > 0) {
        commitsToAnalyze = Math.max(0, totalCommitsInRange - 1);
      }

      // Apply limit if present
      if (config.commitAnalysisLimit().isPresent() && config.commitAnalysisLimit().get() < commitsToAnalyze) {
        commitsToAnalyze = config.commitAnalysisLimit().get();
      }

      LOGGER.info("Total commits to analyze: {}", commitsToAnalyze);
      analysisStatusService.markRunning(config.landscapeToken(), commitsToAnalyze, 0);

      final int commitsToSkipBeforeAnalyzing = totalCommitsInRange - commitsToAnalyze;
      final Map<ObjectId, List<String>> tagsByCommitId = buildTagsByCommitId(repository);

      // Pre-compile patterns
      final List<java.nio.file.PathMatcher> restrictMatchers = compileMatchers(
          config.includeInAnalysisExpressions());
      final List<java.nio.file.PathMatcher> excludeMatchers = compileMatchers(
          config.excludeFromAnalysisExpressions());

      RevCommit lastCheckedCommit = resolveRemoteStartCommit(repository, startCommit, exporter.isRemote());
      int commitCount = 0;
      for (int index = 0; index < commitsInRange.size(); index++) {
        final RevCommit commit = commitsInRange.get(index);

        if (index < commitsToSkipBeforeAnalyzing) {
          lastCheckedCommit = advanceLastCheckedCommit(commit, lastCheckedCommit);
          continue;
        }

        if (commitCount >= commitsToAnalyze) {
          break;
        }

        LOGGER.atDebug().addArgument(commit.getName()).log("Analyzing commit: {}");

        final boolean isFirstAnalyzedCommit = commitCount == 0;
        final RevCommit baseCommit =
            resolveDiffBaseCommit(
                commit, commitCount, exporter.isRemote(), startCommit, lastCheckedCommit);

        if (isFirstAnalyzedCommit && baseCommit == null) {
          LOGGER.info(
              "First commit analyzed for repository {} on branch {} — analyzing all files",
              config.getRepositoryName(),
              branch);
        }

        final var reportTriple =
            gitRepositoryHandler.listDiff(
                repository, Optional.ofNullable(baseCommit), commit, config.pathRestrictionForDiff());

        final List<FileDescriptor> descriptorAddedList = new ArrayList<>(reportTriple.right()); // NOPMD
        final List<FileDescriptor> descriptorModifiedList = new ArrayList<>(reportTriple.left());
        final List<FileDescriptor> descriptorDeletedList = reportTriple.middle();

        applyGlobFiltering(descriptorAddedList, restrictMatchers, excludeMatchers);
        applyGlobFiltering(descriptorModifiedList, restrictMatchers, excludeMatchers);
        applyGlobFiltering(descriptorDeletedList, restrictMatchers, excludeMatchers);

        LOGGER.atDebug().addArgument(descriptorAddedList.size())
            .addArgument(descriptorModifiedList.size())
            .log("Files added: {}, files modified: {}");

        analysisStatusService.setCurrentCommitFiles(
            config.landscapeToken(),
            descriptorAddedList.size() + descriptorModifiedList.size());

        if (descriptorAddedList.isEmpty() && descriptorModifiedList.isEmpty()) {
          createCommitReport(
              config,
              commit,
              lastCheckedCommit,
              exporter,
              branch,
              descriptorAddedList,
              descriptorModifiedList,
              descriptorDeletedList,
              tagsByCommitId);

          commitCount++;
          analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());
          lastCheckedCommit = advanceLastCheckedCommit(commit, lastCheckedCommit);
          continue;
        }

        final List<FileDescriptor> descriptorList = new ArrayList<FileDescriptor>(); // NOPMD
        descriptorList.addAll(descriptorAddedList);
        descriptorList.addAll(descriptorModifiedList);

        commitAnalysis(
            config,
            repository,
            commit,
            lastCheckedCommit,
            descriptorList,
            exporter,
            branch,
            descriptorAddedList,
            descriptorModifiedList,
            descriptorDeletedList,
            tagsByCommitId);

        commitCount++;
        analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());

        lastCheckedCommit = advanceLastCheckedCommit(commit, lastCheckedCommit);
      }

      for (final RevCommit commit : commitsInRange) {
        commit.disposeBody();
      }

      final RevCommit finalLastCheckedCommit = lastCheckedCommit;
      if (finalLastCheckedCommit != null && commitsInRange.stream()
          .noneMatch(commit -> commit.getId().equals(finalLastCheckedCommit.getId()))) {
        finalLastCheckedCommit.disposeBody();
      }

      LOGGER.atTrace().addArgument(commitCount).log("Analyzed {} commits");
      // checkout the branch, so not a single commit is checked out after the run
      Git.wrap(repository).checkout().setName(fullBranch).call();
    }
  }

  private void fetchSocialData(final AnalysisConfig config, final DataExporter exporter) {
    if (!config.fetchSocialData()) {
      LOGGER.info("Skipping GitHub social data fetch, not enabled in config.");
      return;
    }

    // determine repo sub string with format "owner/repo" needed for graphql query
    if(config.repoRemoteUrl().isPresent()){
      final Optional<String> repoSubString = extractGithubRepoSubString(config.repoRemoteUrl().get());
      if (repoSubString.isEmpty()) {
        return;
      }
    }

    // send state data before fetching to make sure precondition is met
    preInitializeRemoteState(config, exporter, config.branch().orElse("main"), "");

    // determine time frame to fetch
    final int socialDataTimeFrameDays = config.socialDataTimeFrameDays().orElse(90);
    final Date endDate = determineEndDate(config);
    final Date startDate = Date.from(endDate.toInstant().minus(socialDataTimeFrameDays, ChronoUnit.DAYS));

    managedExecutor.execute(() -> {
      try {
        LOGGER.info("Starting independent background fetch for GitHub Social Data (Last {} Days).",
            socialDataTimeFrameDays);
        socialFetcherService.fetchSocialDataInRange(
            repoSubString.get(),
            startDate,
            endDate,
            exporter,
            config.landscapeToken(),
            config.gitPassword().orElse(""));
      } catch (final Exception e) {
        LOGGER.error("Background social fetch aborted: {}", e.getMessage());
      }
    });
  }

  private Optional<String> extractGithubRepoSubString(String remoteUrl) {
    if (!remoteUrl.contains("github.com")) {
      LOGGER.info("Skipping GitHub collaboration data fetch, not a GitHub repository: {}", remoteUrl);
      return Optional.empty();
    }
    final String[] parts = remoteUrl.split("github.com[:/]");
    if (parts.length < 2) {
      LOGGER.warn("Could not extract repo name from GitHub URL: {}", remoteUrl);
      return Optional.empty();
    }
    return Optional.of(parts[1].replace(".git", ""));
  }

  private Date determineEndDate(AnalysisConfig config) {

    Date endDate = Date.from(Instant.now()); // Default fallback
    if (config.fetchEndDate().isPresent() && !config.fetchEndDate().get().isBlank()) {
      final String dateStr = config.fetchEndDate().get();
      try {
        // Try parsing ISO timestamp first
        endDate = Date.from(Instant.parse(dateStr));
      } catch (final DateTimeParseException e) {
        // Fallback to simple date parsing "YYYY-MM-DD"
        endDate = Date.from(LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant());
      }
    }
    return endDate;
  }

  private void preInitializeRemoteState(final AnalysisConfig config, final DataExporter exporter,
      final String branch, final String repositoryUrl) {
    if (exporter.isRemote()) {
      try {
        final String resolvedRepositoryUrl =
            RepositoryFileUrlBuilder.resolveRepositoryUrl(
                    repositoryUrl.isBlank()
                        ? config.repoRemoteUrl()
                        : Optional.of(repositoryUrl),
                    "")
                .orElse("");
        exporter.getStateData(
            config.getRepositoryName(),
            branch,
            config.landscapeToken(),
            config.applicationPathsMap(),
            resolvedRepositoryUrl,
            true);
      } catch (final Exception e) {
        LOGGER.warn("Could not pre-initialize remote state: {}", e.getMessage());
      }
    }
  }

  private void checkIfCommitsAreReachable(final Optional<String> startCommit,
      final Optional<String> endCommit, final String branch)
      throws NotFoundException {
    if (this.gitRepositoryHandler.isUnreachableCommit(startCommit, branch)) {
      throw new NotFoundException(toErrorText("start", startCommit.orElse(""), branch));
    } else if (this.gitRepositoryHandler.isUnreachableCommit(endCommit, branch)) {
      throw new NotFoundException(toErrorText("end", endCommit.orElse(""), branch));
    }
  }

  private record AnalysisStartContext(
      Optional<String> startCommit, boolean landscapeHasPersistedCommits) {
  }

  /**
   * Resolves the git commit to start from and whether landscape already contains a fully persisted
   * commit for this repository branch.
   */
  private AnalysisStartContext resolveAnalysisStartContext(final AnalysisConfig config,
      final DataExporter exporter, final String branch, final String repositoryUrl) {
    final boolean ciMode = isCiMode();
    final StateData remoteState = exporter.getStateData(
        config.getRepositoryName(), branch,
        config.landscapeToken(), config.applicationPathsMap(), repositoryUrl,
        false);
    final boolean landscapeHasPersistedCommits = exporter.isRemote()
        && remoteState.getCommitId() != null
        && !remoteState.getCommitId().isBlank();

    if (exporter.isRemote() && ciMode) {
      if (!landscapeHasPersistedCommits) {
        LOGGER.info("No remote state found for branch {}. Starting analysis from the beginning.",
            branch);
        return new AnalysisStartContext(Optional.empty(), false);
      }
      LOGGER.info("Remote state found. Starting analysis after already analyzed commit: {}",
          remoteState.getCommitId());
      return new AnalysisStartContext(Optional.of(remoteState.getCommitId()), true);
    }

    if (config.startCommit().isPresent() && exporter.isInvalidCommitHash(
        config.startCommit().get())) {
      return new AnalysisStartContext(Optional.empty(), landscapeHasPersistedCommits);
    }
    return new AnalysisStartContext(config.startCommit(), landscapeHasPersistedCommits);
  }

  /**
   * Resolves the old commit for {@code git diff}. This may differ from the commit used for
   * unchanged-file inheritance in landscape-service; see {@link #resolveLandscapeParentCommitIds}.
   */
  /* package */ RevCommit resolveDiffBaseCommit(
      final RevCommit commit,
      final int commitCount,
      final boolean remoteExport,
      final Optional<String> startCommit,
      final RevCommit lastCheckedCommit) {
    final boolean isFirstAnalyzedCommit = commitCount == 0;
    if (isFirstAnalyzedCommit && (!remoteExport || startCommit.isEmpty())) {
      return null;
    }
    if (commit.getParentCount() > 0) {
      return commit.getParent(0);
    }
    return lastCheckedCommit;
  }

  /** Uses all git parent links so branch commit trees preserve merge topology. */
  /* package */ List<String> resolveStoredParentCommitIds(final RevCommit commit) {
    final List<String> parentIds = new ArrayList<>(commit.getParentCount());
    for (int parentIndex = 0; parentIndex < commit.getParentCount(); parentIndex++) {
      parentIds.add(commit.getParent(parentIndex).getName());
    }
    return parentIds;
  }

  /**
   * Parent commit ids for {@link CommitData}. The first id is used by landscape-service to copy
   * unchanged files and must be the previously analyzed commit on the current branch walk.
   */
  /* package */ List<String> resolveLandscapeParentCommitIds(
      final RevCommit commit, final RevCommit lastAnalyzedCommit) {
    final LinkedHashSet<String> parentIds = new LinkedHashSet<>();
    if (lastAnalyzedCommit != null) {
      parentIds.add(lastAnalyzedCommit.getName());
    }
    parentIds.addAll(resolveStoredParentCommitIds(commit));
    return List.copyOf(parentIds);
  }

  /* package */ Optional<RevCommit> toOptionalDiffBase(final RevCommit baseCommit) {
    return Optional.ofNullable(baseCommit);
  }

  /* package */ List<FileDescriptor> resolveUnchangedFilesForBootstrapCommit(
      final List<FileDescriptor> allFilesInCommit,
      final Triple<List<FileDescriptor>, List<FileDescriptor>, List<FileDescriptor>> reportTriple) {
    final Set<String> changedPaths = new HashSet<>();
    reportTriple.right().forEach(file -> changedPaths.add(file.reportedPath));
    reportTriple.left().forEach(file -> changedPaths.add(file.reportedPath));
    reportTriple.middle().forEach(file -> changedPaths.add(file.reportedPath));

    final List<FileDescriptor> unchangedFiles = new ArrayList<>();
    for (final FileDescriptor file : allFilesInCommit) {
      if (!changedPaths.contains(file.reportedPath)) {
        unchangedFiles.add(file);
      }
    }
    return unchangedFiles;
  }

  private boolean isCiMode() {
    return !"api".equals(runModeProperty);
  }

  private RevCommit resolveRemoteStartCommit(final Repository repository,
      final Optional<String> startCommit, final boolean remoteExport) throws IOException {
    if (!remoteExport || startCommit.isEmpty() || startCommit.get().isBlank()) {
      return null;
    }
    try (RevWalk revWalk = new RevWalk(repository)) {
      return revWalk.parseCommit(repository.resolve(startCommit.get()));
    }
  }

  private List<RevCommit> collectCommitsInRange(final Repository repository, final String fullBranch,
      final Optional<String> startCommit, final Optional<String> endCommit,
      final boolean remoteExport, final boolean firstParentCommitsOnly) throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      prepareRevWalk(repository, revWalk, fullBranch, firstParentCommitsOnly);

      final List<RevCommit> commits = new ArrayList<>();
      boolean inAnalysisRange = startCommit.isEmpty() || "".equals(startCommit.get());

      for (RevCommit commit : revWalk) {
        if (!inAnalysisRange) {
          if (commit.name().equals(startCommit.get())) {
            inAnalysisRange = true;
            if (remoteExport) {
              continue;
            }
          } else {
            continue;
          }
        }
        commits.add(commit);
        if (endCommit.isPresent() && commit.name().equals(endCommit.get())) {
          break;
        }
      }

      return commits;
    }
  }

  private Map<ObjectId, List<String>> buildTagsByCommitId(final Repository repository)
      throws GitAPIException {
    final Map<ObjectId, List<String>> tagsByCommitId = new HashMap<>();
    final List<Ref> tags = Git.wrap(repository).tagList().call();
    for (final Ref tag : tags) {
      tagsByCommitId.computeIfAbsent(tag.getObjectId(), ignored -> new ArrayList<>())
          .add(tag.getName());
    }
    return tagsByCommitId;
  }

  private void prepareRevWalk(final Repository repository, final RevWalk revWalk,
      final String branch, final boolean firstParentCommitsOnly) throws IOException {
    gitRepositoryHandler.configureBranchRevWalk(revWalk, repository, branch,
        firstParentCommitsOnly);
  }

  private void commitAnalysis(final AnalysisConfig config, final Repository repository,
      final RevCommit commit, final RevCommit lastAnalyzedCommit,
      final List<FileDescriptor> descriptorList,
      final DataExporter exporter, final String branchName,
      final List<FileDescriptor> addedFiles, final List<FileDescriptor> modifiedFiles,
      final List<FileDescriptor> deletedFiles,
      final Map<ObjectId, List<String>> tagsByCommitId)
      throws GitAPIException, NotFoundException, IOException {

    // Commit metadata and every file stub must reach the landscape service before FileData.
    createCommitReport(
        config,
        commit,
        lastAnalyzedCommit,
        exporter,
        branchName,
        addedFiles,
        modifiedFiles,
        deletedFiles,
        tagsByCommitId);

    antlrParserService.reset();

    LOGGER.atTrace().addArgument(descriptorList.toString()).log("Files: {}");

    final long analysisStartedAt = System.nanoTime();
    final List<CompletableFuture<FileData>> analysisTasks = submitFileAnalysisTasks(config,
        repository, commit, descriptorList);
    CompletableFuture.allOf(analysisTasks.toArray(new CompletableFuture<?>[0]))
        .whenComplete((ignored, error) -> LOGGER.atDebug()
            .addArgument(commit.getName())
            .addArgument(analysisTasks.size())
            .addArgument((System.nanoTime() - analysisStartedAt) / 1_000_000L)
            .log("File analysis for commit {} ({} files) took {} ms"));

    pipelinePersistAnalyzedFiles(exporter, analysisTasks, commit.getName());
  }

  private List<CompletableFuture<FileData>> submitFileAnalysisTasks(final AnalysisConfig config,
      final Repository repository, final RevCommit commit,
      final List<FileDescriptor> descriptorList) {
    final String commitAuthor = commit.getAuthorIdent().getEmailAddress();
    final int parallelism = resolveFileAnalysisParallelism();
    final Semaphore inFlightTasks = new Semaphore(parallelism);
    final List<CompletableFuture<FileData>> analysisTasks = new ArrayList<>(descriptorList.size());

    for (final FileDescriptor fileDescriptor : descriptorList) {
      analysisTasks.add(managedExecutor.supplyAsync(() -> {
        try {
          inFlightTasks.acquire();
          analysisStatusService.setCurrentAnalyzingFile(config.landscapeToken(),
              fileDescriptor.reportedPath);

          LOGGER.atDebug()
              .addArgument(fileDescriptor.reportedPath)
              .log("Analyzing file: {}");

          AbstractFileDataHandler fileDataHandler = analyzeFileForCommit(config, repository,
              fileDescriptor, commit.getName(), commitAuthor);
          if (fileDataHandler == null) {
            LOGGER.atWarn()
                .addArgument(fileDescriptor.reportedPath)
                .log("Analysis of file {} failed - sending minimal file data with updated hash");
            fileDataHandler = createMinimalFileDataHandler(fileDescriptor, commit);
            GitMetricCollector.addCommitGitMetrics(fileDataHandler, commitAuthor);
            fileDataHandler.setLandscapeToken(config.landscapeToken());
            fileDataHandler.setRepositoryName(config.getRepositoryName());
          }

          return toExportFileData(fileDataHandler, config);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          LOGGER.warn("File analysis interrupted for {}", fileDescriptor.reportedPath);
          final AbstractFileDataHandler minimalHandler = createMinimalFileDataHandler(fileDescriptor, commit);
          GitMetricCollector.addCommitGitMetrics(minimalHandler, commitAuthor);
          minimalHandler.setLandscapeToken(config.landscapeToken());
          minimalHandler.setRepositoryName(config.getRepositoryName());
          return toExportFileData(minimalHandler, config);
        } finally {
          inFlightTasks.release();
          analysisStatusService.incrementAnalyzedFile(config.landscapeToken());
        }
      }));
    }
    return analysisTasks;
  }

  private FileData toExportFileData(final AbstractFileDataHandler fileDataHandler,
      final AnalysisConfig config) {
    return FileDataExportFilter.filter(fileDataHandler.getProtoBufObject(),
        config.includeDataStructures());
  }

  private void pipelinePersistAnalyzedFiles(final DataExporter exporter,
      final List<CompletableFuture<FileData>> analysisTasks, final String commitId) {
    if (analysisTasks.isEmpty()) {
      return;
    }

    // Pipeline: analysis and persistence run concurrently.
    // A single batch accumulator minimizes gRPC batches per commit; up to
    // filePersistConcurrencyProperty sends may run in parallel so Neo4j can
    // process several transactions concurrently. Each FileRevision subtree is
    // owned by exactly one file, so concurrent transactions never conflict.
    final BlockingQueue<FileData> completedFiles = new LinkedBlockingQueue<>();
    final CountDownLatch analysisFinished = new CountDownLatch(1);

    final Thread persistThread = Thread.ofVirtual().name("file-persist-" + commitId)
        .start(() -> exporter.persistFilesFromQueueInBatches(completedFiles, analysisFinished,
            filePersistBatchSizeProperty, filePersistConcurrencyProperty));

    for (final CompletableFuture<FileData> analysisTask : analysisTasks) {
      analysisTask.whenComplete((fileData, error) -> {
        if (error != null) {
          LOGGER.error("Unexpected analysis failure during pipelined persist for commit {}: {}",
              commitId, error.getMessage());
        }
        if (fileData != null) {
          completedFiles.offer(fileData);
        }
      });
    }

    CompletableFuture.allOf(analysisTasks.toArray(new CompletableFuture<?>[0])).join();
    analysisFinished.countDown();
    try {
      persistThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private AbstractFileDataHandler analyzeFileForCommit(final AnalysisConfig config,
      final Repository repository, final FileDescriptor fileDescriptor, final String commitSha,
      final String commitAuthor) {
    try {
      LOGGER.atDebug()
          .addArgument(fileDescriptor.reportedPath)
          .log("Analyzing file: {}");

      AbstractFileDataHandler fileDataHandler = fileAnalysis(config, repository, fileDescriptor,
          commitSha);
      if (fileDataHandler == null) {
        return null;
      }

      try {
        final long fileSize = GitRepositoryHandler.getBlobSize(fileDescriptor.objectId, repository);
        fileDataHandler.addMetric(CommonFileDataListener.FILE_SIZE, String.valueOf(fileSize));
      } catch (IOException e) {
        LOGGER.error("File size of file {} could not be analyzed: {}", fileDescriptor.relativePath,
            e.getMessage());
      }

      GitMetricCollector.addCommitGitMetrics(fileDataHandler, commitAuthor);
      fileDataHandler.setLandscapeToken(config.landscapeToken());
      fileDataHandler.setRepositoryName(config.getRepositoryName());
      return fileDataHandler;
    } catch (IOException e) {
      LOGGER.error("Failed to analyze file {}: {}", fileDescriptor.reportedPath, e.getMessage());
      return null;
    }
  }

  private int resolveFileAnalysisParallelism() {
    if (fileAnalysisParallelismProperty > 0) {
      return fileAnalysisParallelismProperty;
    }
    return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  }

  private RevCommit advanceLastCheckedCommit(final RevCommit commit,
      final RevCommit previousCommit) {
    if (previousCommit != null) {
      previousCommit.disposeBody();
    }
    return commit;
  }

  private void createCommitReport(final AnalysisConfig config, final RevCommit commit,
      final RevCommit lastAnalyzedCommit, final DataExporter exporter, final String branchName,
      final List<FileDescriptor> addedFiles, final List<FileDescriptor> modifiedFiles,
      final List<FileDescriptor> deletedFiles,
      final Map<ObjectId, List<String>> tagsByCommitId)
      throws NotFoundException, IOException, GitAPIException {
    final CommitReportHandler commitReportHandler = new CommitReportHandler();

    commitReportHandler.init(
        commit.getId().getName(),
        resolveLandscapeParentCommitIds(commit, lastAnalyzedCommit),
        branchName);

    commitReportHandler.setAnalysisFileCount(addedFiles.size() + modifiedFiles.size());

    commitReportHandler.setAuthorDate(Timestamp.newBuilder()
        .setSeconds(commit.getAuthorIdent().getWhen().getTime() / 1000).build());
    commitReportHandler.setCommitDate(Timestamp.newBuilder()
        .setSeconds(commit.getCommitterIdent().getWhen().getTime() / 1000).build());

    for (final FileDescriptor addedFile : addedFiles) {
      commitReportHandler.addAdded(addedFile);
    }

    for (final FileDescriptor deletedFile : deletedFiles) {
      commitReportHandler.addDeleted(deletedFile);
    }

    for (final FileDescriptor modifiedFile : modifiedFiles) {
      commitReportHandler.addModified(modifiedFile);
    }

    final List<String> tags = tagsByCommitId.getOrDefault(commit.getId(), Collections.emptyList());
    commitReportHandler.addTags(tags);
    commitReportHandler.addToken(config.landscapeToken());
    commitReportHandler.setRepositoryName(config.getRepositoryName());

    ContributorData contributorData = GitMetricCollector.createContributorData(
        commit,
        config.landscapeToken(),
        config.getRepositoryName());

    commitReportHandler.setAuthor(contributorData);

    exporter.persistCommit(commitReportHandler.getCommitData());
  }

  /**
   * Checks if a file is a text file by checking its MIME type. Detects text/*,
   * application/json, and application/yaml
   * files.
   *
   * @param file the file descriptor
   * @return true if it's a readable text file
   */
  /* package */ boolean isTextFile(final FileDescriptor file, final String fileContent) {
    final String fileName = file.fileName.toLowerCase();

    if (fileName.lastIndexOf('.') == -1) {
      return isLikelyTextContent(fileContent);
    }

    if (TEXT_FILE_EXTENSIONS.contains(fileName.substring(fileName.lastIndexOf('.') + 1))) {
      return true;
    }

    return isLikelyTextContent(fileContent);
  }

  private boolean isLikelyTextContent(final String fileContent) {
    if (fileContent == null || fileContent.isEmpty()) {
      return false;
    }
    int nonPrintable = 0;
    final int sampleSize = Math.min(fileContent.length(), 4096);
    for (int i = 0; i < sampleSize; i++) {
      final char character = fileContent.charAt(i);
      if (character == '\n' || character == '\r' || character == '\t') {
        continue;
      }
      if (character < 32 || character == 127) {
        nonPrintable++;
      }
    }
    return nonPrintable == 0;
  }

  /**
   * Analyzes a file and returns the appropriate handler based on file extension.
   * Routes code files to parsers and text
   * files to basic metric collection.
   *
   * @param config     the analysis configuration
   * @param repository the git repository
   * @param file       the file descriptor
   * @param commitSha  the commit SHA
   * @return the file data handler
   * @throws IOException if file content cannot be read
   */
  private AbstractFileDataHandler fileAnalysis(final AnalysisConfig config,
      final Repository repository, final FileDescriptor file, final String commitSha)
      throws IOException {
    final String fileContent;
    try {
      fileContent = GitRepositoryHandler.getContent(file.objectId, repository);
    } catch (Exception e) {
      // skipping unreadable files
      return null;
    }

    final String fileName = file.fileName.toLowerCase();
    final long loc = fileContent.lines().count();

    try {
      AbstractFileDataHandler fileDataHandler = null;

      LOGGER.atDebug()
          .addArgument(file.reportedPath)
          .log("Analyzing file {} with size {} bytes", file.reportedPath, fileContent.length());

      if (shouldUseMinimalSourceAnalysis(config, fileName, loc)) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(loc)
            .addArgument(config.maxLocForFullAnalysis().get())
            .log("Skipping full analysis for {} ({} LOC exceeds limit of {})");
        return FallbackFileDataHandlerFactory.create(file, fileContent);
      }

      // Route to appropriate parser based on file extension
      if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")
          || fileName.endsWith(".js") || fileName.endsWith(".jsx")) {
        final Language tsJsLanguage = fileName.endsWith(".ts") || fileName.endsWith(".tsx")
            ? Language.TYPESCRIPT
            : Language.JAVASCRIPT;
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing TypeScript/JavaScript file: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> tsParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, tsJsLanguage);
      } else if (fileName.endsWith(".java")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Java file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> antlrParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.JAVA);
      } else if (fileName.endsWith(".py")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Python file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> pythonParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.PYTHON);
      } else if (fileName.endsWith(".go")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Go file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> goParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.GO);
      } else if (fileName.endsWith(".cs")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C# file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> csharpParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.CSHARP);
      } else if (fileName.endsWith(".rs")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Rust file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> rustParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.RUST);
      } else if (fileName.endsWith(".kt") || fileName.endsWith(".kts")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Kotlin file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> kotlinParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.KOTLIN);
      } else if (fileName.endsWith(".php")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing PHP file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> phpParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.PHP);
      } else if (fileName.endsWith(".swift")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Swift file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> swiftParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.SWIFT);
      } else if (fileName.endsWith(".c") || fileName.endsWith(".h")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> antlrCParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.C);
      } else if (fileName.endsWith(".cpp") || fileName.endsWith(".cxx")
          || fileName.endsWith(".cc") || fileName.endsWith(".hpp")
          || fileName.endsWith(".hxx")) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C++ file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = parseOrFallback(
            () -> cppParserService.parseFileContent(fileContent, file.reportedPath,
                file.objectId.getName()),
            file, fileContent, Language.CPP);
      } else if (isTextFile(file, fileContent)) {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("📄 Processing detected text file: {} (size: {} bytes)");

        final TextFileDataHandler textHandler = new TextFileDataHandler(file.reportedPath,
            Language.PLAINTEXT);
        textHandler.setFileHash(file.objectId.getName());
        textHandler.calculateMetrics(fileContent);

        // Add git metrics
        GitMetricCollector.addFileGitMetrics(textHandler, file);

        fileDataHandler = textHandler;
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .log("✅ Successfully processed text file: {}");
      } else {
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .log("📄 Processing other file (size only): {}");

        final TextFileDataHandler genericHandler = new TextFileDataHandler(file.reportedPath,
            Language.LANGUAGE_UNSPECIFIED);
        genericHandler.setFileHash(file.objectId.getName());

        // Add git metrics
        GitMetricCollector.addFileGitMetrics(genericHandler, file);

        fileDataHandler = genericHandler;
      }

      if (fileDataHandler != null) {
        fileDataHandler.addMetric(CommonFileDataListener.LINE_COUNT, String.valueOf(loc));
      }

      return fileDataHandler;

    } catch (NoSuchElementException | NoSuchFieldError e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(e.toString());
      }
      return createMinimalFileDataHandler(file, commitSha, fileContent);
    }
  }

  private String resolveRepositoryUrl(final AnalysisConfig config, final Repository repository) {
    return RepositoryFileUrlBuilder.resolveRepositoryUrl(
            config.repoRemoteUrl(), GitRepositoryHandler.getRemoteOriginUrl(repository))
        .orElse("");
  }

  private AbstractFileDataHandler createMinimalFileDataHandler(
      final FileDescriptor file, final RevCommit commit) {
    return createMinimalFileDataHandler(file, commit.getName(), null);
  }

  private AbstractFileDataHandler createMinimalFileDataHandler(
      final FileDescriptor file, final String commitSha) {
    return createMinimalFileDataHandler(file, commitSha, null);
  }

  private AbstractFileDataHandler createMinimalFileDataHandler(
      final FileDescriptor file, final String commitSha, final String fileContent) {
    return FallbackFileDataHandlerFactory.create(file, fileContent);
  }

  /* package */ boolean shouldUseMinimalSourceAnalysis(final AnalysisConfig config,
      final String fileName, final long loc) {
    if (config.maxLocForFullAnalysis().isEmpty()) {
      return false;
    }
    if (loc <= config.maxLocForFullAnalysis().get()) {
      return false;
    }
    return FileLanguageResolver.resolveFromFileName(fileName) != Language.LANGUAGE_UNSPECIFIED;
  }

  private AbstractFileDataHandler parseOrFallback(
      final Supplier<AbstractFileDataHandler> parseCall,
      final FileDescriptor file,
      final String fileContent,
      final Language language) {
    final AbstractFileDataHandler handler = parseCall.get();
    if (handler != null) {
      GitMetricCollector.addFileGitMetrics(handler, file);
      return handler;
    }

    if (saveCrashedFilesProperty) {
      DebugFileWriter.saveDebugFile("/logs/crashedfiles/", fileContent, file.fileName);
    }
    LOGGER.atWarn()
        .addArgument(file.reportedPath)
        .addArgument(language)
        .log("Parser failed for {}, using fallback metrics (language={}, loc, size)");
    return FallbackFileDataHandlerFactory.create(file, fileContent, language);
  }

  void applyGlobFiltering(final List<FileDescriptor> descriptors,
      final List<java.nio.file.PathMatcher> restrictMatchers,
      final List<java.nio.file.PathMatcher> excludeMatchers) {
    if (descriptors == null || descriptors.isEmpty()) {
      return;
    }

    descriptors.removeIf(desc -> {
      final java.nio.file.Path path = java.nio.file.Paths.get(desc.relativePath);

      // Restriction (Inclusion) - if specified, it must match one of them
      if (restrictMatchers != null && !restrictMatchers.isEmpty()) {
        boolean matchesRestrict = false;
        for (final java.nio.file.PathMatcher matcher : restrictMatchers) {
          if (matcher.matches(path)) {
            matchesRestrict = true;
            break;
          }
        }
        if (!matchesRestrict) {
          LOGGER.atDebug().addArgument(desc.relativePath).log("File {} does not match any restrict pattern. Skipping.");
          return true; // remove because it doesn't match restriction
        }
      }

      // Exclusion - if it matches any, remove it
      if (excludeMatchers != null && !excludeMatchers.isEmpty()) {
        for (final java.nio.file.PathMatcher matcher : excludeMatchers) {
          if (matcher.matches(path)) {
            LOGGER.atDebug().addArgument(desc.relativePath).log("File {} matches an exclude pattern. Skipping.");
            return true; // remove because it matches exclusion
          }
        }
      }

      return false; // keep it
    });
  }

  private List<java.nio.file.PathMatcher> compileMatchers(final Optional<String> patternsString) {
    if (patternsString.isEmpty() || patternsString.get().isBlank()) {
      return new java.util.ArrayList<>();
    }
    final String[] globs = patternsString.get().split(",");
    final List<java.nio.file.PathMatcher> matchers = new java.util.ArrayList<>();
    for (final String glob : globs) {
      if (!glob.trim().isEmpty()) {
        try {
          String pattern = glob.trim();
          if (!pattern.startsWith("glob:") && !pattern.startsWith("regex:")) {
            pattern = "glob:" + pattern;
          }
          matchers.add(java.nio.file.FileSystems.getDefault().getPathMatcher(pattern));
        } catch (final Exception e) {
          LOGGER.atError().addArgument(glob).log("Malformed glob/regex expression: {}");
        }
      }
    }
    return matchers;
  }

}
