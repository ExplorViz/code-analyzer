package net.explorviz.code.analysis.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.explorviz.code.analysis.exceptions.AnalysisCancelledException;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.export.VoidExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcurrentAnalysisServiceTest {

  private static final String TOKEN = "token";
  private static final String OTHER_TOKEN = "other-token";
  private static final long TIMEOUT_SECONDS = 10;

  private final BlockingAnalysisService analysisService = new BlockingAnalysisService();
  private final AnalysisStatusService statusService = new AnalysisStatusService();
  private final ConcurrentAnalysisService service = new ConcurrentAnalysisService();
  private final DataExporter exporter = new VoidExporter();

  @BeforeEach
  void setUp() {
    statusService.terminalProgressReporter = new TerminalProgressReporter();
    analysisService.statusService = statusService;
    service.analysisService = analysisService;
    service.analysisStatusService = statusService;
    service.init();
  }

  @AfterEach
  void tearDown() {
    analysisService.releaseAll();
    service.shutdown();
  }

  @Test
  void rejectsSecondAnalysisForTokenWhileFirstIsActive() throws Exception {
    final CompletableFuture<Void> first = submit(TOKEN).orElseThrow();
    analysisService.awaitStart(TOKEN);

    Assertions.assertTrue(submit(TOKEN).isEmpty());

    analysisService.release(TOKEN);
    first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    Assertions.assertEquals(AnalysisStatusService.STATUS_FINISHED, status(TOKEN));
    Assertions.assertTrue(submit(TOKEN).isPresent());
  }

  @Test
  void cancellingQueuedAnalysisReleasesTokenImmediately() throws Exception {
    final CompletableFuture<Void> blocker = submit(OTHER_TOKEN).orElseThrow();
    analysisService.awaitStart(OTHER_TOKEN);
    final CompletableFuture<Void> queued = submit(TOKEN).orElseThrow();

    Assertions.assertEquals(AnalysisCancellationResult.CANCELLED, service.cancelAnalysis(TOKEN));
    assertCancelled(queued);

    final CompletableFuture<Void> retriggered = submit(TOKEN).orElseThrow();
    Assertions.assertEquals(AnalysisStatusService.STATUS_PENDING, status(TOKEN));
    Assertions.assertFalse(statusService.isCancellationRequested(TOKEN));

    analysisService.release(OTHER_TOKEN);
    blocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    analysisService.awaitStart(TOKEN);
    analysisService.release(TOKEN);
    retriggered.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    // The cancelled job never ran; only the re-triggered one did.
    Assertions.assertEquals(1, analysisService.startCount(TOKEN));
    Assertions.assertEquals(AnalysisStatusService.STATUS_FINISHED, status(TOKEN));
  }

  @Test
  void cancelledRunningAnalysisKeepsTokenUntilItStops() throws Exception {
    final CompletableFuture<Void> running = submit(TOKEN).orElseThrow();
    analysisService.awaitStart(TOKEN);

    Assertions.assertEquals(AnalysisCancellationResult.CANCELLED, service.cancelAnalysis(TOKEN));
    // Re-triggering must not clear the cancellation request of the still running analysis.
    Assertions.assertTrue(submit(TOKEN).isEmpty());
    Assertions.assertTrue(statusService.isCancellationRequested(TOKEN));

    analysisService.release(TOKEN);
    assertCancelled(running);
    Assertions.assertEquals(AnalysisStatusService.STATUS_CANCELLED, status(TOKEN));

    Assertions.assertTrue(submit(TOKEN).isPresent());
    Assertions.assertFalse(statusService.isCancellationRequested(TOKEN));
  }

  @Test
  void failedAnalysisIsMarkedFailedAndReleasesToken() throws Exception {
    analysisService.failures.put(TOKEN, new IllegalStateException("boom"));
    final CompletableFuture<Void> job = submit(TOKEN).orElseThrow();
    analysisService.release(TOKEN);

    final ExecutionException thrown =
        Assertions.assertThrows(
            ExecutionException.class, () -> job.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    Assertions.assertInstanceOf(IllegalStateException.class, thrown.getCause());
    Assertions.assertEquals(AnalysisStatusService.STATUS_FAILED, status(TOKEN));
    Assertions.assertTrue(submit(TOKEN).isPresent());
  }

  private Optional<CompletableFuture<Void>> submit(final String token) {
    final AnalysisConfig config = new AnalysisConfig.Builder().landscapeToken(token).build();
    return service.analyzeAndSendRepoAsync(config, exporter);
  }

  private String status(final String token) {
    return statusService.getStatus(token).orElseThrow();
  }

  private static void assertCancelled(final CompletableFuture<Void> job) {
    final ExecutionException thrown =
        Assertions.assertThrows(
            ExecutionException.class, () -> job.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    Assertions.assertInstanceOf(AnalysisCancelledException.class, thrown.getCause());
  }

  /** Blocks every analysis until it is released and honours cancellation afterwards. */
  private static final class BlockingAnalysisService extends AnalysisService {

    private final Map<String, CountDownLatch> releases = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<Boolean>> starts = new ConcurrentHashMap<>();
    private final Map<String, Integer> startCounts = new ConcurrentHashMap<>();
    private final Map<String, RuntimeException> failures = new ConcurrentHashMap<>();
    private AnalysisStatusService statusService;
    private volatile boolean closed;

    @Override
    public void analyzeAndSendRepo(final AnalysisConfig config, final DataExporter exporter) {
      final String token = config.landscapeToken();
      startCounts.merge(token, 1, Integer::sum);
      startQueue(token).add(true);
      try {
        awaitRelease(token);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AnalysisCancelledException(token);
      }
      // Re-arm, so that a later analysis of the same token blocks again.
      releases.remove(token);
      if (statusService.isCancellationRequested(token)) {
        throw new AnalysisCancelledException(token);
      }
      final RuntimeException failure = failures.get(token);
      if (failure != null) {
        throw failure;
      }
    }

    private void awaitRelease(final String token) throws InterruptedException {
      final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      while (!latch(token).await(50, TimeUnit.MILLISECONDS)) {
        if (closed) {
          return;
        }
        if (System.nanoTime() > deadline) {
          throw new IllegalStateException("Analysis for " + token + " was never released");
        }
      }
    }

    private void awaitStart(final String token) throws InterruptedException {
      Assertions.assertNotNull(
          startQueue(token).poll(TIMEOUT_SECONDS, TimeUnit.SECONDS),
          "Analysis for " + token + " did not start");
    }

    private void release(final String token) {
      latch(token).countDown();
    }

    private void releaseAll() {
      closed = true;
      releases.values().forEach(CountDownLatch::countDown);
    }

    private int startCount(final String token) {
      return startCounts.getOrDefault(token, 0);
    }

    private CountDownLatch latch(final String token) {
      return releases.computeIfAbsent(token, ignored -> new CountDownLatch(1));
    }

    private BlockingQueue<Boolean> startQueue(final String token) {
      return starts.computeIfAbsent(token, ignored -> new LinkedBlockingQueue<>());
    }
  }
}
