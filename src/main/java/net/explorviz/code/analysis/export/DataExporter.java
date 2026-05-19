package net.explorviz.code.analysis.export;

import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;

/**
 * A DataExporter handles the export of {@link FileData}, {@link CommitData} and request of {@link StateData}.
 */
public interface DataExporter {

  StateData getStateData(final String repositoryName, final String branchName, final String token,
      final Map<String, String> applicationPaths);

  void persistFile(final FileData fileData);

  void persistCommit(final CommitData commitData);

  void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent);

  boolean isRemote();

  boolean isInvalidCommitHash(final String hash);
}
