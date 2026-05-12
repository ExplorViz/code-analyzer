package net.explorviz.code.analysis.export;

import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.CommitServiceGrpc;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.ContributorServiceGrpc;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FileDataServiceGrpc;
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
  //
  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ CommitServiceGrpc.CommitServiceBlockingStub commitDataGrpcClient;
  //
  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ StateDataServiceGrpc.StateDataServiceBlockingStub stateDataGrpcClient;
  //
  @GrpcClient(GRPC_CLIENT_NAME)
  /* package */ ContributorServiceGrpc.ContributorServiceBlockingStub contributorDataGrpcClient;
  //
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
      final String applicationName, final String applicationRoot) {
    final StateDataRequest.Builder requestBuilder = StateDataRequest.newBuilder();
    requestBuilder.setBranchName(branchName);
    requestBuilder.setRepositoryName(repositoryName);
    requestBuilder.setLandscapeToken("".equals(token) ? landscapeTokenProperty : token);

    final String appName = "".equals(applicationName) ? applicationNameProperty : applicationName;
    requestBuilder.putApplicationPaths(appName, applicationRoot);

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
  public void persistCommit(final CommitData commitData) {
    LOGGER.info("Sending commit data on {}", commitData.getCommitId());
    try {
      commitDataGrpcClient.persistCommit(commitData);
    } catch (final Exception e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Failed to send commit data {}", commitData);
        LOGGER.error(e.getMessage());
      }
    }
  }

  @Override
  public void persistContributor(final ContributorData contributorData) {
    LOGGER.info("Sending contributor data on {}", contributorData.getGitUsername(), contributorData.getEmail());
    System.out.println(
        String.format("Sending contributor data on {}", contributorData.getGitUsername(), contributorData.getEmail()));
    try {
      contributorDataGrpcClient.persistContributor(contributorData);
    } catch (final Exception e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Failed to send contributor data {}", contributorData);
        LOGGER.error(e.getMessage());
      }
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
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Failed to send trackable resource event {}: {}", trackableResourceEvent.getAnnotationId(),
            e.getMessage());
        LOGGER.debug("Detailed event data: {}", trackableResourceEvent);
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
