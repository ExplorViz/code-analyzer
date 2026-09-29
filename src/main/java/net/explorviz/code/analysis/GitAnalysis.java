package net.explorviz.code.analysis;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.exceptions.PropertyNotDefinedException;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.export.GrpcExporter;
import net.explorviz.code.analysis.export.JsonExporter;
import net.explorviz.code.analysis.service.AnalysisConfig;
import net.explorviz.code.analysis.service.AnalysisService;
import net.explorviz.code.analysis.service.AnalysisStatusService;
import net.explorviz.code.analysis.service.CommitSamplingPeriod;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for this service. Expects a local path to a Git repository folder
 * ("explorviz.repo.folder.path"). Sends the analysis's results to ExplorViz code service.
 */
@ApplicationScoped
public class GitAnalysis {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitAnalysis.class);

  private static final int ONE_SECOND_IN_MILLISECONDS = 1000;

  @ConfigProperty(name = "explorviz.gitanalysis.run-mode")
  /* default */ Optional<String> runMode;

  @ConfigProperty(name = "explorviz.gitanalysis.local.storage-path")
  /* default */ Optional<String> repoPathProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.remote.url")
  /* default */ Optional<String> repoRemoteUrlProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.remote.username")
  /* default */ Optional<String> usernameProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.remote.password")
  /* default */ Optional<String> passwordProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.branch")
  /* default */ Optional<String> repositoryBranchProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.include-in-analysis-expressions")
  /* default */ Optional<String> includeInAnalysisExpressionsProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.exclude-from-analysis-expressions")
  /* default */ Optional<String> excludeFromAnalysisExpressionsProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.application-root")
  /* default */ Optional<String> applicationRootProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.send-to-remote", defaultValue = "true")
  /* default */ boolean sendToRemoteProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.include-data-structures", defaultValue = "true")
  /* default */ boolean includeDataStructuresProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.start-commit-sha1")
  /* default */ Optional<String> startCommitProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.end-commit-sha1")
  /* default */ Optional<String> endCommitProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.commit-analysis-limit")
  /* default */ Optional<Integer> commitAnalysisLimitProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.commit-sampling-interval")
  /* default */ Optional<Integer> commitSamplingIntervalProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.commit-sampling-period")
  /* default */ Optional<String> commitSamplingPeriodProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.max-loc-for-full-analysis")
  /* default */ Optional<Integer> maxLocForFullAnalysisProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.first-parent-commits-only", defaultValue = "true")
  /* default */ boolean firstParentCommitsOnlyProperty;

  @ConfigProperty(
      name = "explorviz.gitanalysis.skip-commits-without-relevant-file-changes",
      defaultValue = "false")
  /* default */ boolean skipCommitsWithoutRelevantFileChangesProperty;

  @ConfigProperty(name = "explorviz.landscape.token", defaultValue = "mytokenvalue")
  /* default */ String landscapeTokenProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.application-name")
  /* default */ String applicationNameProperty;

  @Inject /* package */ GrpcExporter grpcExporter;

  @Inject /* package */ AnalysisService analysisService;

  @Inject /* package */ AnalysisStatusService analysisStatusService;

  /**
   * Creates an AnalysisConfig from the current properties.
   *
   * @return The analysis configuration
   */
  private AnalysisConfig createConfig() {
    return new AnalysisConfig.Builder()
        .repoPath(repoPathProperty)
        .repoRemoteUrl(repoRemoteUrlProperty)
        .gitUsername(usernameProperty)
        .gitPassword(passwordProperty)
        .branch(repositoryBranchProperty)
        .includeInAnalysisExpressions(includeInAnalysisExpressionsProperty)
        .excludeFromAnalysisExpressions(excludeFromAnalysisExpressionsProperty)
        .applicationRoot(applicationRootProperty)
        .includeDataStructures(includeDataStructuresProperty)
        .startCommit(startCommitProperty)
        .endCommit(endCommitProperty)
        .commitAnalysisLimit(commitAnalysisLimitProperty)
        .commitSamplingInterval(commitSamplingIntervalProperty)
        .commitSamplingPeriod(
            CommitSamplingPeriod.fromConfigValue(commitSamplingPeriodProperty.orElse(null)))
        .maxLocForFullAnalysis(maxLocForFullAnalysisProperty)
        .firstParentCommitsOnly(firstParentCommitsOnlyProperty)
        .skipCommitsWithoutRelevantFileChanges(skipCommitsWithoutRelevantFileChangesProperty)
        .landscapeToken(landscapeTokenProperty)
        .applicationName(applicationNameProperty)
        .build();
  }

  private void analyzeAndSendRepo(final DataExporter exporter)
      throws IOException, GitAPIException, PropertyNotDefinedException, NotFoundException {
    final AnalysisConfig config = createConfig();
    analysisService.analyzeAndSendRepo(config, exporter);
  }

  /* package */ void onStart(@Observes final StartupEvent ev)
      throws IOException, GitAPIException, PropertyNotDefinedException, NotFoundException {

    if (runMode.isPresent() && "api".equals(runMode.get())) {
      LOGGER.info("Running in API mode");
      return;
    }

    final long startTime = System.currentTimeMillis();

    if (repoPathProperty.isEmpty() && repoRemoteUrlProperty.isEmpty()) {
      return;
    }
    DataExporter exporter;
    final AnalysisConfig config = createConfig();
    if (sendToRemoteProperty) {
      exporter = grpcExporter;
    } else {
      exporter = new JsonExporter(config.getRepositoryName(), applicationNameProperty);
    }
    try {
      analyzeAndSendRepo(exporter);
      analysisStatusService.markFinished(landscapeTokenProperty);
    } catch (IOException | GitAPIException | PropertyNotDefinedException | NotFoundException e) {
      analysisStatusService.markFailed(landscapeTokenProperty);
      throw e;
    } catch (RuntimeException e) {
      analysisStatusService.markFailed(landscapeTokenProperty);
      throw e;
    }

    final long endTime = System.currentTimeMillis();

    LOGGER
        .atInfo()
        .addArgument((endTime - startTime) / ONE_SECOND_IN_MILLISECONDS)
        .log("Analysis finished successfully and took {} seconds, exiting now. ");

    Quarkus.asyncExit();
    // Quarkus.waitForExit();
    // System.exit(-1);

  }
}
