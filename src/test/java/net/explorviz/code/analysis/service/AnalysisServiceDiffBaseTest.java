package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.Triple;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class AnalysisServiceDiffBaseTest {

  @Inject
  AnalysisService analysisService;

  @Test
  void analyzesAllFilesForFirstCommitWhenLandscapeHasNoPersistedCommits() {
    Assertions.assertTrue(analysisService.shouldAnalyzeAllFilesInCommit(0, false));
  }

  @Test
  void doesNotAnalyzeAllFilesForFirstCommitWhenLandscapeAlreadyHasCommits() {
    Assertions.assertFalse(analysisService.shouldAnalyzeAllFilesInCommit(0, true));
  }

  @Test
  void doesNotAnalyzeAllFilesForSubsequentCommits() {
    Assertions.assertFalse(analysisService.shouldAnalyzeAllFilesInCommit(1, false));
  }

  @Test
  void usesParentDiffBaseWhenAvailable() {
    final var baseCommit = Mockito.mock(org.eclipse.jgit.revwalk.RevCommit.class);

    Assertions.assertTrue(analysisService.resolveDiffBaseCommit(baseCommit).isPresent());
    Assertions.assertSame(
        baseCommit,
        analysisService.resolveDiffBaseCommit(baseCommit).orElseThrow());
  }

  @Test
  void usesEmptyDiffBaseForRootCommitWithoutParent() {
    Assertions.assertTrue(analysisService.resolveDiffBaseCommit(null).isEmpty());
  }

  @Test
  void resolvesUnchangedFilesForBootstrapCommit() {
    final ObjectId unchangedHash = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
    final ObjectId addedHash = ObjectId.fromString("abcdef0123456789abcdef0123456789abcdef01");
    final ObjectId modifiedHash = ObjectId.fromString("fedcba9876543210fedcba9876543210fedcba98");

    final List<FileDescriptor> allFiles = List.of(
        new FileDescriptor(unchangedHash, "Unchanged.java", "src/Unchanged.java"),
        new FileDescriptor(addedHash, "Added.java", "src/Added.java"),
        new FileDescriptor(modifiedHash, "Modified.java", "src/Modified.java"));
    final var reportTriple = new Triple<List<FileDescriptor>, List<FileDescriptor>, List<FileDescriptor>>(
        List.of(allFiles.get(2)),
        List.of(),
        List.of(allFiles.get(1)));

    final List<FileDescriptor> unchangedFiles =
        analysisService.resolveUnchangedFilesForBootstrapCommit(allFiles, reportTriple);

    Assertions.assertEquals(1, unchangedFiles.size());
    Assertions.assertEquals("src/Unchanged.java", unchangedFiles.get(0).reportedPath);
  }
}
