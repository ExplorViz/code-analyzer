package net.explorviz.code.analysis.git;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import net.explorviz.code.analysis.types.RemoteRepositoryObject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Testing the repository loader. */
@QuarkusTest
public class GitRepositoryHandlerTest {

  private static final String MASTER = "master";
  private final String sshUrl = "git@gitlab.com:0xhexdec/busydoingnothing.git";
  private final String httpsUrl = "https://gitlab.com/0xhexdec/busydoingnothing.git";

  @Inject GitRepositoryHandler gitRepositoryHandler;
  private File tempGitLocation;

  @BeforeEach
  void setup() throws IOException {
    tempGitLocation = Files.createTempDirectory("explorviz-test").toFile();
  }

  @AfterEach
  void tearDown() {
    try (Stream<Path> walk = Files.walk(tempGitLocation.toPath())) {
      walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    } catch (IOException exception) {
      System.err.println("Folder not deletable");
    }
  }

  @Test()
  void testInvalidRemote() {
    String url = "%%%%";

    Assertions.assertThrows(
        InvalidRemoteException.class,
        () -> {
          this.gitRepositoryHandler.getGitRepository(
              "", new RemoteRepositoryObject(url, tempGitLocation.getAbsolutePath(), MASTER));
        });
  }

  @Test
  void testInvalidParameters() {
    Assertions.assertThrows(
        InvalidRemoteException.class,
        () -> {
          this.gitRepositoryHandler.getGitRepository(
              "", new RemoteRepositoryObject("", "", MASTER));
        });
  }

  @Test()
  void testMalformedRemote() {
    String url = "https://gitlab.com/0xhexdec/";
    Assertions.assertThrows(
        MalformedURLException.class,
        () -> {
          this.gitRepositoryHandler.getGitRepository(
              "", new RemoteRepositoryObject(url, tempGitLocation.getAbsolutePath(), MASTER));
        });
  }

  @Test
  void testFileInsteadDirectory() throws IOException {
    File file = new File(tempGitLocation.getAbsolutePath() + "/file");
    Assertions.assertTrue(file.createNewFile());
    Assertions.assertThrows(
        NotDirectoryException.class,
        () -> {
          this.gitRepositoryHandler.getGitRepository(
              file.getAbsolutePath(), new RemoteRepositoryObject("", "", MASTER));
        });
  }

  @Test
  void openRepository() throws Exception {
    final File repoDir = initLocalRepositoryWithOrigin(httpsUrl, "busydoingnothing");
    try (Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      Assertions.assertEquals(httpsUrl, GitRepositoryHandler.getRemoteOriginUrl(repository));
    }
    try (Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      Assertions.assertEquals(httpsUrl, GitRepositoryHandler.getRemoteOriginUrl(repository));
      Assertions.assertNotNull(repository.getBranch());
    }
  }

  @Test()
  void testSshOriginUrlOnLocalRepository() throws Exception {
    final File repoDir = initLocalRepositoryWithOrigin(sshUrl, "ssh-origin");
    try (Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      Assertions.assertEquals(sshUrl, GitRepositoryHandler.getRemoteOriginUrl(repository));
      Assertions.assertNotNull(repository.getBranch());
    }
  }

  @Test()
  void testHttpsOriginUrlOnLocalRepository() throws Exception {
    final File repoDir = initLocalRepositoryWithOrigin(httpsUrl, "https-origin");
    try (Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      Assertions.assertEquals(httpsUrl, GitRepositoryHandler.getRemoteOriginUrl(repository));
      Assertions.assertNotNull(repository.getBranch());
    }
  }

  @Test()
  void testSshConversion() {
    Assertions.assertEquals(
        Map.entry(true, httpsUrl), GitRepositoryHandler.convertSshToHttps(httpsUrl));

    Assertions.assertEquals(
        Map.entry(true, httpsUrl), GitRepositoryHandler.convertSshToHttps(sshUrl));

    // GitHub SSH URL with .git suffix
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/ExplorViz/code-analyzer.git"),
        GitRepositoryHandler.convertSshToHttps("git@github.com:ExplorViz/code-analyzer.git"));

    // GitHub SSH URL without .git suffix
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/ExplorViz/code-analyzer"),
        GitRepositoryHandler.convertSshToHttps("git@github.com:ExplorViz/code-analyzer"));

    // GitHub SSH URL with hyphenated org/repo names
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/open-telemetry/opentelemetry-demo.git"),
        GitRepositoryHandler.convertSshToHttps(
            "git@github.com:open-telemetry/opentelemetry-demo.git"));

    // ssh:// style GitHub URL
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/ExplorViz/code-analyzer.git"),
        GitRepositoryHandler.convertSshToHttps("ssh://git@github.com/ExplorViz/code-analyzer.git"));

    // if the url looks off, assume the user wants it that way
    final String urlUnderTest2 = "abc.xyz";
    Assertions.assertEquals(
        Map.entry(false, urlUnderTest2), GitRepositoryHandler.convertSshToHttps(urlUnderTest2));
  }

  @Test
  void testRemoteLookup() throws Exception {
    final File repoDir = initLocalRepositoryWithOrigin(httpsUrl, "remote-lookup");
    try (Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      Assertions.assertEquals(httpsUrl, GitRepositoryHandler.getRemoteOriginUrl(repository));
    }
  }

  @Test()
  void testGetStringifiedFileInCommit() throws Exception {
    final String expected =
        "package testgit.my.test.pckg;\n"
            + "\n"
            + "public class TestGitClass {\n"
            + "\n"
            + "  private final String testVariable;\n"
            + "\n"
            + "  public TestGitClass(final String testVariable) {\n"
            + "    this.testVariable = testVariable;\n"
            + "  }\n"
            + "\n"
            + "}";

    final File repoDir = new File(tempGitLocation, "content-test");
    final ObjectId commitId;
    try (Git git = Git.init().setDirectory(repoDir).call()) {
      final File sourceFile = new File(repoDir, "TestGitClass.java");
      Files.writeString(sourceFile.toPath(), expected);
      git.add().addFilepattern("TestGitClass.java").call();
      commitId = git.commit().setMessage("add test class").call().getId();
    }

    try (final Repository repository =
        this.gitRepositoryHandler.getGitRepository(
            repoDir.getAbsolutePath(), new RemoteRepositoryObject())) {
      try (RevWalk walk = new RevWalk(repository)) {
        final RevCommit commit = walk.parseCommit(commitId);
        final RevTree tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(tree);
          treeWalk.setRecursive(true);
          while (treeWalk.next()) {
            final String actual =
                GitRepositoryHandler.getContent(treeWalk.getObjectId(0), repository);
            Assertions.assertEquals(
                expected.replace(" ", "").replace("\n", "").replace("\r", ""),
                actual.replace(" ", "").replace("\n", "").replace("\r", ""));
            walk.dispose();
          }
        }
      }
    }
  }

  private File initLocalRepositoryWithOrigin(final String remoteUrl, final String directoryName)
      throws Exception {
    final File repoDir = new File(tempGitLocation, directoryName);
    Files.createDirectories(repoDir.toPath());
    try (Git git = Git.init().setDirectory(repoDir).call()) {
      final StoredConfig config = git.getRepository().getConfig();
      config.setString("remote", "origin", "url", remoteUrl);
      config.save();
      git.commit().setMessage("init").setAllowEmpty(true).call();
    }
    return repoDir;
  }
}
