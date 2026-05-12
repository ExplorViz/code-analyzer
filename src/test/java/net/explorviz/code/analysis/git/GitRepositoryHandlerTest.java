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
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testing the repository loader.
 */
@QuarkusTest
public class GitRepositoryHandlerTest {

  private static final String MASTER = "master";
  private final String sshUrl = "git@gitlab.com:0xhexdec/busydoingnothing.git";
  private final String httpsUrl = "https://gitlab.com/0xhexdec/busydoingnothing.git";

  @Inject
  GitRepositoryHandler gitRepositoryHandler; // NOCS
  private File tempGitLocation;

  @BeforeEach
  void setup() throws IOException {
    tempGitLocation = Files.createTempDirectory("explorviz-test").toFile();
  }

  @AfterEach
  void tearDown() {
    try (Stream<Path> walk = Files.walk(tempGitLocation.toPath())) {
      walk.sorted(Comparator.reverseOrder()).map(Path::toFile)
          .forEach(File::delete);
    } catch (IOException exception) {
      System.err.println("Folder not deletable");
    }
  }

  @Test()
  void testInvalidRemote() {
    String url = "%%%%";

    Assertions.assertThrows(InvalidRemoteException.class, () -> {
      this.gitRepositoryHandler.getGitRepository("",
          new RemoteRepositoryObject(url, tempGitLocation.getAbsolutePath(), MASTER));
    });
  }

  @Test
  void testInvalidParameters() {
    Assertions.assertThrows(InvalidRemoteException.class, () -> {
      this.gitRepositoryHandler.getGitRepository("", new RemoteRepositoryObject("", "", MASTER));
    });
  }

  @Test()
  void testMalformedRemote() {
    String url = "https://gitlab.com/0xhexdec/";
    Assertions.assertThrows(MalformedURLException.class, () -> {
      this.gitRepositoryHandler.getGitRepository("",
          new RemoteRepositoryObject(url, tempGitLocation.getAbsolutePath(), MASTER));
    });
  }

  @Test
  void testFileInsteadDirectory() throws IOException {
    File file = new File(tempGitLocation.getAbsolutePath() + "/file");
    Assertions.assertTrue(file.createNewFile());
    Assertions.assertThrows(NotDirectoryException.class, () -> {
      this.gitRepositoryHandler.getGitRepository(file.getAbsolutePath(),
          new RemoteRepositoryObject("", "", MASTER));
    });
  }

  @Test
  void openRepository() throws GitAPIException, IOException {
    // downloading the repository first
    try (Repository repository = this.gitRepositoryHandler.getGitRepository("",
        new RemoteRepositoryObject(httpsUrl, tempGitLocation.getAbsolutePath(), MASTER))) {
      // call is here to satisfy checkstyle by not having empty try block
      System.out.println(GitRepositoryHandler.getRemoteOriginUrl(repository));
    } catch (Exception e) {
      Assertions.fail();
    }
    // checking the same folder and reopen the repository
    try (Repository repository = this.gitRepositoryHandler.getGitRepository(
        tempGitLocation.getAbsolutePath() + "/busydoingnothing", new RemoteRepositoryObject())) {
      Assertions.assertEquals(GitRepositoryHandler.getRemoteOriginUrl(repository), httpsUrl);
    }

  }

  @Test()
  void testSsh() {
    try (Repository repository = this.gitRepositoryHandler.getGitRepository("",
        new RemoteRepositoryObject(sshUrl,
            tempGitLocation.getAbsolutePath(), MASTER))) {
      // call is here to satisfy checkstyle by not having empty try block
      repository.getBranch();
    } catch (Exception e) {
      Assertions.fail();
    }
  }

  @Test()
  void testHttps() {
    try (Repository repository = this.gitRepositoryHandler.getGitRepository("",
        new RemoteRepositoryObject(
            httpsUrl, tempGitLocation.getAbsolutePath(), MASTER))) {
      // call is here to satisfy checkstyle by not having empty try block
      repository.getBranch();
    } catch (Exception e) {
      Assertions.fail();
    }
  }

  @Test()
  void testSshConversion() {
    Assertions.assertEquals(Map.entry(true, httpsUrl),
        GitRepositoryHandler.convertSshToHttps(httpsUrl));

    Assertions.assertEquals(Map.entry(true, httpsUrl),
        GitRepositoryHandler.convertSshToHttps(sshUrl));

    // GitHub SSH URL with .git suffix
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/ExplorViz/code-agent.git"),
        GitRepositoryHandler.convertSshToHttps("git@github.com:ExplorViz/code-agent.git"));

    // GitHub SSH URL without .git suffix
    Assertions.assertEquals(
        Map.entry(true, "https://github.com/ExplorViz/code-agent"),
        GitRepositoryHandler.convertSshToHttps("git@github.com:ExplorViz/code-agent"));

    // if the url looks off, assume the user wants it that way
    final String urlUnderTest2 = "abc.xyz";
    Assertions.assertEquals(Map.entry(false, urlUnderTest2),
        GitRepositoryHandler.convertSshToHttps(urlUnderTest2));

  }

  @Test
  void testRemoteLookup() throws GitAPIException, IOException {
    try (Repository repository = this.gitRepositoryHandler.getGitRepository("",
        new RemoteRepositoryObject(httpsUrl,
            tempGitLocation.getAbsolutePath(), MASTER))) {
      Assertions.assertEquals(GitRepositoryHandler.getRemoteOriginUrl(repository), httpsUrl);
    }
  }

  @Test()
  void testGetStringifiedFileInCommit()
      throws GitAPIException, IOException {

    try (final Repository repository = this.gitRepositoryHandler.getGitRepository("",
        new RemoteRepositoryObject(
            "https://github.com/Alexander-Krause-Glau/Test-JGit-Code.git",
            tempGitLocation.getAbsolutePath(), MASTER))) {

      try (RevWalk walk = new RevWalk(repository)) {
        final ObjectId id = repository.resolve("8ee1f25");

        final RevCommit commit = walk.parseCommit(id);

        final RevTree tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(tree);
          treeWalk.setRecursive(true);
          while (treeWalk.next()) {
            final String actual = GitRepositoryHandler.getContent(treeWalk.getObjectId(0),
                repository);
            final String expected = "package testgit.my.test.pckg;\n" + "\n" + "public class TestGitClass {\n" + "\n"
                + "  private final String testVariable;\n" + "\n"
                + "  public TestGitClass(final String testVariable) {\n"
                + "    this.testVariable = testVariable;\n" + "  }\n" + "\n" + "}";

            Assertions.assertEquals(expected.replace(" ", "").replace("\n", "").replace("\r", ""),
                actual.replace(" ", "").replace("\n", "").replace("\r", ""));

            walk.dispose();

          }
        }
      }
    }

  }

}
