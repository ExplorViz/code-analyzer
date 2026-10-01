package net.explorviz.code.analysis.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.FileDiff;
import net.explorviz.code.analysis.types.RenamedFile;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that {@link GitRepositoryHandler#listDiff} reports renamed files. */
class GitRepositoryHandlerRenameTest {

  private static final String ORIGINAL_PATH = "src/Original.java";

  private final GitRepositoryHandler handler = new GitRepositoryHandler();

  @TempDir Path repoDir;

  @Test
  void detectsPureRenameWithinDirectory() throws Exception {
    final String content = lines("line", 20);

    final FileDiff diff = diffOfRename(content, ORIGINAL_PATH, "src/Renamed.java", content);

    Assertions.assertEquals(1, diff.renamed().size());
    final RenamedFile renamed = diff.renamed().get(0);
    Assertions.assertEquals(ORIGINAL_PATH, renamed.oldFile().reportedPath);
    Assertions.assertEquals("src/Renamed.java", renamed.newFile().reportedPath);
    Assertions.assertEquals(100, renamed.similarity());
    Assertions.assertEquals(renamed.oldFile().objectId, renamed.newFile().objectId);
  }

  @Test
  void reportsRenameAsDeletionAndAddition() throws Exception {
    final String content = lines("line", 20);

    final FileDiff diff = diffOfRename(content, ORIGINAL_PATH, "src/Renamed.java", content);

    Assertions.assertEquals(List.of(ORIGINAL_PATH), paths(diff.deleted()));
    Assertions.assertEquals(List.of("src/Renamed.java"), paths(diff.added()));
    Assertions.assertTrue(diff.modified().isEmpty());
    Assertions.assertSame(diff.renamed().get(0).oldFile(), diff.deleted().get(0));
    Assertions.assertSame(diff.renamed().get(0).newFile(), diff.added().get(0));
  }

  @Test
  void detectsRenameAcrossDirectories() throws Exception {
    final String content = lines("line", 20);

    final FileDiff diff =
        diffOfRename(content, ORIGINAL_PATH, "src/moved/deeper/Original.java", content);

    Assertions.assertEquals(1, diff.renamed().size());
    Assertions.assertEquals("Original.java", diff.renamed().get(0).newFile().fileName);
    Assertions.assertEquals(
        "src/moved/deeper/Original.java", diff.renamed().get(0).newFile().reportedPath);
  }

  @Test
  void detectsRenameWithModifiedContent() throws Exception {
    final String before = lines("line", 20);
    final String after =
        before.replace("line 3\n", "changed 3\n").replace("line 9\n", "changed 9\n");

    final FileDiff diff = diffOfRename(before, ORIGINAL_PATH, "src/Renamed.java", after);

    Assertions.assertEquals(1, diff.renamed().size());
    final RenamedFile renamed = diff.renamed().get(0);
    Assertions.assertNotEquals(renamed.oldFile().objectId, renamed.newFile().objectId);
    Assertions.assertTrue(
        renamed.similarity() >= 50 && renamed.similarity() < 100,
        "unexpected similarity " + renamed.similarity());
    Assertions.assertTrue(diff.modified().isEmpty());
  }

  @Test
  void doesNotTreatUnrelatedFilesAsRenamed() throws Exception {
    final FileDiff diff =
        diffOfRename(
            lines("old", 20),
            ORIGINAL_PATH,
            "src/Unrelated.java",
            lines("completely different", 20));

    Assertions.assertTrue(diff.renamed().isEmpty());
    Assertions.assertEquals(List.of(ORIGINAL_PATH), paths(diff.deleted()));
    Assertions.assertEquals(List.of("src/Unrelated.java"), paths(diff.added()));
  }

  @Test
  void detectsRenameRegardlessOfLocalGitConfiguration() throws Exception {
    final String content = lines("line", 20);

    final FileDiff diff =
        diffOfRename(
            content,
            ORIGINAL_PATH,
            "src/Renamed.java",
            content,
            config -> config.setBoolean("diff", null, "renames", false));

    Assertions.assertEquals(1, diff.renamed().size());
  }

  @Test
  void detectsEveryFileOfAMovedDirectory() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      for (int index = 0; index < 5; index++) {
        write("old/File" + index + ".java", lines("file " + index + " line", 15));
      }
      final RevCommit before = commitAll(git, "before");

      for (int index = 0; index < 5; index++) {
        Files.move(
            repoDir.resolve("old/File" + index + ".java"),
            createParent(repoDir.resolve("new/File" + index + ".java")));
      }
      final RevCommit after = commitAll(git, "move directory");

      final FileDiff diff = diff(git.getRepository(), before, after);

      Assertions.assertEquals(5, diff.renamed().size());
      Assertions.assertEquals(5, diff.added().size());
      Assertions.assertEquals(5, diff.deleted().size());
      diff.renamed()
          .forEach(
              renamed ->
                  Assertions.assertEquals(renamed.oldFile().fileName, renamed.newFile().fileName));
    }
  }

  @Test
  void reportsPlainModificationsWithoutRenames() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      write(ORIGINAL_PATH, lines("line", 10));
      final RevCommit before = commitAll(git, "before");
      write(ORIGINAL_PATH, lines("line", 11));
      final RevCommit after = commitAll(git, "after");

      final FileDiff diff = diff(git.getRepository(), before, after);

      Assertions.assertEquals(List.of(ORIGINAL_PATH), paths(diff.modified()));
      Assertions.assertTrue(diff.renamed().isEmpty());
      Assertions.assertTrue(diff.added().isEmpty());
      Assertions.assertTrue(diff.deleted().isEmpty());
    }
  }

  @Test
  void bootstrapCommitHasNoRenames() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      write(ORIGINAL_PATH, lines("line", 10));
      final RevCommit first = commitAll(git, "first");

      final FileDiff diff = diff(git.getRepository(), null, first);

      Assertions.assertEquals(List.of(ORIGINAL_PATH), paths(diff.added()));
      Assertions.assertTrue(diff.renamed().isEmpty());
    }
  }

  private FileDiff diffOfRename(
      final String oldContent, final String oldPath, final String newPath, final String newContent)
      throws Exception {
    return diffOfRename(oldContent, oldPath, newPath, newContent, config -> {});
  }

  private FileDiff diffOfRename(
      final String oldContent,
      final String oldPath,
      final String newPath,
      final String newContent,
      final ConfigCustomizer customizer)
      throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final StoredConfig config = git.getRepository().getConfig();
      customizer.customize(config);
      config.save();

      write(oldPath, oldContent);
      final RevCommit before = commitAll(git, "before");

      Files.delete(repoDir.resolve(oldPath));
      write(newPath, newContent);
      final RevCommit after = commitAll(git, "rename");

      return diff(git.getRepository(), before, after);
    }
  }

  private FileDiff diff(
      final Repository repository, final RevCommit oldCommit, final RevCommit newCommit)
      throws Exception {
    try (RevWalk walk = new RevWalk(repository)) {
      final RevCommit parsedNew = walk.parseCommit(newCommit.getId());
      final Optional<RevCommit> parsedOld =
          Optional.ofNullable(oldCommit).map(commit -> parse(walk, commit));
      return handler.listDiff(repository, parsedOld, parsedNew, List.of());
    }
  }

  private static RevCommit parse(final RevWalk walk, final RevCommit commit) {
    try {
      return walk.parseCommit(commit.getId());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private RevCommit commitAll(final Git git, final String message) throws Exception {
    git.add().addFilepattern(".").call();
    git.add().setUpdate(true).addFilepattern(".").call();
    return git.commit().setMessage(message).call();
  }

  private void write(final String relativePath, final String content) throws IOException {
    Files.writeString(createParent(repoDir.resolve(relativePath)), content);
  }

  private static Path createParent(final Path file) throws IOException {
    Files.createDirectories(file.getParent());
    return file;
  }

  private static String lines(final String prefix, final int count) {
    return IntStream.range(0, count)
        .mapToObj(index -> prefix + " " + index + "\n")
        .collect(Collectors.joining());
  }

  private static List<String> paths(final List<FileDescriptor> files) {
    return files.stream().map(file -> file.reportedPath).toList();
  }

  @FunctionalInterface
  private interface ConfigCustomizer {
    void customize(StoredConfig config);
  }
}
