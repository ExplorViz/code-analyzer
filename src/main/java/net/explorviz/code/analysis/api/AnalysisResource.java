package net.explorviz.code.analysis.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.io.IOException;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.export.GrpcExporter;
import net.explorviz.code.analysis.export.JsonExporter;
import net.explorviz.code.analysis.service.AnalysisConfig;
import net.explorviz.code.analysis.service.AnalysisProgressState;
import net.explorviz.code.analysis.service.AnalysisStatusService;
import net.explorviz.code.analysis.service.ConcurrentAnalysisService;
import net.explorviz.code.analysis.service.LocalRepositoryService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST resource for triggering Git analysis operations.
 */
@Path("/api/analysis")
public class AnalysisResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisResource.class);

  @ConfigProperty(name = "explorviz.gitanalysis.send-to-remote", defaultValue = "true")
  /* default */ boolean sendToRemoteProperty; // NOCS

  @Inject
  /* default */ ConcurrentAnalysisService analysisService; // NOCS

  @Inject
  /* default */ GrpcExporter grpcExporter; // NOCS

  @Inject
  /* default */ AnalysisStatusService analysisStatusService; // NOCS

  @Inject
  /* default */ LocalRepositoryService localRepositoryService; // NOCS

  /**
   * Triggers a Git repository analysis with the provided configuration. The
   * request is queued and processed
   * asynchronously to handle concurrent requests safely.
   *
   * @param request The analysis request containing configuration
   * @return Response indicating the request was accepted (202) or an error
   *         occurred
   */
  @POST
  @Path("/trigger")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public Response triggerAnalysis(final AnalysisRequest request) {
    if (request == null) {
      LOGGER.error("Request body is null or invalid");
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("Request body is required")
          .build();
    }

    try {
      final String repoInfo = request.getRepoPath() != null ? request.getRepoPath()
          : (request.getRepoRemoteUrl() != null ? request.getRepoRemoteUrl() : "unknown");
      LOGGER.info("📥 Received analysis request for repository: {}", repoInfo);

      final String landscapeToken = request.getLandscapeToken();
      analysisStatusService.markPending(landscapeToken);

      final AnalysisConfig config = request.toConfig();

      final DataExporter exporter;
      if (request.isSendToRemote()) {
        exporter = grpcExporter;
      } else {
        exporter = new JsonExporter(config.getRepositoryName(), config.primaryApplicationNameForExport());
      }

      // Submit to queue for async processing
      analysisService.analyzeAndSendRepoAsync(config, exporter)
          .whenComplete((result, error) -> {
            if (error != null) {
              analysisStatusService.markFailed(landscapeToken);
              LOGGER.error("❌ Async analysis failed for {}: {}",
                  repoInfo, error.getMessage());
            } else {
              analysisStatusService.markFinished(landscapeToken);
              LOGGER.info("✅ Async analysis completed for {}", repoInfo);
            }
          });

      LOGGER.info("✅ Analysis request queued for repository: {}", repoInfo);
      return Response.status(Response.Status.ACCEPTED)
          .entity("Analysis request accepted and queued for processing")
          .build();

    } catch (Exception e) {
      LOGGER.error("❌ Failed to queue analysis request: {}", e.getMessage(), e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to queue analysis request: " + e.getMessage())
          .build();
    }
  }

  @GET
  @Path("/local-repositories")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getLocalRepositories() {
    try {
      return Response.ok(localRepositoryService.listRepositories()).build();
    } catch (IOException exception) {
      LOGGER.error("Failed to list local repositories: {}", exception.getMessage(), exception);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("Failed to list local repositories")
          .build();
    }
  }

  @GET
  @Path("/status/{landscapeToken}")
  @Produces(MediaType.TEXT_PLAIN)
  public Response getStatusByLandscapeToken(@PathParam("landscapeToken") final String landscapeToken) {
    return analysisStatusService.getStatus(landscapeToken)
        .map(status -> Response.ok(status).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
            .entity("No analysis status found for landscapeToken=" + landscapeToken)
            .build());
  }

  @GET
  @Path("/state/{landscapeToken}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getStateByLandscapeToken(@PathParam("landscapeToken") final String landscapeToken) {
    return analysisStatusService.getState(landscapeToken)
        .map(state -> Response.ok(state).build())
        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
            .entity("No analysis state found for landscapeToken=" + landscapeToken)
            .build());
  }

  @GET
  @Path("/state/stream/{landscapeToken}")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  public void streamStateByLandscapeToken(@PathParam("landscapeToken") final String landscapeToken,
      final SseEventSink eventSink,
      final Sse sse) {
    analysisStatusService.subscribeToStateUpdates(landscapeToken, eventSink, sse);
  }
}
