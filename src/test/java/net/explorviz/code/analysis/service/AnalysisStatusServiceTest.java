package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AnalysisStatusServiceTest {

  @Inject AnalysisStatusService analysisStatusService;

  @Test
  void requestCancellationMarksJobAsCancelled() {
    analysisStatusService.markPending("token-1");

    final AnalysisCancellationResult result = analysisStatusService.requestCancellation("token-1");

    Assertions.assertEquals(AnalysisCancellationResult.CANCELLED, result);
    Assertions.assertEquals(
        AnalysisStatusService.STATUS_CANCELLED,
        analysisStatusService.getStatus("token-1").orElseThrow());
    Assertions.assertTrue(analysisStatusService.isCancellationRequested("token-1"));
  }

  @Test
  void requestCancellationReturnsNotFoundWhenNoJobExists() {
    final AnalysisCancellationResult result = analysisStatusService.requestCancellation("missing");

    Assertions.assertEquals(AnalysisCancellationResult.NOT_FOUND, result);
  }

  @Test
  void requestCancellationReturnsAlreadyTerminalForFinishedJob() {
    analysisStatusService.markPending("token-1");
    analysisStatusService.markFinished("token-1");

    final AnalysisCancellationResult result = analysisStatusService.requestCancellation("token-1");

    Assertions.assertEquals(AnalysisCancellationResult.ALREADY_TERMINAL, result);
  }

  @Test
  void terminalStatesAreNotOverwrittenByMarkFailedOrMarkFinished() {
    analysisStatusService.markPending("token-1");
    analysisStatusService.requestCancellation("token-1");

    analysisStatusService.markFailed("token-1");
    analysisStatusService.markFinished("token-1");

    Assertions.assertEquals(
        AnalysisStatusService.STATUS_CANCELLED,
        analysisStatusService.getStatus("token-1").orElseThrow());
  }

  @Test
  void markPendingClearsCancellationRequest() {
    analysisStatusService.markPending("token-1");
    analysisStatusService.requestCancellation("token-1");

    analysisStatusService.markPending("token-1");

    Assertions.assertFalse(analysisStatusService.isCancellationRequested("token-1"));
    Assertions.assertEquals(
        AnalysisStatusService.STATUS_PENDING,
        analysisStatusService.getStatus("token-1").orElseThrow());
  }
}
