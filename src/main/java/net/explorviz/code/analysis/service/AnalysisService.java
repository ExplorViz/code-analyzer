package net.explorviz.code.analysis.service;

import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.explorviz.code.analysis.FileLanguageResolver;
import net.explorviz.code.analysis.exceptions.AnalysisCancelledException;
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
import net.explorviz.code.analysis.types.FileDiff;
import net.explorviz.code.analysis.types.RenamedFile;
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

/** Service for analyzing Git repositories and extracting code metrics. */
@ApplicationScoped
public class AnalysisService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);
  private static final Long SOCIAL_FETCH_TIMEOUT_SECONDS = 900L; // should probably be in config
  private static final Set<String> TEXT_FILE_EXTENSIONS =
      Set.of(
          // Plain text & docs
          "txt",
          "md",
          "rst",
          "adoc",
          "log",
          "license",
          "notice",
          "readme",

          // Configuration formats
          "conf",
          "cfg",
          "ini",
          "properties",
          "prefs",
          "env",
          "dotenv",
          "toml",
          "yaml",
          "yml",
          "json",
          "xml",

          // Infrastructure / tooling configs
          "gradle",
          "editorconfig",
          "gitignore",
          "gitattributes",
          "gitmodules",
          "dockerignore",
          "npmrc",
          "yarnrc",
          "pnpmrc",
          "eslintrc",
          "prettierrc",
          "stylelintrc",
          "babelrc",
          "htaccess",

          // CI / automation
          "workflow",

          // Data & text-based assets
          "csv",
          "tsv",
          "sql",

          // System / service configs
          "service",
          "socket",
          "timer");
  @Inject /* package */ GitRepositoryHandler gitRepositoryHandler;
  @Inject /* package */ AntlrParserService antlrParserService;
  @Inject /* package */ AntlrTypeScriptParserService tsParserService;
  @Inject /* package */ AntlrPythonParserService pythonParserService;
  @Inject /* package */ AntlrCppParserService cppParserService;
  @Inject /* package */ AntlrCParserService antlrCParserService;
  @Inject /* package */ AntlrGoParserService goParserService;
  @Inject /* package */ AntlrCSharpParserService csharpParserService;
  @Inject /* package */ AntlrRustParserService rustParserService;
  @Inject /* package */ AntlrKotlinParserService kotlinParserService;
  @Inject /* package */ AntlrPhpParserService phpParserService;
  @Inject /* package */ AntlrSwiftParserService swiftParserService;
  @Inject /* package */ AnalysisStatusService analysisStatusService;
  @Inject /* package */ GithubFetcherService socialFetcherService;
  @Inject /* package */ ManagedExecutor managedExecutor;

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

  private static String toErrorText(
      final String position, final String commitId, final String branchName) {
    return "The given "
        + position
        + " commit <"
        + commitId
        + "> was not found in the current branch <"
        + branchName
        + ">";
  }

  /**
   * Analyzes a Git repository and sends the results using the provided exporter.
   *
   * @param config The analysis configuration
   * @param exporter The data exporter to use for sending results
   * @throws IOException If an I/O error occurs
   * @throws GitAPIException If a Git operation fails
   * @throws NotFoundException If a required resource is not found
   * @throws PropertyNotDefinedException If a required property is not defined
   */
  public void analyzeAndSendRepo(final AnalysisConfig config, final DataExporter exporter)
      throws IOException, GitAPIException, NotFoundException, PropertyNotDefinedException {

    ensureNotCancelled(config.landscapeToken());

    // start social analysis to run async while repo is being cloned
    Optional<CompletableFuture<Void>> socialFuture = Optional.empty();

    if (!config.syncSocialWindow()) {
      socialFuture = socialFetcherService.fetchSocialData(config, exporter, managedExecutor);
    }

    try (Repository repository = this.gitRepositoryHandler.getGitRepository(config)) {

      final String fullBranch = repository.getFullBranch();
      final String branch = repository.getBranch();
      final String repositoryUrl = resolveRepositoryUrl(config, repository);

      final AnalysisStartContext analysisStartContext =
          initializeRemoteStateAndResolveStart(config, exporter, branch, repositoryUrl);
      final Optional<String> startCommit = analysisStartContext.startCommit();

      final Optional<String> endCommit =
          exporter.isRemote() ? Optional.empty() : config.endCommit();

      checkIfCommitsAreReachable(startCommit, endCommit, fullBranch);

      final List<CommitWalkEntry> commitsInRange =
          collectCommitWalkEntries(
              repository,
              fullBranch,
              startCommit,
              endCommit,
              exporter.isRemote(),
              config.firstParentCommitsOnly());
      final int totalCommitsInRange = commitsInRange.size();
      final boolean commitSamplingEnabled = CommitSampler.isEnabled(config);

      CommitRangeSelection commitRangeSelection =
          resolveCommitRangeSelection(totalCommitsInRange, config);
      commitRangeSelection =
          adjustSkipForShallowCloneBoundary(
              repository, commitsInRange, commitRangeSelection, config);

      final int commitsToAnalyze = commitRangeSelection.commitsToAnalyze();
      final int commitsToSkipBeforeAnalyzing = commitRangeSelection.commitsToSkipBeforeAnalyzing();
      final List<CommitWalkEntry> commitsToProcess =
          commitsInRange.subList(commitsToSkipBeforeAnalyzing, totalCommitsInRange);
      final List<Integer> commitTimesForSampling =
          commitsToProcess.stream().map(CommitWalkEntry::commitTime).toList();
      final Set<Integer> fullyAnalyzedCommitIndices =
          CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(commitTimesForSampling, config);
      final int commitsForProgressTracking =
          commitSamplingEnabled ? commitsToProcess.size() : commitsToAnalyze;

      if (commitSamplingEnabled) {
        LOGGER.info(
            "Commit sampling enabled: walking {} commits, fully analyzing {} of them",
            commitsToProcess.size(),
            fullyAnalyzedCommitIndices.size());
      } else {
        LOGGER.info("Total commits to analyze: {}", commitsToAnalyze);
      }
      analysisStatusService.markRunning(config.landscapeToken(), commitsForProgressTracking, 0);

      // find start and end dates for social analysis
      final List<CommitWalkEntry> analyzedCommits =
          commitSamplingEnabled
              ? commitsToProcess
              : commitsInRange.subList(commitsToSkipBeforeAnalyzing, totalCommitsInRange);
      if (config.syncSocialWindow() && !analyzedCommits.isEmpty()) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (final CommitWalkEntry walkEntry : analyzedCommits) {
          final int t = walkEntry.commitTime();
          min = Math.min(min, t);
          max = Math.max(max, t);
        }
        final Date socialStart = new Date(min * 1000L);
        final Date socialEnd = new Date(max * 1000L);
        socialFuture =
            socialFetcherService.fetchSocialData(
                config, exporter, managedExecutor, socialStart, socialEnd);
      }

      final Map<ObjectId, List<String>> tagsByCommitId = buildTagsByCommitId(repository);

      // Pre-compile patterns
      final List<java.nio.file.PathMatcher> restrictMatchers =
          compileMatchers(config.includeInAnalysisExpressions());
      final List<java.nio.file.PathMatcher> excludeMatchers =
          compileMatchers(config.excludeFromAnalysisExpressions());

      // Ancestors of a start commit are only known to the receiver when it resumes a persisted
      // analysis; locally, the start commit itself is the first commit that gets analyzed.
      final boolean resumesPersistedAnalysis =
          exporter.isRemote() && startCommit.filter(hash -> !hash.isBlank()).isPresent();
      final PersistedCommitTracker persistedCommits =
          new PersistedCommitTracker(commitsInRange, resumesPersistedAnalysis);
      int fullAnalysisCount = 0;
      int processedCommitCount = 0;
      for (int index = 0; index < commitsToProcess.size(); index++) {
        ensureNotCancelled(config.landscapeToken());
        final CommitWalkEntry walkEntry = commitsToProcess.get(index);
        final boolean fullyAnalyzeCommit =
            commitSamplingEnabled && fullyAnalyzedCommitIndices.contains(index)
                || !commitSamplingEnabled && processedCommitCount < commitsToAnalyze;

        if (!fullyAnalyzeCommit && commitSamplingEnabled) {
          LOGGER
              .atDebug()
              .addArgument(walkEntry.hash())
              .log("Skipping commit not selected for sampling: {}");
          recordSkippedCommitInWalk(config);
          persistedCommits.markSkipped(
              walkEntry.hash(), persistedCommits.resolveDiffBase(walkEntry.parentHashes()));
          continue;
        }

        if (!fullyAnalyzeCommit) {
          break;
        }

        final RevCommit commit = parseCommitWalkEntry(repository, walkEntry);
        try {
          LOGGER.atDebug().addArgument(commit.getName()).log("Analyzing commit: {}");

          final RevCommit baseCommit =
              resolveDiffBaseCommit(repository, walkEntry, persistedCommits);
          final String baseCommitHash = baseCommit == null ? null : baseCommit.getName();
          final List<String> reportedParentIds =
              persistedCommits.resolveReportedParents(walkEntry.parentHashes(), baseCommitHash);

          try {
            if (baseCommit == null) {
              LOGGER.info(
                  "Commit {} of repository {} on branch {} has no analyzed parent — analyzing all"
                      + " files",
                  commit.getName(),
                  config.getRepositoryName(),
                  branch);
            }

            final FileDiff fileDiff =
                gitRepositoryHandler.listDiff(
                    repository,
                    Optional.ofNullable(baseCommit),
                    commit,
                    config.pathRestrictionForDiff());

            final List<FileDescriptor> descriptorAddedList = fileDiff.added();
            final List<FileDescriptor> descriptorModifiedList = fileDiff.modified();
            final List<FileDescriptor> descriptorDeletedList = fileDiff.deleted();

            final GlobFilterStats globFilterStats = new GlobFilterStats();
            globFilterStats.merge(
                applyGlobFiltering(descriptorAddedList, restrictMatchers, excludeMatchers));
            globFilterStats.merge(
                applyGlobFiltering(descriptorModifiedList, restrictMatchers, excludeMatchers));
            globFilterStats.merge(
                applyGlobFiltering(descriptorDeletedList, restrictMatchers, excludeMatchers));
            logGlobFilterSummary(commit.getName(), globFilterStats);
            removeRenamesOutsideScope(fileDiff);

            if (config.skipCommitsWithoutRelevantFileChanges()
                && !hasRelevantFilteredFileChanges(
                    descriptorAddedList, descriptorModifiedList, descriptorDeletedList)) {
              LOGGER
                  .atDebug()
                  .addArgument(commit.getName())
                  .log("Skipping commit without file changes in analysis scope: {}");
              recordSkippedCommitInWalk(config);
              // Within the analysis scope, the commit equals its diff base.
              persistedCommits.markSkipped(walkEntry.hash(), baseCommitHash);
              continue;
            }

            LOGGER
                .atDebug()
                .addArgument(descriptorAddedList.size())
                .addArgument(descriptorModifiedList.size())
                .addArgument(fileDiff.renamed().size())
                .log("Files added: {}, files modified: {}, files renamed: {}");

            final List<FileDescriptor> unchangedFiles = List.of();

            final List<FileDescriptor> filesToAnalyze =
                new ArrayList<>(descriptorAddedList.size() + descriptorModifiedList.size());
            filesToAnalyze.addAll(descriptorAddedList);
            filesToAnalyze.addAll(descriptorModifiedList);

            analysisStatusService.setCurrentCommitFiles(
                config.landscapeToken(), filesToAnalyze.size());

            if (filesToAnalyze.isEmpty()) {
              createCommitReport(
                  config,
                  commit,
                  exporter,
                  branch,
                  fileDiff,
                  unchangedFiles,
                  tagsByCommitId,
                  reportedParentIds);

              fullAnalysisCount++;
              processedCommitCount++;
              analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());
              persistedCommits.markPersisted(walkEntry.hash());
              continue;
            }

            commitAnalysis(
                config,
                repository,
                commit,
                filesToAnalyze,
                exporter,
                branch,
                fileDiff,
                unchangedFiles,
                tagsByCommitId,
                reportedParentIds);

            fullAnalysisCount++;
            processedCommitCount++;
            analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());
            persistedCommits.markPersisted(walkEntry.hash());
          } finally {
            if (baseCommit != null) {
              baseCommit.disposeBody();
            }
          }
        } finally {
          commit.disposeBody();
        }
      }

      LOGGER.atTrace().addArgument(fullAnalysisCount).log("Analyzed {} commits");

      socialFuture.ifPresent(
          f -> {
            try {
              f.get(SOCIAL_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
              exporter.relinkResourceEvents(config.landscapeToken(), config.getRepositoryName());
            } catch (TimeoutException | ExecutionException e) {
              LOGGER.warn("Social fetch did not complete", e);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });

      // checkout the branch, so not a single commit is checked out after the run
      Git.wrap(repository).checkout().setName(fullBranch).call();
    }
  }

  private String resolveRepositoryUrlForStateRequest(
      final AnalysisConfig config, final String repositoryUrl) {
    return RepositoryFileUrlBuilder.resolveRepositoryUrl(
            repositoryUrl.isBlank() ? config.repoRemoteUrl() : Optional.of(repositoryUrl), "")
        .orElse("");
  }

  /**
   * Sends a single state request that both registers landscape metadata and, in CI mode, resolves
   * the latest fully persisted commit to resume from.
   */
  private AnalysisStartContext initializeRemoteStateAndResolveStart(
      final AnalysisConfig config,
      final DataExporter exporter,
      final String branch,
      final String repositoryUrl) {
    if (!exporter.isRemote()) {
      return new AnalysisStartContext(config.startCommit(), false);
    }

    try {
      final boolean ciMode = isCiMode();
      final StateData remoteState =
          exporter.getStateData(
              config.getRepositoryName(),
              branch,
              config.landscapeToken(),
              config.applicationPathsMap(),
              resolveRepositoryUrlForStateRequest(config, repositoryUrl),
              !ciMode);
      return resolveAnalysisStartContext(config, exporter, remoteState, ciMode, branch);
    } catch (final Exception e) {
      LOGGER.warn("Could not initialize remote state: {}", e.getMessage());
      return new AnalysisStartContext(config.startCommit(), false);
    }
  }

  private void checkIfCommitsAreReachable(
      final Optional<String> startCommit, final Optional<String> endCommit, final String branch)
      throws NotFoundException {
    if (this.gitRepositoryHandler.isUnreachableCommit(startCommit, branch)) {
      throw new NotFoundException(toErrorText("start", startCommit.orElse(""), branch));
    } else if (this.gitRepositoryHandler.isUnreachableCommit(endCommit, branch)) {
      throw new NotFoundException(toErrorText("end", endCommit.orElse(""), branch));
    }
  }

  private record AnalysisStartContext(
      Optional<String> startCommit, boolean landscapeHasPersistedCommits) {}

  /**
   * Resolves the git commit to start from and whether landscape already contains a fully persisted
   * commit for this repository branch.
   */
  private AnalysisStartContext resolveAnalysisStartContext(
      final AnalysisConfig config,
      final DataExporter exporter,
      final StateData remoteState,
      final boolean ciMode,
      final String branch) {
    final boolean landscapeHasPersistedCommits =
        remoteState.getCommitId() != null && !remoteState.getCommitId().isBlank();

    if (ciMode) {
      if (!landscapeHasPersistedCommits) {
        LOGGER.info(
            "No remote state found for branch {}. Starting analysis from the beginning.", branch);
        return new AnalysisStartContext(Optional.empty(), false);
      }
      LOGGER.info(
          "Remote state found. Starting analysis after already analyzed commit: {}",
          remoteState.getCommitId());
      return new AnalysisStartContext(Optional.of(remoteState.getCommitId()), true);
    }

    if (config.startCommit().isPresent()
        && exporter.isInvalidCommitHash(config.startCommit().get())) {
      return new AnalysisStartContext(Optional.empty(), landscapeHasPersistedCommits);
    }
    return new AnalysisStartContext(config.startCommit(), landscapeHasPersistedCommits);
  }

  /**
   * Resolves the old commit for {@code git diff}: the commit known to the receiver that represents
   * the first git parent (see {@link PersistedCommitTracker}). When commits were skipped (sampling
   * or irrelevant-change filtering), this is the nearest persisted ancestor so the receiver gets
   * the cumulative added/modified/deleted set. Returns {@code null} when all files must be
   * reported, i.e. for root commits, the first analyzed commit, and when the base is not available
   * locally.
   */
  /* package */ RevCommit resolveDiffBaseCommit(
      final Repository repository,
      final CommitWalkEntry walkEntry,
      final PersistedCommitTracker persistedCommits)
      throws IOException {
    final String baseHash = persistedCommits.resolveDiffBase(walkEntry.parentHashes());
    if (baseHash == null) {
      return null;
    }
    if (!isCommitAvailableInRepository(repository, baseHash)) {
      LOGGER.warn(
          "Diff base {} of commit {} is not available locally — analyzing all files",
          baseHash,
          walkEntry.hash());
      return null;
    }
    return parseCommitByHash(repository, baseHash);
  }

  /* package */ boolean hasRelevantFilteredFileChanges(
      final List<FileDescriptor> addedFiles,
      final List<FileDescriptor> modifiedFiles,
      final List<FileDescriptor> deletedFiles) {
    return !addedFiles.isEmpty() || !modifiedFiles.isEmpty() || !deletedFiles.isEmpty();
  }

  /**
   * Determines how many oldest commits to skip and how many to analyze when a {@link
   * AnalysisConfig#commitAnalysisLimit()} is set. Shallow clones fetch {@code limit + 1} commits;
   * the extra oldest commit is excluded via {@code total - limit} when {@code total > limit}.
   */
  /* package */ CommitRangeSelection resolveCommitRangeSelection(
      final int totalCommitsInRange, final AnalysisConfig config) {
    int commitsToAnalyze = totalCommitsInRange;

    if (config.commitAnalysisLimit().isPresent()) {
      final int limit = config.commitAnalysisLimit().get();
      commitsToAnalyze = Math.min(limit, totalCommitsInRange);
    }

    final int commitsToSkipBeforeAnalyzing = totalCommitsInRange - commitsToAnalyze;
    return new CommitRangeSelection(commitsToAnalyze, commitsToSkipBeforeAnalyzing);
  }

  /**
   * Excludes the shallow-clone boundary commit when it would otherwise be analyzed. That commit's
   * parent is not available locally, so it falls outside the intended analysis window.
   */
  /* package */ CommitRangeSelection adjustSkipForShallowCloneBoundary(
      final Repository repository,
      final List<CommitWalkEntry> commitsInRange,
      final CommitRangeSelection selection,
      final AnalysisConfig config)
      throws IOException {
    if (config.commitAnalysisLimit().isEmpty() || commitsInRange.isEmpty()) {
      return selection;
    }

    int commitsToSkipBeforeAnalyzing = selection.commitsToSkipBeforeAnalyzing();
    int commitsToAnalyze = selection.commitsToAnalyze();
    if (commitsToAnalyze == 0) {
      return selection;
    }

    final CommitWalkEntry firstEntryToProcess = commitsInRange.get(commitsToSkipBeforeAnalyzing);
    final RevCommit firstCommitToProcess =
        parseCommitByHash(repository, firstEntryToProcess.hash());
    try {
      if (shouldExcludeAsShallowCloneBoundary(repository, firstCommitToProcess)) {
        commitsToSkipBeforeAnalyzing++;
        commitsToAnalyze = Math.max(0, commitsToAnalyze - 1);
        LOGGER.info(
            "Excluding shallow-clone boundary commit {} from analysis", firstEntryToProcess.hash());
      }
    } finally {
      firstCommitToProcess.disposeBody();
    }

    return new CommitRangeSelection(commitsToAnalyze, commitsToSkipBeforeAnalyzing);
  }

  /* package */ boolean shouldExcludeAsShallowCloneBoundary(
      final Repository repository, final RevCommit commit) throws IOException {
    return isShallowBoundaryCommit(repository, commit)
        || hasMissingParentInRepository(repository, commit);
  }

  /* package */ boolean isShallowBoundaryCommit(final Repository repository, final RevCommit commit)
      throws IOException {
    final java.io.File shallowFile = new java.io.File(repository.getDirectory(), "shallow");
    if (!shallowFile.isFile()) {
      return false;
    }
    final String commitId = commit.getName();
    for (final String line : java.nio.file.Files.readAllLines(shallowFile.toPath())) {
      if (line.startsWith(commitId)) {
        return true;
      }
    }
    return false;
  }

  /* package */ boolean hasMissingParentInRepository(
      final Repository repository, final RevCommit commit) throws IOException {
    if (commit.getParentCount() == 0) {
      return false;
    }
    return !isCommitAvailableInRepository(repository, commit.getParent(0).getName());
  }

  private boolean isCommitAvailableInRepository(
      final Repository repository, final String commitHash) throws IOException {
    // Repository#resolve does not check whether a full object id exists, so ask the database.
    return repository.getObjectDatabase().has(ObjectId.fromString(commitHash));
  }

  /* package */ record CommitRangeSelection(
      int commitsToAnalyze, int commitsToSkipBeforeAnalyzing) {}

  /* package */ List<FileDescriptor> resolveUnchangedFilesForBootstrapCommit(
      final List<FileDescriptor> allFilesInCommit, final FileDiff fileDiff) {
    final Set<String> changedPaths = new HashSet<>();
    fileDiff.added().forEach(file -> changedPaths.add(file.reportedPath));
    fileDiff.modified().forEach(file -> changedPaths.add(file.reportedPath));
    fileDiff.deleted().forEach(file -> changedPaths.add(file.reportedPath));

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

  private RevCommit parseCommitWalkEntry(final Repository repository, final CommitWalkEntry entry)
      throws IOException {
    return parseCommitByHash(repository, entry.hash());
  }

  /**
   * Collects the commits to analyze in topological order (every parent before its children).
   *
   * <p>The range is defined by reachability, as in {@code git rev-list start..end}, not by the
   * position of the start commit in the walk: when not only first-parent commits are walked,
   * commits of merged side branches may be emitted before the start commit although they are no
   * ancestors of it, or after it although they are. The start commit itself is only included for
   * local exports, as remote exports resume after an already persisted start commit.
   */
  /* package */ List<CommitWalkEntry> collectCommitWalkEntries(
      final Repository repository,
      final String fullBranch,
      final Optional<String> startCommit,
      final Optional<String> endCommit,
      final boolean remoteExport,
      final boolean firstParentCommitsOnly)
      throws IOException {
    final String start = startCommit.filter(hash -> !hash.isBlank()).orElse(null);
    final String tip = endCommit.filter(hash -> !hash.isBlank()).orElse(fullBranch);
    try (RevWalk revWalk = new RevWalk(repository)) {
      gitRepositoryHandler.configureBranchRevWalk(
          revWalk, repository, tip, start, firstParentCommitsOnly);

      final List<CommitWalkEntry> commits = new ArrayList<>();
      if (start != null && !remoteExport) {
        // All ancestors of the start commit are excluded, so it precedes every walked commit.
        commits.add(toCommitWalkEntry(revWalk.parseCommit(repository.resolve(start))));
      }
      for (final RevCommit commit : revWalk) {
        commits.add(toCommitWalkEntry(commit));
        commit.disposeBody();
      }
      return commits;
    }
  }

  private static CommitWalkEntry toCommitWalkEntry(final RevCommit commit) {
    final List<String> parentHashes = new ArrayList<>(commit.getParentCount());
    for (int parentIndex = 0; parentIndex < commit.getParentCount(); parentIndex++) {
      parentHashes.add(commit.getParent(parentIndex).getName());
    }
    return new CommitWalkEntry(commit.getName(), commit.getCommitTime(), parentHashes);
  }

  /**
   * Maps each tagged commit to the names of its tags. Annotated tags point to a tag object rather
   * than to the commit, so they are peeled to the commit they reference.
   */
  /* package */ static Map<ObjectId, List<String>> buildTagsByCommitId(final Repository repository)
      throws GitAPIException, IOException {
    final Map<ObjectId, List<String>> tagsByCommitId = new HashMap<>();
    final List<Ref> tags = Git.wrap(repository).tagList().call();
    for (final Ref tag : tags) {
      final Ref peeledTag = repository.getRefDatabase().peel(tag);
      final ObjectId commitId =
          peeledTag.getPeeledObjectId() != null
              ? peeledTag.getPeeledObjectId()
              : peeledTag.getObjectId();
      tagsByCommitId.computeIfAbsent(commitId, ignored -> new ArrayList<>()).add(tag.getName());
    }
    return tagsByCommitId;
  }

  private void commitAnalysis(
      final AnalysisConfig config,
      final Repository repository,
      final RevCommit commit,
      final List<FileDescriptor> filesToAnalyze,
      final DataExporter exporter,
      final String branchName,
      final FileDiff fileDiff,
      final List<FileDescriptor> unchangedFiles,
      final Map<ObjectId, List<String>> tagsByCommitId,
      final List<String> parentCommitIds)
      throws GitAPIException, NotFoundException, IOException {

    ensureNotCancelled(config.landscapeToken());

    // Commit metadata and every file stub must reach the landscape service before FileData.
    createCommitReport(
        config,
        commit,
        exporter,
        branchName,
        fileDiff,
        unchangedFiles,
        tagsByCommitId,
        parentCommitIds);

    antlrParserService.reset();

    LOGGER.atTrace().addArgument(filesToAnalyze.toString()).log("Files: {}");

    final long analysisStartedAt = System.nanoTime();
    pipelineAnalyzeAndPersistFiles(config, repository, commit, filesToAnalyze, exporter);
    LOGGER
        .atDebug()
        .addArgument(commit.getName())
        .addArgument(filesToAnalyze.size())
        .addArgument((System.nanoTime() - analysisStartedAt) / 1_000_000L)
        .log("File analysis and persistence for commit {} ({} files) took {} ms");
  }

  /**
   * Analyzes the given files in parallel and persists the results while the analysis is still
   * running.
   *
   * <p>A single batch accumulator minimizes gRPC batches per commit; up to {@code
   * filePersistConcurrencyProperty} sends may run in parallel so Neo4j can process several
   * transactions concurrently. Each FileRevision subtree is owned by exactly one file, so
   * concurrent transactions never conflict.
   *
   * <p>The number of files in flight is bounded before a task is submitted, so executor threads
   * never block while waiting for a slot. Finished results go straight to the persist queue and are
   * not retained until the whole commit is done.
   */
  private void pipelineAnalyzeAndPersistFiles(
      final AnalysisConfig config,
      final Repository repository,
      final RevCommit commit,
      final List<FileDescriptor> descriptorList,
      final DataExporter exporter) {
    if (descriptorList.isEmpty()) {
      return;
    }

    final String commitId = commit.getName();
    final BlockingQueue<FileData> completedFiles = new LinkedBlockingQueue<>();
    final CountDownLatch analysisFinished = new CountDownLatch(1);
    final Thread persistThread =
        Thread.ofVirtual()
            .name("file-persist-" + commitId)
            .start(
                () ->
                    exporter.persistFilesFromQueueInBatches(
                        completedFiles,
                        analysisFinished,
                        filePersistBatchSizeProperty,
                        filePersistConcurrencyProperty));

    try {
      final List<CompletableFuture<Void>> analysisTasks =
          submitFileAnalysisTasks(config, repository, commit, descriptorList, completedFiles);
      CompletableFuture.allOf(analysisTasks.toArray(new CompletableFuture<?>[0])).join();
    } finally {
      // Always release the persist thread, also when the analysis failed or was cancelled.
      analysisFinished.countDown();
      try {
        persistThread.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private List<CompletableFuture<Void>> submitFileAnalysisTasks(
      final AnalysisConfig config,
      final Repository repository,
      final RevCommit commit,
      final List<FileDescriptor> descriptorList,
      final BlockingQueue<FileData> completedFiles) {
    final String commitId = commit.getName();
    final String commitAuthor = commit.getAuthorIdent().getEmailAddress();
    final Semaphore inFlightTasks = new Semaphore(resolveFileAnalysisParallelism());
    final List<CompletableFuture<Void>> analysisTasks = new ArrayList<>(descriptorList.size());

    for (final FileDescriptor fileDescriptor : descriptorList) {
      ensureNotCancelled(config.landscapeToken());
      try {
        inFlightTasks.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AnalysisCancelledException(config.landscapeToken());
      }

      final CompletableFuture<Void> analysisTask;
      try {
        analysisTask =
            managedExecutor.runAsync(
                () -> {
                  try {
                    ensureNotCancelled(config.landscapeToken());
                    analysisStatusService.setCurrentAnalyzingFile(
                        config.landscapeToken(), fileDescriptor.reportedPath);
                    completedFiles.offer(
                        analyzeFileOrFallback(
                            config, repository, fileDescriptor, commit, commitAuthor));
                  } finally {
                    inFlightTasks.release();
                    analysisStatusService.completeAnalyzedFile(
                        config.landscapeToken(), fileDescriptor.reportedPath);
                  }
                });
      } catch (RuntimeException e) {
        inFlightTasks.release();
        throw e;
      }

      analysisTask.whenComplete(
          (ignored, error) -> {
            if (error != null) {
              LOGGER.error(
                  "Unexpected analysis failure for file {} in commit {}: {}",
                  fileDescriptor.reportedPath,
                  commitId,
                  error.getMessage());
            }
          });
      analysisTasks.add(analysisTask);
    }
    return analysisTasks;
  }

  private FileData analyzeFileOrFallback(
      final AnalysisConfig config,
      final Repository repository,
      final FileDescriptor fileDescriptor,
      final RevCommit commit,
      final String commitAuthor) {
    LOGGER.atDebug().addArgument(fileDescriptor.reportedPath).log("Analyzing file: {}");

    AbstractFileDataHandler fileDataHandler =
        analyzeFileForCommit(config, repository, fileDescriptor, commit.getName(), commitAuthor);
    if (fileDataHandler == null) {
      LOGGER
          .atWarn()
          .addArgument(fileDescriptor.reportedPath)
          .log("Analysis of file {} failed - sending minimal file data with updated hash");
      fileDataHandler = createMinimalFileDataHandler(fileDescriptor, commit);
      GitMetricCollector.addCommitGitMetrics(fileDataHandler, commitAuthor);
      fileDataHandler.setLandscapeToken(config.landscapeToken());
      fileDataHandler.setRepositoryName(config.getRepositoryName());
    }
    return toExportFileData(fileDataHandler, config);
  }

  private FileData toExportFileData(
      final AbstractFileDataHandler fileDataHandler, final AnalysisConfig config) {
    return FileDataExportFilter.filter(
        fileDataHandler.getProtoBufObject(), config.includeDataStructures());
  }

  private void ensureNotCancelled(final String landscapeToken) {
    if (analysisStatusService.isCancellationRequested(landscapeToken)
        || Thread.currentThread().isInterrupted()) {
      throw new AnalysisCancelledException(landscapeToken);
    }
  }

  private AbstractFileDataHandler analyzeFileForCommit(
      final AnalysisConfig config,
      final Repository repository,
      final FileDescriptor fileDescriptor,
      final String commitSha,
      final String commitAuthor) {
    final byte[] fileBytes;
    try {
      fileBytes = GitRepositoryHandler.getBytes(fileDescriptor.objectId, repository);
    } catch (IOException e) {
      // skipping unreadable files
      LOGGER.error("Failed to read file {}: {}", fileDescriptor.reportedPath, e.getMessage());
      return null;
    }

    final AbstractFileDataHandler fileDataHandler =
        fileAnalysis(
            config, fileDescriptor, commitSha, new String(fileBytes, StandardCharsets.UTF_8));
    if (fileDataHandler == null) {
      return null;
    }

    fileDataHandler.addMetric(CommonFileDataListener.FILE_SIZE, String.valueOf(fileBytes.length));
    GitMetricCollector.addCommitGitMetrics(fileDataHandler, commitAuthor);
    fileDataHandler.setLandscapeToken(config.landscapeToken());
    fileDataHandler.setRepositoryName(config.getRepositoryName());
    return fileDataHandler;
  }

  private int resolveFileAnalysisParallelism() {
    if (fileAnalysisParallelismProperty > 0) {
      return fileAnalysisParallelismProperty;
    }
    return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
  }

  private RevCommit parseCommitByHash(final Repository repository, final String commitHash)
      throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      return revWalk.parseCommit(repository.resolve(commitHash));
    }
  }

  private void recordSkippedCommitInWalk(final AnalysisConfig config) {
    analysisStatusService.setCurrentCommitFiles(config.landscapeToken(), 0);
    analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());
  }

  private void createCommitReport(
      final AnalysisConfig config,
      final RevCommit commit,
      final DataExporter exporter,
      final String branchName,
      final FileDiff fileDiff,
      final List<FileDescriptor> unchangedFiles,
      final Map<ObjectId, List<String>> tagsByCommitId,
      final List<String> parentCommitIds)
      throws NotFoundException, IOException, GitAPIException {
    final CommitReportHandler commitReportHandler = new CommitReportHandler();

    commitReportHandler.init(commit.getId().getName(), parentCommitIds, branchName);

    commitReportHandler.setAnalysisFileCount(
        fileDiff.added().size() + fileDiff.modified().size() + unchangedFiles.size());

    commitReportHandler.setAuthorDate(
        Timestamp.newBuilder()
            .setSeconds(commit.getAuthorIdent().getWhen().getTime() / 1000)
            .build());
    commitReportHandler.setCommitDate(
        Timestamp.newBuilder()
            .setSeconds(commit.getCommitterIdent().getWhen().getTime() / 1000)
            .build());

    for (final FileDescriptor addedFile : fileDiff.added()) {
      commitReportHandler.addAdded(addedFile);
    }

    for (final FileDescriptor deletedFile : fileDiff.deleted()) {
      commitReportHandler.addDeleted(deletedFile);
    }

    for (final FileDescriptor modifiedFile : fileDiff.modified()) {
      commitReportHandler.addModified(modifiedFile);
    }

    for (final RenamedFile renamedFile : fileDiff.renamed()) {
      commitReportHandler.addRenamed(renamedFile);
    }

    for (final FileDescriptor unchangedFile : unchangedFiles) {
      commitReportHandler.addUnchanged(unchangedFile);
    }

    final List<String> tags = tagsByCommitId.getOrDefault(commit.getId(), Collections.emptyList());
    commitReportHandler.addTags(tags);
    commitReportHandler.addToken(config.landscapeToken());
    commitReportHandler.setRepositoryName(config.getRepositoryName());

    ContributorData contributorData =
        GitMetricCollector.createContributorData(
            commit, config.landscapeToken(), config.getRepositoryName());

    commitReportHandler.setAuthor(contributorData);

    exporter.persistCommit(commitReportHandler.getCommitData());
  }

  /**
   * Checks if a file is a text file by checking its MIME type. Detects text/*, application/json,
   * and application/yaml files.
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
   * Analyzes a file and returns the appropriate handler based on file extension. Routes code files
   * to parsers and text files to basic metric collection.
   *
   * @param config the analysis configuration
   * @param file the file descriptor
   * @param commitSha the commit SHA
   * @param fileContent the file content, decoded as UTF-8
   * @return the file data handler
   */
  private AbstractFileDataHandler fileAnalysis(
      final AnalysisConfig config,
      final FileDescriptor file,
      final String commitSha,
      final String fileContent) {
    final String fileName = file.fileName.toLowerCase();
    final long loc = fileContent.lines().count();

    try {
      AbstractFileDataHandler fileDataHandler = null;

      LOGGER
          .atDebug()
          .addArgument(file.reportedPath)
          .addArgument(fileContent.length())
          .log("Analyzing file {} with size {} bytes");

      if (shouldUseMinimalSourceAnalysis(config, fileName, loc)) {
        LOGGER
            .atInfo()
            .addArgument(file.reportedPath)
            .addArgument(loc)
            .addArgument(config.maxLocForFullAnalysis().get())
            .log("Skipping full analysis for {} ({} LOC exceeds limit of {})");
        return FallbackFileDataHandlerFactory.create(file, fileContent);
      }

      // Route to appropriate parser based on file extension
      if (fileName.endsWith(".ts")
          || fileName.endsWith(".tsx")
          || fileName.endsWith(".js")
          || fileName.endsWith(".jsx")) {
        final Language tsJsLanguage =
            fileName.endsWith(".ts") || fileName.endsWith(".tsx")
                ? Language.TYPESCRIPT
                : Language.JAVASCRIPT;
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing TypeScript/JavaScript file: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    tsParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                tsJsLanguage);
      } else if (fileName.endsWith(".java")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Java file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    antlrParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.JAVA);
      } else if (fileName.endsWith(".py")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Python file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    pythonParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.PYTHON);
      } else if (fileName.endsWith(".go")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Go file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    goParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.GO);
      } else if (fileName.endsWith(".cs")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C# file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    csharpParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.CSHARP);
      } else if (fileName.endsWith(".rs")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Rust file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    rustParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.RUST);
      } else if (fileName.endsWith(".kt") || fileName.endsWith(".kts")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Kotlin file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    kotlinParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.KOTLIN);
      } else if (fileName.endsWith(".php")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing PHP file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    phpParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.PHP);
      } else if (fileName.endsWith(".swift")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Swift file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    swiftParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.SWIFT);
      } else if (fileName.endsWith(".c") || fileName.endsWith(".h")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    antlrCParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.C);
      } else if (fileName.endsWith(".cpp")
          || fileName.endsWith(".cxx")
          || fileName.endsWith(".cc")
          || fileName.endsWith(".hpp")
          || fileName.endsWith(".hxx")) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C++ file with ANTLR: {} (size: {} bytes)");

        fileDataHandler =
            parseOrFallback(
                () ->
                    cppParserService.parseFileContent(
                        fileContent, file.reportedPath, file.objectId.getName()),
                file,
                fileContent,
                Language.CPP);
      } else if (isTextFile(file, fileContent)) {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("📄 Processing detected text file: {} (size: {} bytes)");

        final TextFileDataHandler textHandler =
            new TextFileDataHandler(file.reportedPath, Language.PLAINTEXT);
        textHandler.setFileHash(file.objectId.getName());
        textHandler.calculateMetrics(fileContent);

        // Add git metrics
        GitMetricCollector.addFileGitMetrics(textHandler, file);

        fileDataHandler = textHandler;
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .log("✅ Successfully processed text file: {}");
      } else {
        LOGGER
            .atDebug()
            .addArgument(file.reportedPath)
            .log("📄 Processing other file (size only): {}");

        final TextFileDataHandler genericHandler =
            new TextFileDataHandler(file.reportedPath, Language.LANGUAGE_UNSPECIFIED);
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

  /* package */ boolean shouldUseMinimalSourceAnalysis(
      final AnalysisConfig config, final String fileName, final long loc) {
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
    LOGGER
        .atWarn()
        .addArgument(file.reportedPath)
        .addArgument(language)
        .log("Parser failed for {}, using fallback metrics (language={}, loc, size)");
    return FallbackFileDataHandlerFactory.create(file, fileContent, language);
  }

  private static final class GlobFilterStats {
    private int excludedByInclusion;
    private int excludedByExclusion;

    private void merge(final GlobFilterStats other) {
      excludedByInclusion += other.excludedByInclusion;
      excludedByExclusion += other.excludedByExclusion;
    }

    private boolean hasExclusions() {
      return excludedByInclusion > 0 || excludedByExclusion > 0;
    }
  }

  private void logGlobFilterSummary(final String commitHash, final GlobFilterStats stats) {
    if (!stats.hasExclusions()) {
      return;
    }
    LOGGER
        .atDebug()
        .addArgument(commitHash)
        .addArgument(stats.excludedByInclusion)
        .addArgument(stats.excludedByExclusion)
        .log(
            "Commit {}: skipped {} file(s) outside inclusion patterns and {} file(s) matching"
                + " exclusion patterns");
  }

  /**
   * Drops renames of which only one side survived glob filtering. Such a file is, from the point of
   * view of the analysis scope, a plain addition or deletion, and a receiver could not resolve the
   * file that is out of scope.
   */
  /* package */ static void removeRenamesOutsideScope(final FileDiff fileDiff) {
    if (fileDiff.renamed().isEmpty()) {
      return;
    }
    final Set<FileDescriptor> added = new HashSet<>(fileDiff.added());
    final Set<FileDescriptor> deleted = new HashSet<>(fileDiff.deleted());
    fileDiff
        .renamed()
        .removeIf(
            renamed -> !added.contains(renamed.newFile()) || !deleted.contains(renamed.oldFile()));
  }

  private GlobFilterStats applyGlobFiltering(
      final List<FileDescriptor> descriptors,
      final List<java.nio.file.PathMatcher> restrictMatchers,
      final List<java.nio.file.PathMatcher> excludeMatchers) {
    final GlobFilterStats stats = new GlobFilterStats();
    if (descriptors == null || descriptors.isEmpty()) {
      return stats;
    }

    descriptors.removeIf(
        desc -> {
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
              stats.excludedByInclusion++;
              return true; // remove because it doesn't match restriction
            }
          }

          // Exclusion - if it matches any, remove it
          if (excludeMatchers != null && !excludeMatchers.isEmpty()) {
            for (final java.nio.file.PathMatcher matcher : excludeMatchers) {
              if (matcher.matches(path)) {
                stats.excludedByExclusion++;
                return true; // remove because it matches exclusion
              }
            }
          }

          return false; // keep it
        });
    return stats;
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
