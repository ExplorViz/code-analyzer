package net.explorviz.code.analysis.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.analysis.types.FileDiff;
import net.explorviz.code.analysis.types.RemoteRepositoryObject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies path restrictions, commit reachability and repository opening. */
class GitRepositoryHandlerScopeTest {

  private static final List<String> ALL_FILES =
      List.of(
          "README.md",
          "src/Main.java",
          "src/util/Helper.java",
          "src/util/notes.txt",
          "srcgen/Generated.java",
          "backend/Service.java",
          "frontend/app.ts");

  private final GitRepositoryHandler handler = new GitRepositoryHandler();

  @TempDir Path repoDir;

  @Test
  void directoryRestrictionSelectsAllFilesBelowIt() throws Exception {
    Assertions.assertEquals(
        List.of("src/Main.java", "src/util/Helper.java", "src/util/notes.txt"), listFiles("src"));
  }

  @Test
  void recursiveGlobIncludesFilesDirectlyInTheDirectory() throws Exception {
    Assertions.assertEquals(
        List.of("src/Main.java", "src/util/Helper.java", "src/util/notes.txt"),
        listFiles("src/**"));
  }

  @Test
  void trailingAndLeadingSlashesAreIgnored() throws Exception {
    Assertions.assertEquals(List.of("backend/Service.java"), listFiles("/backend/"));
    Assertions.assertEquals(List.of("backend/Service.java"), listFiles("./backend"));
  }

  @Test
  void fileGlobSelectsMatchingFiles() throws Exception {
    Assertions.assertEquals(
        List.of(
            "backend/Service.java",
            "src/Main.java",
            "src/util/Helper.java",
            "srcgen/Generated.java"),
        listFiles("**/*.java"));
  }

  @Test
  void severalRestrictionsAreCombined() throws Exception {
    Assertions.assertEquals(
        List.of("backend/Service.java", "frontend/app.ts"), listFiles("backend, frontend"));
  }

  @Test
  void restrictionWithoutMatchSelectsNothing() throws Exception {
    Assertions.assertEquals(List.of(), listFiles("does-not-exist"));
  }

  @Test
  void blankOrRootRestrictionSelectsEverything() throws Exception {
    Assertions.assertEquals(sorted(ALL_FILES), listFiles(""));
    Assertions.assertEquals(sorted(ALL_FILES), listFiles("/"));
  }

  @Test
  void restrictionsAreMatchedAgainstTheCommitNotTheWorkingTree() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit commit = commitAll(git, ALL_FILES);
      deleteRecursively(repoDir.resolve("backend"));

      Assertions.assertEquals(
          List.of("backend/Service.java"), paths(list(git.getRepository(), commit, "backend")));
    }
  }

  @Test
  void diffOnlyReportsChangesWithinRestrictions() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit before = commitAll(git, ALL_FILES);
      write("src/Main.java", "changed");
      write("frontend/app.ts", "changed");
      write("src/New.java", "new");
      final RevCommit after = commitAll(git, List.of());

      final FileDiff diff = diff(git.getRepository(), before, after, "src");

      Assertions.assertEquals(List.of("src/New.java"), paths(diff.added()));
      Assertions.assertEquals(List.of("src/Main.java"), paths(diff.modified()));
    }
  }

  @Test
  void malformedRestrictionIsRejected() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit commit = commitAll(git, ALL_FILES);
      Assertions.assertThrows(
          IllegalArgumentException.class, () -> list(git.getRepository(), commit, "src/[a"));
    }
  }

  @Test
  void ancestorsAndTipAreReachable() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit first = commitAll(git, List.of("a.txt"));
      final RevCommit second = commitAll(git, List.of("b.txt"));
      final Repository repository = git.getRepository();
      final String branch = repository.getFullBranch();

      Assertions.assertTrue(handler.isReachableCommit(repository, first.getName(), branch));
      Assertions.assertTrue(handler.isReachableCommit(repository, second.getName(), branch));
      Assertions.assertTrue(
          handler.isReachableCommit(repository, first.getName().substring(0, 10), branch));
      Assertions.assertFalse(
          handler.isUnreachableCommit(repository, Optional.of(first.getName()), branch));
      Assertions.assertFalse(handler.isUnreachableCommit(repository, Optional.empty(), branch));
    }
  }

  @Test
  void commitsOfOtherBranchesAreUnreachable() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      commitAll(git, List.of("a.txt"));
      final Repository repository = git.getRepository();
      final String mainBranch = repository.getFullBranch();
      git.checkout().setCreateBranch(true).setName("feature").call();
      final RevCommit featureCommit = commitAll(git, List.of("feature.txt"));

      Assertions.assertFalse(
          handler.isReachableCommit(repository, featureCommit.getName(), mainBranch));
    }
  }

  @Test
  void missingOrMalformedCommitsAreUnreachableInsteadOfFailing() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      commitAll(git, List.of("a.txt"));
      final Repository repository = git.getRepository();
      final String branch = repository.getFullBranch();

      Assertions.assertFalse(
          handler.isReachableCommit(repository, ObjectId.zeroId().name(), branch));
      Assertions.assertFalse(
          handler.isReachableCommit(
              repository, "1234567890123456789012345678901234567890", branch));
      Assertions.assertFalse(handler.isReachableCommit(repository, "not a commit", branch));
    }
  }

  @Test
  void detachedHeadCanBeUsedAsBranch() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit first = commitAll(git, List.of("a.txt"));
      final RevCommit second = commitAll(git, List.of("b.txt"));
      git.checkout().setName(second.getName()).call();
      final Repository repository = git.getRepository();

      // On a detached HEAD, the "full branch" is the commit id.
      Assertions.assertEquals(second.getName(), repository.getFullBranch());
      Assertions.assertTrue(
          handler.isReachableCommit(repository, first.getName(), repository.getFullBranch()));
    }
  }

  @Test
  void emptyDirectoryIsNoRepository() throws Exception {
    final Path emptyDir = Files.createDirectory(repoDir.resolve("empty"));

    Assertions.assertThrows(
        RepositoryNotFoundException.class,
        () ->
            handler.getGitRepository(
                emptyDir.toAbsolutePath().toString(), new RemoteRepositoryObject()));
  }

  private List<String> listFiles(final String restrictions) throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit commit = commitAll(git, ALL_FILES);
      return paths(list(git.getRepository(), commit, restrictions));
    }
  }

  private List<FileDescriptor> list(
      final Repository repository, final RevCommit commit, final String restrictions)
      throws IOException {
    try (RevWalk walk = new RevWalk(repository)) {
      return handler.listFilesInCommit(repository, walk.parseCommit(commit), restrictions);
    }
  }

  private FileDiff diff(
      final Repository repository,
      final RevCommit oldCommit,
      final RevCommit newCommit,
      final String restrictions)
      throws IOException {
    try (RevWalk walk = new RevWalk(repository)) {
      return handler.listDiff(
          repository,
          Optional.of(walk.parseCommit(oldCommit)),
          walk.parseCommit(newCommit),
          restrictions);
    }
  }

  private RevCommit commitAll(final Git git, final List<String> files) throws Exception {
    for (final String file : files) {
      write(file, "content of " + file);
    }
    git.add().addFilepattern(".").call();
    return git.commit().setMessage("commit").setAllowEmpty(true).call();
  }

  private void write(final String relativePath, final String content) throws IOException {
    final Path file = repoDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static void deleteRecursively(final Path directory) throws IOException {
    try (var walk = Files.walk(directory)) {
      for (final Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  private static List<String> paths(final List<FileDescriptor> files) {
    return sorted(files.stream().map(file -> file.reportedPath).toList());
  }

  private static List<String> sorted(final List<String> values) {
    return values.stream().sorted().toList();
  }
}
