package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Java is covered by its own test. */
class MetricsRobustnessTest {

  private static final Path DIRECTORY = Path.of("src/test/resources/regression");
  private static final int STEP = 211;

  /** Error recovery yields trees with missing children, which must not fail the analysis. */
  @ParameterizedTest
  @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "JAVA")
  void incompleteTreesAreMeasured(final TestLanguage language) throws IOException {
    final String source = fixture(language);
    for (int end = STEP; end < source.length(); end += STEP) {
      final int hole = end / 2;
      assertMeasured(language, source.substring(0, end));
      assertMeasured(language, source.substring(0, hole) + source.substring(hole + STEP));
    }
  }

  @ParameterizedTest
  @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "JAVA")
  void allFunctionsAreMeasured(final TestLanguage language) throws IOException {
    final FileData file = language.parse(fixture(language));
    file.getFunctionsList().forEach(MetricsRobustnessTest::assertMeasured);
    file.getClassesList().forEach(type -> assertMeasured(language, type));
    final double complexity =
        file.getFunctionsList().stream().mapToDouble(MetricsRobustnessTest::complexityOf).sum()
            + file.getClassesList().stream().mapToDouble(MetricsRobustnessTest::complexityOf).sum();
    assertEquals(complexity, file.getMetricsOrThrow(CodeMetrics.CYCLOMATIC_COMPLEXITY));
  }

  private static String fixture(final TestLanguage language) throws IOException {
    return Files.readString(
        DIRECTORY.resolve(language.directory()).resolve("ParserTestInput." + language.extension()));
  }

  private static void assertMeasured(final TestLanguage language, final String source) {
    final FileData file = language.parse(source);
    Assertions.assertTrue(file.containsMetrics(CodeMetrics.CYCLOMATIC_COMPLEXITY), source);
  }

  private static void assertMeasured(final FunctionData function) {
    assertTrue(function.containsMetrics(CodeMetrics.CYCLOMATIC_COMPLEXITY), function.getName());
    assertTrue(function.containsMetrics(CodeMetrics.NPATH_COMPLEXITY), function.getName());
  }

  private static void assertMeasured(final TestLanguage language, final ClassData type) {
    type.getFunctionsList().forEach(MetricsRobustnessTest::assertMeasured);
    type.getInnerClassesList().forEach(inner -> assertMeasured(language, inner));
    if (language != TestLanguage.C) {
      assertTrue(type.containsMetrics(CodeMetrics.LCOM4), type.getName());
      assertEquals(
          type.getFunctionsList().stream()
              .mapToDouble(f -> f.getMetricsOrThrow(CodeMetrics.CYCLOMATIC_COMPLEXITY))
              .sum(),
          type.getMetricsOrThrow(CodeMetrics.CYCLOMATIC_COMPLEXITY),
          type.getName());
    }
  }

  private static double complexityOf(final FunctionData function) {
    return function.getMetricsOrThrow(CodeMetrics.CYCLOMATIC_COMPLEXITY);
  }

  private static double complexityOf(final ClassData type) {
    return type.getFunctionsList().stream().mapToDouble(MetricsRobustnessTest::complexityOf).sum()
        + type.getInnerClassesList().stream()
            .mapToDouble(MetricsRobustnessTest::complexityOf)
            .sum();
  }
}
