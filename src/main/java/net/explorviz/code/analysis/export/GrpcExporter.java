package net.explorviz.code.analysis.export;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.CommitServiceGrpc;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FileDataServiceGrpc;
import net.explorviz.code.proto.MutinyFileDataServiceGrpc;
import net.explorviz.code.proto.RelinkResourcesRequest;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.StateDataRequest;
import net.explorviz.code.proto.StateDataServiceGrpc;
import net.explorviz.code.proto.TrackableResourceEvent;
import net.explorviz.code.proto.TrackableResourceServiceGrpc;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic GRPC handler.
 */
@ApplicationScoped
public final class GrpcExporter implements DataExporter {

  public static final Logger LOGGER = LoggerFactory.getLogger(GrpcExporter.class);

  private static final String GRPC_CLIENT_NAME = "codeAnalysisGrpcClient";

  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ FileDataServiceGrpc.FileDataServiceBlockingStub fileDataGrpcClient;

  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ MutinyFileDataServiceGrpc.MutinyFileDataServiceStub fileDataMutinyGrpcClient;

  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ CommitServiceGrpc.CommitServiceBlockingStub commitDataGrpcClient;

  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ StateDataServiceGrpc.StateDataServiceBlockingStub stateDataGrpcClient;

  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ TrackableResourceServiceGrpc.TrackableResourceServiceBlockingStub trackableResourceGrpcClient;

  @ConfigProperty(name = "explorviz.landscape.token")
  /* default */ String landscapeTokenProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.application-name")
  /* default */ String applicationNameProperty;

  /**
   * Requests the state data from the remote endpoint.
   *
   * @param branchName the branch for the analysis
   * @return the state of the remote database
   */
  @Override
  public StateData getStateData(final String repositoryName, final String branchName,
      final String token,
      final Map<String, String> applicationPaths, final String repositoryUrl,
      final boolean skipLatestCommitLookup) {
    final StateDataRequest.Builder requestBuilder = StateDataRequest.newBuilder();
    requestBuilder.setBranchName(branchName);
    requestBuilder.setRepositoryName(repositoryName);
    requestBuilder.setLandscapeToken("".equals(token) ? landscapeTokenProperty : token);
    requestBuilder.setSkipLatestCommitLookup(skipLatestCommitLookup);
    if (repositoryUrl != null && !repositoryUrl.isBlank()) {
      requestBuilder.setRepositoryUrl(repositoryUrl);
    }

    if (applicationPaths == null || applicationPaths.isEmpty()) {
      requestBuilder.putApplicationPaths(applicationNameProperty, "");
    } else {
      for (final Map.Entry<String, String> entry : applicationPaths.entrySet()) {
        final String key = "".equals(entry.getKey()) ? applicationNameProperty : entry.getKey();
        final String value = entry.getValue() != null ? entry.getValue() : "";
        requestBuilder.putApplicationPaths(key, value);
      }
    }

    final StateDataRequest request = requestBuilder.build();
    LOGGER.debug("Sending state request: {}", request);
    return stateDataGrpcClient.getStateData(request);
  }

  @Override
  public void persistFile(final FileData fileData) {
    try {
      fileDataGrpcClient.persistFile(fileData);
    } catch (final Exception e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Failed to send file data {}", fileData);
        LOGGER.info(e.getMessage());
      }
    }
  }

  @Override
  public void persistFilesBatch(final List<FileData> files) {
    if (files.isEmpty()) {
      return;
    }
    LOGGER.info("Sending batch of {} files via streaming RPC", files.size());
    try {
      fileDataMutinyGrpcClient
          .persistFiles(Multi.createFrom().items(files.stream()))
          .await()
          .indefinitely();
    } catch (final Exception e) {
      LOGGER.error("Failed to send batch of {} files: {}", files.size(), e.getMessage());
      throw new RuntimeException("Failed to send file batch to landscape-service", e);
    }
  }

  @Override
  public void persistCommit(final CommitData commitData) {
    LOGGER.info("Sending commit data on {}", commitData.getCommitId());
    try {
      commitDataGrpcClient.persistCommit(commitData);
    } catch (final Exception e) {
      LOGGER.error("Failed to send commit data {}", commitData.getCommitId(), e);
      throw new RuntimeException("Failed to send commit data for " + commitData.getCommitId(), e);
    }
  }

  @Override
  public void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent) {
    LOGGER.info(
        "Sending TrackableResourceEvent {} for {} #{}",
        trackableResourceEvent.getAnnotationType(),
        trackableResourceEvent.getResourceType(),
        trackableResourceEvent.getResourceId()
    );
    try {
      trackableResourceGrpcClient.persistTrackableResourceEvent(trackableResourceEvent);
    } catch (final Exception e) {
      LOGGER.error("Failed to send trackable resource event {}: {}", trackableResourceEvent.getAnnotationId(),
          e.getMessage());
      throw new RuntimeException("Failed to send trackable resource event for "
          + trackableResourceEvent.getAnnotationId(), e);
    }
  }
  
  @Override
  public void relinkResourceEvents(final String token, final String repoName) {
    LOGGER.info("Sending relink request on {}", repoName);
    try {
      RelinkResourcesRequest request = RelinkResourcesRequest.newBuilder()
          .setLandscapeToken(token)
          .setRepositoryName(repoName)
          .build();
      trackableResourceGrpcClient.relinkResources(request);
    } catch (final Exception e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Failed to send relink request on {}: {}", repoName,  e.getMessage());
      }
    }
  }

  @Override
  public boolean isRemote() {
    return true;
  }

  @Override
  public boolean isInvalidCommitHash(final String hash) {
    return "0000000000000000000000000000000000000000".equals(hash);
  }
}
