package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FileMetricsTest {

  /** Two unrelated methods in the first class, nested and branching code in the second one. */
  private static final Map<TestLanguage, String> SOURCES =
      Map.of(
          TestLanguage.JAVA,
          """
          class A { int x; int y; int a() { return x; } int b() { return y; } }
          class B { int x; void c(int i) { if (i > 0) { while (i > 1) { i--; } } } int d() { return x; } }
          """,
          TestLanguage.TYPESCRIPT,
          """
          class A { x = 1; y = 2; a() { return this.x; } b() { return this.y; } }
          class B { c(i) { if (i > 0) { while (i > 1) { i--; } } } }
          """,
          TestLanguage.KOTLIN,
          """
          class A { val x = 1; val y = 2; fun a(): Int { return x } fun b(): Int { return y } }
          class B { fun c(i: Int) { if (i > 0) { while (i > 1) { f() } } } }
          """);

  @ParameterizedTest
  @EnumSource(names = {"JAVA", "TYPESCRIPT", "KOTLIN"})
  void fileHasTheMaximumOfItsFunctionsAndClasses(final TestLanguage language) {
    final FileData file = language.parse(SOURCES.get(language));

    assertEquals(3.0, file.getMetricsOrThrow(CodeMetrics.NESTED_BLOCK_DEPTH));
    assertEquals(3.0, file.getMetricsOrThrow(CodeMetrics.NPATH_COMPLEXITY));
    assertEquals(2.0, file.getMetricsOrThrow(CodeMetrics.LCOM4));
  }

  @Test
  void npathComplexityOfFilesIsLimited() {
    final String source = "class A { void a() { " + "if (b) { f(); } ".repeat(2000) + "} }";
    final FileData file = TestLanguage.JAVA.parse(source);

    assertEquals(
        Double.MAX_VALUE,
        file.getClasses(0).getFunctions(0).getMetricsOrThrow(CodeMetrics.NPATH_COMPLEXITY));
    assertEquals(
        FileMetrics.MAX_NPATH_COMPLEXITY, file.getMetricsOrThrow(CodeMetrics.NPATH_COMPLEXITY));
  }

  @Test
  void filesWithoutCodeHaveNoComplexity() {
    final FileData file = TestLanguage.JAVA.parse("interface A { }");

    assertEquals(0.0, file.getMetricsOrThrow(CodeMetrics.NESTED_BLOCK_DEPTH));
    assertEquals(0.0, file.getMetricsOrThrow(CodeMetrics.NPATH_COMPLEXITY));
    assertEquals(0.0, file.getMetricsOrThrow(CodeMetrics.LCOM4));
  }

  @Test
  void onlyJavaHasAnUnmaintainabilityIndex() {
    final FileData file = TestLanguage.C.parse("int f(void) { return 1; }");

    assertFalse(file.containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
    assertFalse(file.containsMetrics(CodeMetrics.LCOM4));
    assertEquals(1.0, file.getMetricsOrThrow(CodeMetrics.NESTED_BLOCK_DEPTH));
  }
}
