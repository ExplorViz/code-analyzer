package net.explorviz.code.analysis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRepositoryServiceTest {

  private static final String CLONE_ROOT_NAME = "cloned-repositories";

  @TempDir Path tempDir;

  private String originalUserDir;

  @BeforeEach
  void rememberUserDir() {
    originalUserDir = System.getProperty("user.dir");
    System.setProperty("user.dir", tempDir.toString());
  }

  @AfterEach
  void restoreUserDir() {
    System.setProperty("user.dir", originalUserDir);
  }

  @Test
  void listsRepositoriesWhenCloneRootIsSymlink() throws Exception {
    Assumptions.assumeTrue(supportsSymbolicLinks());

    final Path storage = tempDir.resolve("storage");
    Files.createDirectories(storage);
    initGitRepository(storage.resolve("my-project"));

    final Path cloneRootLink = tempDir.resolve(CLONE_ROOT_NAME);
    Files.createSymbolicLink(cloneRootLink, storage);

    final LocalRepositoryService service = newService();

    final List<LocalRepositoryInfo> repositories = service.listRepositories();

    Assertions.assertEquals(1, repositories.size());
    Assertions.assertEquals("my-project", repositories.get(0).path());
    Assertions.assertFalse(repositories.get(0).branches().isEmpty());
  }

  @Test
  void resolvesRelativePathWhenCloneRootIsSymlink() throws Exception {
    Assumptions.assumeTrue(supportsSymbolicLinks());

    final Path storage = tempDir.resolve("storage");
    Files.createDirectories(storage);
    initGitRepository(storage.resolve("my-project"));

    final Path cloneRootLink = tempDir.resolve(CLONE_ROOT_NAME);
    Files.createSymbolicLink(cloneRootLink, storage);

    final LocalRepositoryService service = newService();

    final Path resolved = service.resolveRelativeRepositoryPath("my-project");

    Assertions.assertTrue(Files.isDirectory(resolved.resolve(".git")));
  }

  private LocalRepositoryService newService() {
    final LocalRepositoryService service = new LocalRepositoryService();
    service.cloneRootProperty = CLONE_ROOT_NAME;
    return service;
  }

  private static void initGitRepository(final Path repositoryPath) throws Exception {
    Files.createDirectories(repositoryPath);
    try (Git git = Git.init().setDirectory(repositoryPath.toFile()).call()) {
      git.commit().setMessage("init").setAllowEmpty(true).call();
    }
  }

  private static boolean supportsSymbolicLinks() {
    try {
      final Path linkTarget = Files.createTempDirectory("symlink-target");
      final Path link = Files.createTempFile("symlink-test", ".link");
      Files.delete(link);
      Files.createSymbolicLink(link, linkTarget);
      Files.delete(link);
      Files.delete(linkTarget);
      return true;
    } catch (IOException | UnsupportedOperationException exception) {
      return false;
    }
  }
}
