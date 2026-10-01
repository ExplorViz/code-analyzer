package net.explorviz.code.analysis.handler;

import com.google.protobuf.Timestamp;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.RenamedFile;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.FileRename;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommitReportHandlerTest {

  private CommitReportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new CommitReportHandler();
    handler.init("commit-1", List.of("parent-1"), "main");
  }

  @Test
  void storesAllParentCommitIds() {
    handler.init("commit-1", List.of("parent-1", "parent-2"), "main");

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals("parent-1", commitData.getParentCommitId());
    Assertions.assertEquals(List.of("parent-1", "parent-2"), commitData.getParentCommitIdsList());
  }

  @Test
  void includesUnchangedFileIdentifiers() {
    addSampleFiles();
    handler.addUnchanged(file("2222222222222222222222222222222222222222", "src/Unchanged.java"));
    handler.setAnalysisFileCount(3);

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(3, commitData.getAnalysisFileCount());
    Assertions.assertEquals(1, commitData.getUnchangedFilesCount());
  }

  @Test
  void includesChangedFileIdentifiersAndAnalysisFileCount() {
    addSampleFiles();

    handler.setAnalysisFileCount(2);
    handler.setAuthorDate(Timestamp.newBuilder().setSeconds(1).build());
    handler.setCommitDate(Timestamp.newBuilder().setSeconds(2).build());

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(2, commitData.getAnalysisFileCount());
    Assertions.assertEquals(1, commitData.getAddedFilesCount());
    Assertions.assertEquals(1, commitData.getModifiedFilesCount());
    Assertions.assertEquals(0, commitData.getUnchangedFilesCount());
    Assertions.assertEquals(1, commitData.getDeletedFilesCount());
  }

  @Test
  void clearResetsFileLists() {
    addSampleFiles();
    handler.getCommitData();

    handler.init("commit-2", List.of(), "main");
    addSampleFiles();

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(1, commitData.getAddedFilesCount());
    Assertions.assertEquals(1, commitData.getModifiedFilesCount());
  }

  @Test
  void includesRenamedFilesWithBothSides() {
    final FileDescriptor oldFile = file("3333333333333333333333333333333333333333", "src/Old.java");
    final FileDescriptor newFile =
        file("4444444444444444444444444444444444444444", "src/pkg/New.java");
    handler.addDeleted(oldFile);
    handler.addAdded(newFile);
    handler.addRenamed(new RenamedFile(oldFile, newFile, 87));

    final CommitData commitData = handler.getCommitData();

    Assertions.assertEquals(1, commitData.getRenamedFilesCount());
    final FileRename rename = commitData.getRenamedFiles(0);
    Assertions.assertEquals("src/Old.java", rename.getOldFile().getFilePath());
    Assertions.assertEquals(
        "3333333333333333333333333333333333333333", rename.getOldFile().getFileHash());
    Assertions.assertEquals("src/pkg/New.java", rename.getNewFile().getFilePath());
    Assertions.assertEquals(
        "4444444444444444444444444444444444444444", rename.getNewFile().getFileHash());
    Assertions.assertEquals(87, rename.getSimilarity());
  }

  @Test
  void clearResetsRenamedFiles() {
    final FileDescriptor oldFile = file("3333333333333333333333333333333333333333", "src/Old.java");
    final FileDescriptor newFile = file("4444444444444444444444444444444444444444", "src/New.java");
    handler.addRenamed(new RenamedFile(oldFile, newFile, 100));

    handler.init("commit-2", List.of(), "main");

    Assertions.assertEquals(0, handler.getCommitData().getRenamedFilesCount());
  }

  private void addSampleFiles() {
    handler.addAdded(file("0123456789abcdef0123456789abcdef01234567", "src/Added.java"));
    handler.addModified(file("abcdef0123456789abcdef0123456789abcdef01", "src/Modified.java"));
    handler.addDeleted(file("1111111111111111111111111111111111111111", "src/Deleted.java"));
  }

  private static FileDescriptor file(final String hash, final String path) {
    final String fileName = path.substring(path.lastIndexOf('/') + 1);
    return new FileDescriptor(ObjectId.fromString(hash), fileName, path);
  }
}
