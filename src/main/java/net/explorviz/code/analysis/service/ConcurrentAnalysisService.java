package net.explorviz.code.analysis.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.explorviz.code.analysis.exceptions.AnalysisCancelledException;
import net.explorviz.code.analysis.export.DataExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queues analysis requests and processes them sequentially.
 *
 * <p>Status and cancellation are tracked per landscape token, so at most one analysis per token may
 * be queued or running at a time. A token is only released once its analysis has actually stopped,
 * so a new analysis can never revive or share the state of a cancelled one that is still winding
 * down.
 */
@ApplicationScoped
public class ConcurrentAnalysisService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentAnalysisService.class);

  @Inject /* package */ AnalysisService analysisService;

  @Inject /* package */ AnalysisStatusService analysisStatusService;

  // Single-threaded executor to process analysis requests sequentially
  private ExecutorService executorService;
  private final ConcurrentHashMap<String, AnalysisJob> jobsByLandscapeToken =
      new ConcurrentHashMap<>();

  /** A queued or running analysis. */
  private static final class AnalysisJob {
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    /**
     * Set by whoever decides the fate of the job first: the worker when it starts the analysis, or
     * a cancellation while the job is still queued.
     */
    private final AtomicBoolean claimed = new AtomicBoolean();
  }

  @PostConstruct
  public void init() {
    executorService =
        Executors.newSingleThreadExecutor(
            r -> {
              final Thread thread = new Thread(r, "code-analysis-worker");
              thread.setDaemon(false); // Keep thread alive for pending requests
              return thread;
            });
    LOGGER.info("ConcurrentAnalysisService initialized with sequential request processing");
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("⏳ Shutting down ConcurrentAnalysisService...");
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        LOGGER.warn("⚠️  Executor did not terminate in time, forcing shutdown");
        executorService.shutdownNow();
      } else {
        LOGGER.info("✅ ConcurrentAnalysisService shut down successfully");
      }
    } catch (InterruptedException e) {
      LOGGER.error("❌ Interrupted during shutdown", e);
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Queues an analysis and marks it as pending. Once the analysis has stopped, its status is set to
   * finished, failed or cancelled before the returned future completes.
   *
   * @param config the analysis configuration; its landscape token identifies the job
   * @param exporter the exporter for the results
   * @return the completion of the analysis, or an empty optional if an analysis for the same
   *     landscape token is already queued or running
   */
  public Optional<CompletableFuture<Void>> analyzeAndSendRepoAsync(
      final AnalysisConfig config, final DataExporter exporter) {
    final String landscapeToken = config.landscapeToken();
    final String repoUrl = config.repoRemoteUrl().orElse("unknown");

    final AnalysisJob job = new AnalysisJob();
    if (jobsByLandscapeToken.putIfAbsent(landscapeToken, job) != null) {
      LOGGER.warn(
          "Rejecting analysis request for repository {}: an analysis for landscapeToken={} is"
              + " already queued or running",
          repoUrl,
          landscapeToken);
      return Optional.empty();
    }

    // No other job for this token is active, so clearing a previous cancellation request is safe.
    analysisStatusService.markPending(landscapeToken);
    LOGGER.info("📥 Queuing analysis request for repository: {}", repoUrl);
    try {
      executorService.execute(() -> runJob(job, config, exporter));
    } catch (RejectedExecutionException e) {
      jobsByLandscapeToken.remove(landscapeToken, job);
      analysisStatusService.markFailed(landscapeToken);
      throw e;
    }
    return Optional.of(job.completion);
  }

  private void runJob(
      final AnalysisJob job, final AnalysisConfig config, final DataExporter exporter) {
    if (!job.claimed.compareAndSet(false, true)) {
      // Cancelled while queued, the token has already been released.
      return;
    }

    final String landscapeToken = config.landscapeToken();
    final String repoUrl = config.repoRemoteUrl().orElse("unknown");
    Throwable failure = null;
    try {
      LOGGER.info("⚙️  Processing analysis request for repository: {}", repoUrl);
      analysisService.analyzeAndSendRepo(config, exporter);
      analysisStatusService.markFinished(landscapeToken);
      LOGGER.info("✅ Completed analysis for repository: {}", repoUrl);
    } catch (AnalysisCancelledException e) {
      failure = e;
      analysisStatusService.markCancelled(landscapeToken);
      LOGGER.info("🛑 Analysis cancelled for repository: {}", repoUrl);
    } catch (Exception | Error e) {
      failure = e;
      analysisStatusService.markFailed(landscapeToken);
      LOGGER.error("❌ Analysis failed for repository: {}", repoUrl, e);
    } finally {
      // Release the token before completing, so callbacks can already queue a new analysis.
      jobsByLandscapeToken.remove(landscapeToken, job);
      if (failure == null) {
        job.completion.complete(null);
      } else {
        job.completion.completeExceptionally(failure);
      }
    }
  }

  /**
   * Requests the cancellation of the analysis for the given landscape token. A queued analysis is
   * dropped right away; a running analysis stops at its next cancellation check.
   *
   * @param landscapeToken the landscape token of the analysis
   * @return the outcome of the request
   */
  public AnalysisCancellationResult cancelAnalysis(final String landscapeToken) {
    final AnalysisCancellationResult result =
        analysisStatusService.requestCancellation(landscapeToken);
    if (result != AnalysisCancellationResult.CANCELLED) {
      return result;
    }

    final AnalysisJob job = jobsByLandscapeToken.get(landscapeToken);
    if (job != null && job.claimed.compareAndSet(false, true)) {
      jobsByLandscapeToken.remove(landscapeToken, job);
      job.completion.completeExceptionally(new AnalysisCancelledException(landscapeToken));
      LOGGER.info("🛑 Removed queued analysis job for landscapeToken={}", landscapeToken);
    } else if (job != null) {
      LOGGER.info(
          "🛑 Cancellation requested for running analysis of landscapeToken={}", landscapeToken);
    } else {
      LOGGER.info(
          "🛑 Marked analysis as cancelled for landscapeToken={} (no active job)", landscapeToken);
    }

    return AnalysisCancellationResult.CANCELLED;
  }
}
