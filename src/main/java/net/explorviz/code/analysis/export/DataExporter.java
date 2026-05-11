package net.explorviz.code.analysis.export;

import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;

/**
 * A DataExporter handles the export of {@link FileData}, {@link CommitData} and request of {@link StateData}.
 */
public interface DataExporter {

  StateData getStateData(final String repositoryName, final String branchName, final String token,
      final Map<String, String> applicationPaths);

  void persistFile(final FileData fileData);

  void persistCommit(final CommitData commitData);

  boolean isRemote();

  boolean isInvalidCommitHash(final String hash);
}
