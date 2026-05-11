package net.explorviz.code.analysis.export;

import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;

/**
 * Dummy to dump the data into void.
 */
public class VoidExporter implements DataExporter {

  @Override
  public StateData getStateData(final String repositoryName, final String branchName,
      final String token,
      final Map<String, String> applicationPaths) {
    return StateData.newBuilder().build();
  }

  @Override
  public void persistFile(final FileData fileData) {
    // DO NOTHING
  }

  @Override
  public void persistCommit(final CommitData commitData) {
    // DO NOTHING
  }

  @Override
  public boolean isRemote() {
    return false;
  }

  @Override
  public boolean isInvalidCommitHash(final String hash) {
    return false;
  }
}
