package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.Triple;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@QuarkusTest
class AnalysisServiceDiffBaseTest {

  @Inject AnalysisService analysisService;

  @Test
  void usesNullDiffBaseForFirstLocalCommitWithoutStartCommit() throws IOException {
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(0);

    Assertions.assertNull(
        analysisService.resolveDiffBaseCommit(
            null, commit, 0, false, Optional.empty(), null, null, Set.of()));
  }

  @Test
  void usesNullDiffBaseWhenGitParentMissingAndNoLastCheckedCommit() throws IOException {
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(0);

    Assertions.assertNull(
        analysisService.resolveDiffBaseCommit(
            null, commit, 0, true, Optional.of("parent"), null, null, Set.of()));
  }

  @Test
  void usesFirstGitParentForSubsequentCommits(@TempDir final File repoDir) throws Exception {
    try (Git git = Git.init().setDirectory(repoDir).call();
        Repository repository = git.getRepository()) {
      final var readme = repoDir.toPath().resolve("README");
      Files.writeString(readme, "v1");
      git.add().addFilepattern("README").call();
      final RevCommit parent = git.commit().setMessage("first").call();

      Files.writeString(readme, "v2");
      git.add().addFilepattern("README").call();
      final RevCommit child = git.commit().setMessage("second").call();

      final RevCommit childForDiff;
      try (RevWalk walk = new RevWalk(repository)) {
        childForDiff = walk.parseCommit(child.getId());
      }

      final RevCommit diffBase =
          analysisService.resolveDiffBaseCommit(
              repository, childForDiff, 1, false, Optional.empty(), "ignored", null, Set.of());

      Assertions.assertNotNull(diffBase);
      Assertions.assertEquals(parent.getId(), diffBase.getId());
      Assertions.assertNotNull(diffBase.getTree());
    }
  }

  @Test
  void resolvesUnchangedFilesForBootstrapCommit() {
    final ObjectId unchangedHash = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
    final ObjectId addedHash = ObjectId.fromString("abcdef0123456789abcdef0123456789abcdef01");
    final ObjectId modifiedHash = ObjectId.fromString("fedcba9876543210fedcba9876543210fedcba98");

    final List<FileDescriptor> allFiles =
        List.of(
            new FileDescriptor(unchangedHash, "Unchanged.java", "src/Unchanged.java"),
            new FileDescriptor(addedHash, "Added.java", "src/Added.java"),
            new FileDescriptor(modifiedHash, "Modified.java", "src/Modified.java"));
    final var reportTriple =
        new Triple<List<FileDescriptor>, List<FileDescriptor>, List<FileDescriptor>>(
            List.of(allFiles.get(2)), List.of(), List.of(allFiles.get(1)));

    final List<FileDescriptor> unchangedFiles =
        analysisService.resolveUnchangedFilesForBootstrapCommit(allFiles, reportTriple);

    Assertions.assertEquals(1, unchangedFiles.size());
    Assertions.assertEquals("src/Unchanged.java", unchangedFiles.get(0).reportedPath);
  }
}
