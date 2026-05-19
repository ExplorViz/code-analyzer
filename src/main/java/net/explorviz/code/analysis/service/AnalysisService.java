package net.explorviz.code.analysis.service;

import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import net.explorviz.code.analysis.exceptions.DebugFileWriter;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.exceptions.PropertyNotDefinedException;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.git.GitMetricCollector;
import net.explorviz.code.analysis.git.GitRepositoryHandler;
import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import net.explorviz.code.analysis.handler.CommitReportHandler;
import net.explorviz.code.analysis.handler.TextFileDataHandler;
import net.explorviz.code.analysis.listener.CommonFileDataListener;
import net.explorviz.code.analysis.parser.AntlrCppParserService;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.analysis.parser.AntlrPythonParserService;
import net.explorviz.code.analysis.parser.AntlrTypeScriptParserService;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.Triple;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.Language;
import net.explorviz.code.proto.StateData;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
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
      "gradle", "kts",
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
  /* package */ AnalysisStatusService analysisStatusService;
  @Inject
  /* package */ GithubCollaborationFetcherService socialFetcherService;
  @Inject
  /* package */ ManagedExecutor managedExecutor;
  @ConfigProperty(name = "explorviz.gitanalysis.save-crashed_files")
  /* default */ boolean saveCrashedFilesProperty;

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

      // get fetch data from remote
      final Optional<String> startCommit = findStartCommit(config, exporter, branch);

      final Optional<String> endCommit = exporter.isRemote() ? Optional.empty() : config.endCommit();

      checkIfCommitsAreReachable(startCommit, endCommit, fullBranch);

      final int totalCommitsInRange = countCommitsInRange(repository, fullBranch, startCommit, endCommit,
          exporter.isRemote());

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


      try (RevWalk revWalk = new RevWalk(repository)) {
        prepareRevWalk(repository, revWalk, fullBranch);

        int commitCount = 0;
        int skippedInPreAnalysis = 0;
        final int commitsToSkipBeforeAnalyzing = totalCommitsInRange - commitsToAnalyze;

        RevCommit lastCheckedCommit = null;
        boolean inAnalysisRange = startCommit.isEmpty() || "".equals(startCommit.get());

        // Pre-compile patterns
        final List<java.nio.file.PathMatcher> restrictMatchers = compileMatchers(
            config.includeInAnalysisExpressions());
        final List<java.nio.file.PathMatcher> excludeMatchers = compileMatchers(
            config.excludeFromAnalysisExpressions());


        for (final RevCommit commit : revWalk) {

          if (!inAnalysisRange) {
            if (commit.name().equals(startCommit.get())) {
              inAnalysisRange = true;
              if (exporter.isRemote()) {
                lastCheckedCommit = commit;
                continue;
              }
            } else {
              if (exporter.isRemote()) {
                lastCheckedCommit = commit;
              }
              continue;
            }
          }
          if (skippedInPreAnalysis < commitsToSkipBeforeAnalyzing) {
            skippedInPreAnalysis++;
            lastCheckedCommit = commit;
            continue;
          }

          if (commitCount >= commitsToAnalyze) {
            break;
          }

          LOGGER.atDebug().addArgument(commit.getName()).log("Analyzing commit: {}");

          final boolean isFirstAnalyzedCommit = commitCount == 0;
          final RevCommit baseCommit = (isFirstAnalyzedCommit && (!exporter.isRemote()
              || startCommit.isEmpty())) ? null : lastCheckedCommit;

          final var descTriple = gitRepositoryHandler
              .listDiff(
                  repository,
                  Optional.ofNullable(baseCommit),
                  commit,
                  config.pathRestrictionForDiff());

          final List<FileDescriptor> descriptorAddedList = descTriple.right(); // NOPMD
          final List<FileDescriptor> descriptorModifiedList = descTriple.left();
          final List<FileDescriptor> descriptorDeletedList = descTriple.middle();

          applyGlobFiltering(descriptorAddedList, restrictMatchers, excludeMatchers);
          applyGlobFiltering(descriptorModifiedList, restrictMatchers, excludeMatchers);
          applyGlobFiltering(descriptorDeletedList, restrictMatchers, excludeMatchers);

          LOGGER.atDebug().addArgument(descriptorAddedList.size())
              .addArgument(descriptorModifiedList.size())
              .log("Files added: {}, files modified: {}");

          analysisStatusService.setCurrentCommitFiles(config.landscapeToken(),
              descriptorAddedList.size() + descriptorModifiedList.size());

          if (descriptorAddedList.isEmpty() && descriptorModifiedList.isEmpty()) {
            createCommitReport(config, repository, commit, baseCommit, exporter, branch, descTriple,
                restrictMatchers, excludeMatchers);

            commitCount++;
            analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());
            lastCheckedCommit = commit;
            if (endCommit.isPresent() && commit.name().equals(endCommit.get())) {
              break;
            }
            continue;
          }

          final List<FileDescriptor> descriptorList = new ArrayList<FileDescriptor>(); // NOPMD
          descriptorList.addAll(descriptorAddedList);
          descriptorList.addAll(descriptorModifiedList);

          commitAnalysis(config, repository, commit, baseCommit, descriptorList, exporter,
              branch, descTriple, restrictMatchers, excludeMatchers);

          commitCount++;
          analysisStatusService.incrementAnalyzedCommit(config.landscapeToken());

          lastCheckedCommit = commit;
          // break if endCommit is reached, if endCommit is empty, run for all commits
          if (endCommit.isPresent() && commit.name().equals(endCommit.get())) {
            break;
          }
        }

        LOGGER.atTrace().addArgument(commitCount).log("Analyzed {} commits");
      }
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
    final Optional<String> repoSubString = extractGithubRepoSubString(config.repoRemoteUrl().get());
    if (repoSubString.isEmpty()) {
      return;
    }

    // send state data before fetching to make sure precondition is met
    preInitializeRemoteState(config, exporter);

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
            config.gitPassword().orElse("")
        );
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

  private void preInitializeRemoteState(AnalysisConfig config, DataExporter exporter) {
    if (exporter.isRemote()) {
      try {
        exporter.getStateData(
            config.getRepositoryName(),
            config.branch().orElse("main"),
            config.landscapeToken(),
            config.applicationPathsMap()
        );
      } catch (final Exception e) {
        LOGGER.warn("Could not pre-initialize remote state for social fetch: {}", e.getMessage());
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

  private Optional<String> findStartCommit(final AnalysisConfig config,
      final DataExporter exporter, final String branch) {
    final StateData remoteState = exporter.getStateData(
        config.getRepositoryName(), branch,
        config.landscapeToken(), config.applicationPathsMap());
    if (exporter.isRemote()) {

      if (remoteState.getCommitId().isEmpty() || remoteState.getCommitId().isBlank()) {
        LOGGER.info("No remote state found for branch {}. Starting analysis from the beginning.",
            branch);
        return Optional.empty();
      } else {
        LOGGER.info("Remote state found. Starting analysis after already analyzed commit: {}",
            remoteState.getCommitId());
        return Optional.of(remoteState.getCommitId());
      }
    } else {
      if (config.startCommit().isPresent() && exporter.isInvalidCommitHash(
          config.startCommit().get())) {
        return Optional.empty();
      }
      return config.startCommit();
    }
  }

  private int countCommitsInRange(final Repository repository, final String fullBranch,
      final Optional<String> startCommit, final Optional<String> endCommit,
      final boolean remoteExport) throws IOException {
    try (RevWalk revWalk = new RevWalk(repository)) {
      prepareRevWalk(repository, revWalk, fullBranch);

      int totalCommits = 0;
      boolean inAnalysisRange = startCommit.isEmpty() || "".equals(startCommit.get());

      for (final RevCommit commit : revWalk) {
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
        totalCommits++;
        if (endCommit.isPresent() && commit.name().equals(endCommit.get())) {
          break;
        }
      }

      return totalCommits;
    }
  }

  private void prepareRevWalk(final Repository repository, final RevWalk revWalk,
      final String branch) throws IOException {
    revWalk.sort(RevSort.COMMIT_TIME_DESC, true);
    revWalk.sort(RevSort.REVERSE, true);

    LOGGER.atTrace().addArgument(branch).log("Analyzing branch: {}");

    // get a list of all known heads, tags, remotes, ...
    final Collection<Ref> allRefs = repository.getRefDatabase().getRefs();
    for (final Ref ref : allRefs) {
      if (ref.getName().equals(branch)) {
        revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
        break;
      }
    }
  }

  private void commitAnalysis(final AnalysisConfig config, final Repository repository,
      final RevCommit commit, final RevCommit lastCommit, final List<FileDescriptor> descriptorList,
      final DataExporter exporter, final String branchName,
      final Triple<List<FileDescriptor>, List<FileDescriptor>, List<FileDescriptor>> descriptorTriple,
      final List<java.nio.file.PathMatcher> restrictMatchers,
      final List<java.nio.file.PathMatcher> excludeMatchers)
      throws GitAPIException, NotFoundException, IOException {

    createCommitReport(config, repository, commit, lastCommit, exporter, branchName, descriptorTriple,
        restrictMatchers, excludeMatchers);

    Git.wrap(repository).checkout().setName(commit.getName()).call();
    createCommitReport(config, repository, commit, lastCommit, exporter, branchName, descriptorTriple,
        restrictMatchers, excludeMatchers);

    antlrParserService.reset();
    GitMetricCollector.resetAuthor();

    LOGGER.atTrace().addArgument(descriptorList.toString()).log("Files: {}");

    descriptorList.parallelStream().forEach(fileDescriptor -> {
      try {
        analysisStatusService.setCurrentAnalyzingFile(config.landscapeToken(),
            fileDescriptor.reportedPath);

        LOGGER.atInfo()
            .addArgument(fileDescriptor.reportedPath)
            .log("📄 Analyzing file: {}");

        final AbstractFileDataHandler fileDataHandler = fileAnalysis(config, repository, fileDescriptor,
            commit.getName());

        if (fileDataHandler == null) {
          LOGGER.atError()
              .addArgument(fileDescriptor.relativePath)
              .log("❌ Analysis of file {} failed - handler is NULL");
        } else {
          LOGGER.atInfo()
              .addArgument(fileDescriptor.relativePath)
              .log("✅ Analysis of file {} succeeded - sending to exporter");
          try {
            File file = new File(GitRepositoryHandler.getCurrentRepositoryPath() + "/"
                + fileDescriptor.relativePath);
            fileDataHandler.addMetric(CommonFileDataListener.FILE_SIZE, String.valueOf(file.length()));
          } catch (NullPointerException e) {
            LOGGER.error("File size of file " + fileDescriptor.relativePath
                + " could not be analyzed." + e.getMessage());
          }
          // Add Git metrics for all files
          GitMetricCollector.addCommitGitMetrics(fileDataHandler, commit);
          fileDataHandler.setLandscapeToken(config.landscapeToken());
          fileDataHandler.setRepositoryName(config.getRepositoryName());
          exporter.persistFile(fileDataHandler.getProtoBufObject());
        }
      } catch (IOException e) {
        LOGGER.error("Failed to analyze file {}: {}", fileDescriptor.reportedPath, e.getMessage());
      } finally {
        analysisStatusService.incrementAnalyzedFile(config.landscapeToken());
      }
    });

  }

  private void createCommitReport(final AnalysisConfig config, final Repository repository,
      final RevCommit commit, final RevCommit lastCommit, final DataExporter exporter,
      final String branchName,
      final Triple<List<FileDescriptor>, List<FileDescriptor>, List<FileDescriptor>> descriptorTriple,
      final List<java.nio.file.PathMatcher> restrictMatchers,
      final List<java.nio.file.PathMatcher> excludeMatchers)
      throws NotFoundException, IOException, GitAPIException {
    final CommitReportHandler commitReportHandler = new CommitReportHandler();

    if (lastCommit == null) {
      commitReportHandler.init(commit.getId().getName(), null, branchName);
    } else {
      commitReportHandler.init(commit.getId().getName(), lastCommit.getId().getName(), branchName);
    }

    commitReportHandler.setAuthorDate(Timestamp.newBuilder()
        .setSeconds(commit.getAuthorIdent().getWhen().getTime() / 1000).build());
    commitReportHandler.setCommitDate(Timestamp.newBuilder()
        .setSeconds(commit.getCommitterIdent().getWhen().getTime() / 1000).build());

    final List<FileDescriptor> modifiedFiles = descriptorTriple.left();
    final List<FileDescriptor> deletedFiles = descriptorTriple.middle();
    final List<FileDescriptor> addedFiles = descriptorTriple.right();

    for (final FileDescriptor addedFile : addedFiles) {
      commitReportHandler.addAdded(addedFile);
    }

    for (final FileDescriptor deletedFile : deletedFiles) {
      commitReportHandler.addDeleted(deletedFile);
    }

    for (final FileDescriptor modifiedFile : modifiedFiles) {
      commitReportHandler.addModified(modifiedFile);
    }

    final List<Ref> list = Git.wrap(repository).tagList().call();
    final List<String> tags = new ArrayList<>();
    for (final Ref tag : list) {
      if (tag.getObjectId().equals(commit.getId())) {
        tags.add(tag.getName());
      }
    }
    commitReportHandler.addTags(tags);
    commitReportHandler.addToken(config.landscapeToken());
    commitReportHandler.setRepositoryName(config.getRepositoryName());

    ContributorData contributorData = GitMetricCollector.createContributorData(
        commit, 
        config.landscapeToken(),
        config.getRepositoryName()
    );

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
  /* package */ boolean isTextFile(final FileDescriptor file) {
    final String fileName = file.fileName.toLowerCase();

    if (fileName.lastIndexOf('.') == -1) {
      return false;
    }

    if (TEXT_FILE_EXTENSIONS.contains(fileName.substring(fileName.lastIndexOf('.') + 1))) {
      return true;
    }

    // Detect MIME type using file path
    try {
      Path path = FileSystems.getDefault()
          .getPath(GitRepositoryHandler.getCurrentRepositoryPath() + "/" + file.relativePath);
      return Files.probeContentType(path).startsWith("text");
    } catch (Exception e) {
      LOGGER.atTrace()
          .addArgument(file.relativePath)
          .addArgument(e.getMessage())
          .log("Could not detect MIME type for {}: {}");
    }

    return false;
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

    try {
      AbstractFileDataHandler fileDataHandler = null;

      LOGGER.atError()
          .addArgument(file.reportedPath)
          .log("ANALYZING FILE: {} with size {} bytes", file.reportedPath, fileContent.length());

      // Route to appropriate parser based on file extension
      if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")
          || fileName.endsWith(".js") || fileName.endsWith(".jsx")) {
        // TypeScript/JavaScript file
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing TypeScript/JavaScript file: {} (size: {} bytes)");

        fileDataHandler = tsParserService.parseFileContent(fileContent,
            file.reportedPath, file.objectId.getName());

        if (fileDataHandler != null) {
          // Add git metrics to the TypeScript/JavaScript file handler
          GitMetricCollector.addFileGitMetrics(fileDataHandler, file);
          LOGGER.atInfo()
              .addArgument(file.reportedPath)
              .log("✅ Successfully parsed TypeScript/JavaScript file: {}");
        } else {
          LOGGER.atError()
              .addArgument(file.reportedPath)
              .log("❌ TypeScript parser returned NULL for file: {}");
        }
      } else if (fileName.endsWith(".java")) {
        // Java file - using ANTLR parser
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Java file with ANTLR: {} (size: {} bytes)");

        // Pass reportedPath instead of fileName to preserve directory structure
        fileDataHandler = antlrParserService.parseFileContent(fileContent, file.reportedPath,
            file.objectId.getName());

        if (fileDataHandler != null) {
          // Add git metrics to the Java file handler
          GitMetricCollector.addFileGitMetrics(fileDataHandler, file);
          LOGGER.atInfo()
              .addArgument(file.reportedPath)
              .log("✅ Successfully parsed Java file with ANTLR: {}");
        } else {
          LOGGER.atError()
              .addArgument(file.reportedPath)
              .log("❌ ANTLR Java parser returned NULL for file: {}");
        }
      } else if (fileName.endsWith(".py")) {
        // Python file - using ANTLR parser
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing Python file with ANTLR: {} (size: {} bytes)");

        // Pass reportedPath instead of fileName to preserve directory structure
        fileDataHandler = pythonParserService.parseFileContent(fileContent, file.reportedPath,
            file.objectId.getName());

        if (fileDataHandler != null) {
          // Add git metrics to the Python file handler
          GitMetricCollector.addFileGitMetrics(fileDataHandler, file);
          LOGGER.atInfo()
              .addArgument(file.reportedPath)
              .log("✅ Successfully parsed Python file with ANTLR: {}");
        } else {
          LOGGER.atError()
              .addArgument(file.reportedPath)
              .log("❌ ANTLR Python parser returned NULL for file: {}");
        }
      } else if (fileName.endsWith(".c") || fileName.endsWith(".cpp")
          || fileName.endsWith(".cxx") || fileName.endsWith(".cc")
          || fileName.endsWith(".h") || fileName.endsWith(".hpp")
          || fileName.endsWith(".hxx")) {
        // C/C++ file - using ANTLR CPP14 parser
        LOGGER.atInfo()
            .addArgument(file.reportedPath)
            .addArgument(fileContent.length())
            .log("Parsing C/C++ file with ANTLR: {} (size: {} bytes)");

        fileDataHandler = cppParserService.parseFileContent(fileContent, file.reportedPath,
            file.objectId.getName());

        if (fileDataHandler != null) {
          GitMetricCollector.addFileGitMetrics(fileDataHandler, file);
          LOGGER.atInfo()
              .addArgument(file.reportedPath)
              .log("✅ Successfully parsed C/C++ file with ANTLR: {}");
        } else {
          LOGGER.atError()
              .addArgument(file.reportedPath)
              .log("❌ ANTLR C/C++ parser returned NULL for file: {}");
        }
      } else if (isTextFile(file)) {
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

      if (fileDataHandler == null) {
        if (saveCrashedFilesProperty) {
          DebugFileWriter.saveDebugFile("/logs/crashedfiles/", fileContent,
              file.fileName);
        }
      } else {
        final long loc = fileContent.lines().count();
        fileDataHandler.addMetric(CommonFileDataListener.LOC, String.valueOf(loc));
      }

      return fileDataHandler;

    } catch (NoSuchElementException | NoSuchFieldError e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(e.toString());
      }
      return null;
    }
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
