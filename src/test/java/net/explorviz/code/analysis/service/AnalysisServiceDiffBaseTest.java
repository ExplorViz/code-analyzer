package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.Triple;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class AnalysisServiceDiffBaseTest {

  @Inject
  AnalysisService analysisService;

  @Test
  void usesNullDiffBaseForFirstLocalCommitWithoutStartCommit() {
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(0);

    Assertions.assertNull(
        analysisService.resolveDiffBaseCommit(
            commit, 0, false, Optional.empty(), null));
  }

  @Test
  void usesLastCheckedCommitForFirstRemoteCommitWithStartCommitWhenGitParentMissing() {
    final RevCommit lastChecked = Mockito.mock(RevCommit.class);
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(0);

    Assertions.assertSame(
        lastChecked,
        analysisService.resolveDiffBaseCommit(
            commit, 0, true, Optional.of("parent"), lastChecked));
  }

  @Test
  void usesFirstGitParentForSubsequentCommits() {
    final RevCommit firstParent = Mockito.mock(RevCommit.class);
    final RevCommit lastChecked = Mockito.mock(RevCommit.class);
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(1);
    Mockito.doReturn(firstParent).when(commit).getParent(0);

    Assertions.assertSame(
        firstParent,
        analysisService.resolveDiffBaseCommit(
            commit, 1, false, Optional.empty(), lastChecked));
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
