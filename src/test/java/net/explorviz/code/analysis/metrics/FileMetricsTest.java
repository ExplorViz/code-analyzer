package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      value = {
        "C          # struct S { int x; };",
        "CPP        # class A { int x; };",
        "CSHARP     # class A { int x; }",
        "GO         # package p; type A struct { x int }",
        "JAVA       # interface A { int X = 1; }",
        "JAVASCRIPT # class A { x = 1; }",
        "KOTLIN     # class A { val x = 1 }",
        "PHP        # <?php class A { public $x; }",
        "PYTHON     # x = 1",
        "RUST       # struct A { x: i32 }",
        "SWIFT      # class A { var x = 1 }",
        "TYPESCRIPT # const f = [1].map(x => x + 1);",
      })
  void metricsDoNotExistWithoutFunctions(final TestLanguage language, final String source) {
    final FileData file = language.parse(source);

    assertEquals(Set.of(), codeMetricsOf(file.getMetricsMap()));
    file.getClassesList()
        .forEach(type -> assertEquals(Set.of(), codeMetricsOf(type.getMetricsMap())));
    // The other metrics are still known, even if their value is zero.
    assertEquals(0.0, file.getMetricsOrThrow("functionCount"));
    assertTrue(file.containsMetrics("sloc"));
  }

  @Test
  void metricsOfCodeDoNotExistWithoutCode() {
    final FileData file = TestLanguage.JAVA.parse("interface A { int a(); }");
    final Set<String> expected =
        Set.of(
            CodeMetrics.CYCLOMATIC_COMPLEXITY,
            CodeMetrics.AVERAGE_CYCLOMATIC_COMPLEXITY,
            CodeMetrics.NPATH_COMPLEXITY);

    assertEquals(expected, codeMetricsOf(file.getMetricsMap()));
  }

  @Test
  void cohesionDoesNotExistWithoutMethods() {
    final FileData file = TestLanguage.PYTHON.parse("def f():\n  return 1\n");

    assertFalse(file.containsMetrics(CodeMetrics.LCOM4));
    assertTrue(file.containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
  }

  private static Set<String> codeMetricsOf(final Map<String, Double> metrics) {
    final Set<String> names = new HashSet<>(metrics.keySet());
    names.retainAll(
        Set.of(
            CodeMetrics.CYCLOMATIC_COMPLEXITY,
            CodeMetrics.AVERAGE_CYCLOMATIC_COMPLEXITY,
            CodeMetrics.NESTED_BLOCK_DEPTH,
            CodeMetrics.NPATH_COMPLEXITY,
            CodeMetrics.LCOM4,
            CodeMetrics.UNMAINTAINABILITY_INDEX));
    return names;
  }

  @Test
  void filesOfCAreMeasuredWithoutCohesion() {
    final FileData file = TestLanguage.C.parse("int f(void) { return 1; }");

    assertTrue(file.containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
    assertFalse(file.containsMetrics(CodeMetrics.LCOM4));
    assertEquals(1.0, file.getMetricsOrThrow(CodeMetrics.NESTED_BLOCK_DEPTH));
  }
}
