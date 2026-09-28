package net.explorviz.code.analysis.parser;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@QuarkusTest
class Wsp31ParsePerfTest {

  private static final Path REPO = Path.of("cloned-repositories/wsp-3-1");

  @Inject
  AntlrTypeScriptParserService tsParser;

  @Inject
  AntlrPythonParserService pythonParser;

  @Inject
  AntlrParserService javaParser;

  @Test
  @EnabledIf("repoPresent")
  void largeFrontendMocksCompleteWithinBudget() throws Exception {
    assertParsesWithinBudget("frontend/src/mock/holiday-presets.ts", 30_000L);
    assertParsesWithinBudget("frontend/src/mock/mock-api.ts", 30_000L);
    assertParsesWithinBudget("frontend/scripts/abnahme.spec.ts", 30_000L);
    assertParsesWithinBudget("frontend/src/pages/masterdata/SubjectsPage.tsx", 30_000L);
    assertParsesWithinBudget("frontend/src/pages/start/StartPage.tsx", 30_000L);
  }

  @Test
  @EnabledIf("repoPresent")
  void largePythonScriptsCompleteWithinBudget() throws Exception {
    assertPythonWithinBudget("scripts/abnahme-substitution.py", 30_000L);
    assertPythonWithinBudget("scripts/abnahme-planning.py", 30_000L);
  }

  @Test
  @EnabledIf("repoPresent")
  void largeJavaSourcesCompleteWithinBudget() throws Exception {
    assertJavaWithinBudget(
        "backend/src/main/java/de/wsp3_1/planning/domain/DraftService.java", 30_000L);
    assertJavaWithinBudget(
        "backend/src/main/java/de/wsp3_1/substitution/domain/WeekResolver.java", 30_000L);
  }

  private void assertParsesWithinBudget(final String relativePath, final long budgetMs)
      throws Exception {
    final Path file = REPO.resolve(relativePath);
    final String content = Files.readString(file);
    final long start = System.nanoTime();
    final var handler = tsParser.parseFileContent(content, relativePath, "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println(relativePath + " TS parse took " + ms + " ms, handler=" + (handler != null));
    Assertions.assertTrue(ms < budgetMs,
        relativePath + " expected under " + budgetMs + " ms, was " + ms + " ms");
  }

  private void assertPythonWithinBudget(final String relativePath, final long budgetMs)
      throws Exception {
    final Path file = REPO.resolve(relativePath);
    final String content = Files.readString(file);
    final long start = System.nanoTime();
    final var handler = pythonParser.parseFileContent(content, relativePath, "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println(relativePath + " Python parse took " + ms + " ms, handler=" + (handler != null));
    Assertions.assertTrue(ms < budgetMs,
        relativePath + " expected under " + budgetMs + " ms, was " + ms + " ms");
  }

  private void assertJavaWithinBudget(final String relativePath, final long budgetMs)
      throws Exception {
    final Path file = REPO.resolve(relativePath);
    final String content = Files.readString(file);
    final long start = System.nanoTime();
    final var handler = javaParser.parseFileContent(content, relativePath, "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println(relativePath + " Java parse took " + ms + " ms, handler=" + (handler != null));
    Assertions.assertTrue(ms < budgetMs,
        relativePath + " expected under " + budgetMs + " ms, was " + ms + " ms");
  }

  static boolean repoPresent() {
    return Files.isDirectory(REPO);
  }
}
