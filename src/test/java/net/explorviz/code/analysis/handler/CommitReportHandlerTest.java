package net.explorviz.code.analysis.handler;

import com.google.protobuf.Timestamp;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.proto.CommitData;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommitReportHandlerTest {

  private CommitReportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CommitReportHandler();
    handler.init("commit-1", "parent-1", "main");
  }

  @Test
  void includesAllFileIdentifiersByDefault() {
    addSampleFiles();

    handler.setAnalysisFileCount(3);
    handler.setAuthorDate(Timestamp.newBuilder().setSeconds(1).build());
    handler.setCommitDate(Timestamp.newBuilder().setSeconds(2).build());

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(3, commitData.getAnalysisFileCount());
    Assertions.assertEquals(1, commitData.getAddedFilesCount());
    Assertions.assertEquals(1, commitData.getModifiedFilesCount());
    Assertions.assertEquals(1, commitData.getUnchangedFilesCount());
    Assertions.assertEquals(1, commitData.getDeletedFilesCount());
  }

  @Test
  void defersFileStubCreationWhenRequested() {
    addSampleFiles();

    handler.setAnalysisFileCount(3);
    handler.setDeferFileStubCreation(true);

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(3, commitData.getAnalysisFileCount());
    Assertions.assertTrue(commitData.getAddedFilesList().isEmpty());
    Assertions.assertTrue(commitData.getModifiedFilesList().isEmpty());
    Assertions.assertTrue(commitData.getUnchangedFilesList().isEmpty());
    Assertions.assertEquals(1, commitData.getDeletedFilesCount());
  }

  @Test
  void clearResetsDeferredMode() {
    addSampleFiles();
    handler.setDeferFileStubCreation(true);
    handler.getCommitData();

    handler.init("commit-2", null, "main");
    addSampleFiles();

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(1, commitData.getAddedFilesCount());
    Assertions.assertEquals(1, commitData.getModifiedFilesCount());
    Assertions.assertEquals(1, commitData.getUnchangedFilesCount());
  }

  private void addSampleFiles() {
    handler.addAdded(file("0123456789abcdef0123456789abcdef01234567", "src/Added.java"));
    handler.addModified(file("abcdef0123456789abcdef0123456789abcdef01", "src/Modified.java"));
    handler.addUnchanged(file("fedcba9876543210fedcba9876543210fedcba98", "src/Unchanged.java"));
    handler.addDeleted(file("1111111111111111111111111111111111111111", "src/Deleted.java"));
  }

  private static FileDescriptor file(final String hash, final String path) {
    final String fileName = path.substring(path.lastIndexOf('/') + 1);
    return new FileDescriptor(ObjectId.fromString(hash), fileName, path);
  }
}
