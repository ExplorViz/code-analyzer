package net.explorviz.code.analysis.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class TsxJsxNormalizerTest {

  private static final Path SUBJECTS_PAGE = Path.of(
      "cloned-repositories/wsp-3-1/frontend/src/pages/masterdata/SubjectsPage.tsx");

  @Test
  @EnabledIf("wspRepoPresent")
  void subjectsPageNormalizesQuickly() throws Exception {
    final String source = Files.readString(SUBJECTS_PAGE);
    final long start = System.nanoTime();
    final String normalized = TsxJsxNormalizer.replaceJsxWithNull(source);
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println("SubjectsPage normalize took " + ms + " ms, len " + normalized.length());
    Assertions.assertTrue(ms < 5_000, "Expected normalize under 5s, was " + ms + " ms");
    Assertions.assertFalse(normalized.contains("<PageHeader"), "JSX tags should be replaced");
  }

  static boolean wspRepoPresent() {
    return Files.isRegularFile(SUBJECTS_PAGE);
  }
}
