package net.explorviz.code.analysis.service;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.FileDiff;
import net.explorviz.code.analysis.types.RenamedFile;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AnalysisServiceRenameScopeTest {

  private static final ObjectId HASH =
      ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");

  private final FileDescriptor oldFile = new FileDescriptor(HASH, "Old.java", "src/Old.java");
  private final FileDescriptor newFile = new FileDescriptor(HASH, "New.java", "src/New.java");

  @Test
  void keepsRenameWhenBothSidesAreInScope() {
    final FileDiff diff = diffWithRename(List.of(newFile), List.of(oldFile));

    AnalysisService.removeRenamesOutsideScope(diff);

    Assertions.assertEquals(1, diff.renamed().size());
  }

  @Test
  void dropsRenameWhenOldSideWasFilteredOut() {
    final FileDiff diff = diffWithRename(List.of(newFile), List.of());

    AnalysisService.removeRenamesOutsideScope(diff);

    Assertions.assertTrue(diff.renamed().isEmpty());
    Assertions.assertEquals(List.of(newFile), diff.added());
  }

  @Test
  void dropsRenameWhenNewSideWasFilteredOut() {
    final FileDiff diff = diffWithRename(List.of(), List.of(oldFile));

    AnalysisService.removeRenamesOutsideScope(diff);

    Assertions.assertTrue(diff.renamed().isEmpty());
    Assertions.assertEquals(List.of(oldFile), diff.deleted());
  }

  private FileDiff diffWithRename(
      final List<FileDescriptor> added, final List<FileDescriptor> deleted) {
    final List<RenamedFile> renamed = new ArrayList<>();
    renamed.add(new RenamedFile(oldFile, newFile, 100));
    return new FileDiff(
        new ArrayList<>(added), new ArrayList<>(), new ArrayList<>(deleted), renamed);
  }
}
