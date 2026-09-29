package net.explorviz.code.analysis.git;

import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Stream;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Testing the repository loader. */
@QuarkusTest
public class DirectoryFinderTest {

  private static final String MAIN_SOURCE_PATH = "/src/main/java";
  private static final String TEST_SOURCE_PATH = "/src/test/java";
  private static final String JAVA = "java";
  private static final String SRC = "src";
  private static final String REGEX_CONTAINS_SLASH = "\\\\+|/+";
  private static final String NOT_FOUND = "Not Found";

  private static File tempLocation;
  private static DirectoryFinder directoryFinder;

  @BeforeEach
  void setup() throws IOException {
    tempLocation = Files.createTempDirectory("explorviz-test").toFile();
    Files.createDirectories(Paths.get(tempLocation.getAbsolutePath(), "src", "main", "java"));
    Files.createDirectories(Paths.get(tempLocation.getAbsolutePath(), "src", "test", "java"));
  }

  @AfterEach
  void tearDown() throws IOException {
    try (Stream<Path> walk = Files.walk(tempLocation.toPath())) {
      walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
  }

  @Test()
  void testRelativePathWithWildcard() {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("**/main/java");
    try {
      List<String> absolutePaths =
          DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
      Assertions.assertEquals(1, absolutePaths.size());
      // clean string from mutiple slashes and bring to system seperator
      final String expected =
          (tempLocation.getAbsolutePath() + MAIN_SOURCE_PATH)
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      Assertions.assertEquals(expected, absolutePaths.get(0));
    } catch (NotFoundException e) {
      Assertions.fail(NOT_FOUND);
    }
  }

  @Test()
  void testLeadingWildcardPath() {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/main/java");
    try {
      List<String> absolutePaths =
          DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
      Assertions.assertEquals(1, absolutePaths.size());
      String s =
          (tempLocation.getAbsolutePath() + "/src/main/java")
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      Assertions.assertEquals(absolutePaths.get(0), s);
    } catch (NotFoundException e) {
      Assertions.fail(NOT_FOUND);
    }
  }

  @Test()
  void testForNoDuplicates() {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/main/java");
    searchPaths.add("**/main/java");
    searchPaths.add("src/main/java");
    try {
      List<String> absolutePaths =
          DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
      Assertions.assertEquals(1, absolutePaths.size());
      String s =
          (tempLocation.getAbsolutePath() + "/src/main/java")
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      Assertions.assertEquals(absolutePaths.get(0), s);
    } catch (NotFoundException e) {
      Assertions.fail(NOT_FOUND);
    }
  }

  @Test()
  void testInfixWildcardPath() {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/*/java");
    try {
      List<String> absolutePaths =
          DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
      Assertions.assertEquals(2, absolutePaths.size());
      String expected1 =
          (tempLocation.getAbsolutePath() + TEST_SOURCE_PATH)
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      String expected2 =
          (tempLocation.getAbsolutePath() + MAIN_SOURCE_PATH)
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      Assertions.assertTrue(absolutePaths.contains(expected1));
      Assertions.assertTrue(absolutePaths.contains(expected2));
    } catch (NotFoundException e) {
      Assertions.fail(NOT_FOUND);
    }
  }

  @Test()
  void testZeroOrMoreDirectoriesWildcardPath() throws IOException {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/**/java");

    Files.createDirectories(
        Paths.get(tempLocation.getAbsolutePath(), SRC, "test", "integration", JAVA));

    try {
      List<String> absolutePaths =
          DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
      Assertions.assertEquals(3, absolutePaths.size());
      String expected1 =
          (tempLocation.getAbsolutePath() + "/src/test/integration/java")
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      String expected2 =
          (tempLocation.getAbsolutePath() + "/src/main/java")
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      String expected3 =
          (tempLocation.getAbsolutePath() + "/src/test/java")
              .replaceAll(REGEX_CONTAINS_SLASH, Matcher.quoteReplacement(File.separator));
      Assertions.assertTrue(absolutePaths.contains(expected1));
      Assertions.assertTrue(absolutePaths.contains(expected2));
      Assertions.assertTrue(absolutePaths.contains(expected3));
    } catch (NotFoundException e) {
      Assertions.fail(NOT_FOUND);
    }
  }

  @Test()
  void testConsecutiveInfixWildcardUnresolvablePath() throws NotFoundException {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/*/*/main/java");
    List<String> result =
        DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
    Assertions.assertEquals(0, result.size());
  }

  @Test()
  void testRegexMatching() throws NotFoundException {
    List<String> searchPaths = new ArrayList<>();
    searchPaths.add("src/main/**");
    List<String> absolutePaths =
        DirectoryFinder.getDirectories(tempLocation.getAbsolutePath(), searchPaths);
    Assertions.assertEquals(1, absolutePaths.size());
  }
}
