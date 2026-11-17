package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.FileDiff;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
class AnalysisServiceDiffBaseTest {

  @Inject AnalysisService analysisService;

  @Test
  void usesNullDiffBaseForFirstLocalCommitWithoutStartCommit() throws IOException {
    final CommitWalkEntry root = new CommitWalkEntry("a".repeat(40), 0, List.of());

    Assertions.assertNull(
        analysisService.resolveDiffBaseCommit(
            null, root, new PersistedCommitTracker(List.of(root), false)));
  }

  @Test
  void usesNullDiffBaseWhenParentIsNotKnownToReceiver() throws IOException {
    final CommitWalkEntry commit = new CommitWalkEntry("a".repeat(40), 0, List.of("b".repeat(40)));

    Assertions.assertNull(
        analysisService.resolveDiffBaseCommit(
            null, commit, new PersistedCommitTracker(List.of(commit), false)));
  }

  @Test
  void usesNullDiffBaseWhenPersistedParentIsMissingLocally(@TempDir final File repoDir)
      throws Exception {
    try (Git git = Git.init().setDirectory(repoDir).call();
        Repository repository = git.getRepository()) {
      final CommitWalkEntry commit =
          new CommitWalkEntry("a".repeat(40), 0, List.of("b".repeat(40)));

      Assertions.assertNull(
          analysisService.resolveDiffBaseCommit(
              repository, commit, new PersistedCommitTracker(List.of(commit), true)));
    }
  }

  @Test
  void usesFirstGitParentForSubsequentCommits(@TempDir final File repoDir) throws Exception {
    try (Git git = Git.init().setDirectory(repoDir).call();
        Repository repository = git.getRepository()) {
      final var readme = repoDir.toPath().resolve("README");
      Files.writeString(readme, "v1");
      git.add().addFilepattern("README").call();
      final RevCommit parent = git.commit().setMessage("first").setSign(false).call();

      Files.writeString(readme, "v2");
      git.add().addFilepattern("README").call();
      final RevCommit child = git.commit().setMessage("second").setSign(false).call();

      final CommitWalkEntry parentEntry = new CommitWalkEntry(parent.getName(), 0, List.of());
      final CommitWalkEntry childEntry =
          new CommitWalkEntry(child.getName(), 1, List.of(parent.getName()));
      final PersistedCommitTracker tracker =
          new PersistedCommitTracker(List.of(parentEntry, childEntry), false);
      tracker.markPersisted(parent.getName());

      final RevCommit diffBase =
          analysisService.resolveDiffBaseCommit(repository, childEntry, tracker);

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
    final FileDiff fileDiff =
        new FileDiff(List.of(allFiles.get(1)), List.of(allFiles.get(2)), List.of(), List.of());

    final List<FileDescriptor> unchangedFiles =
        analysisService.resolveUnchangedFilesForBootstrapCommit(allFiles, fileDiff);

    Assertions.assertEquals(1, unchangedFiles.size());
    Assertions.assertEquals("src/Unchanged.java", unchangedFiles.get(0).reportedPath);
  }

  @Test
  void hasRelevantChangesWhenAnyFileListIsNonEmpty() {
    final ObjectId hash = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
    final FileDescriptor file = new FileDescriptor(hash, "File.java", "src/File.java");

    Assertions.assertTrue(
        analysisService.hasRelevantFilteredFileChanges(List.of(file), List.of(), List.of()));
    Assertions.assertFalse(
        analysisService.hasRelevantFilteredFileChanges(List.of(), List.of(), List.of()));
  }
}
