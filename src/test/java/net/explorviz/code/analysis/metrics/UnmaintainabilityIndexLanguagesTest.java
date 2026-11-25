package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the unmaintainability index of all languages but Java. The resources consist of blocks with
 * the Halstead length, the Halstead vocabulary, the cyclomatic complexity and the lines of the
 * first function, followed by the source code.
 */
class UnmaintainabilityIndexLanguagesTest {

  private static final Path DIRECTORY = Path.of("src/test/resources/metrics");
  private static final double DELTA = 1e-9;

  static Stream<Example> examples() throws IOException {
    final List<Example> examples = new ArrayList<>();
    for (final TestLanguage language : TestLanguage.values()) {
      if (language != TestLanguage.JAVA) {
        examples.addAll(examplesOf(language));
      }
    }
    return examples.stream();
  }

  @ParameterizedTest
  @MethodSource("examples")
  void ofFunction(final Example example) {
    final String[] expected = example.expected().split(" ");
    assertEquals(
        indexOf(
            volume(Integer.parseInt(expected[0]), Integer.parseInt(expected[1])),
            Integer.parseInt(expected[2]),
            Integer.parseInt(expected[3])),
        ofFirstFunction(example.language().parse(example.source())),
        DELTA);
  }

  @ParameterizedTest
  @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "JAVA")
  void everyLanguageIsTested(final TestLanguage language) throws IOException {
    assertTrue(examplesOf(language).size() > 1);
  }

  @Test
  void typeArgumentsOfTypeScriptAreBrackets() {
    // function f ( a : Map <> string , number : void { g <> Foo ( a
    assertEquals(
        indexOf(volume(18, 14), 1, 1),
        ofFirstFunction(
            TestLanguage.TYPESCRIPT.parse(
                "function f(a: Map<string, number>): void { g<Foo>(a) }")),
        DELTA);
  }

  /** Optional semicolons are a matter of style. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      value = {
        "JAVASCRIPT # function f(a) { g(a); return a; } # function f(a) { g(a); return a }",
        "TYPESCRIPT # function f(a) { g(a); return a; } # function f(a) { g(a); return a }",
        "KOTLIN     # fun f() { g(); h(); }             # fun f() { g(); h() }",
        "SWIFT      # func f() { g(); h(); }            # func f() { g(); h() }",
      })
  void optionalSemicolonsDoNotCount(
      final TestLanguage language, final String source, final String withoutSemicolon) {
    assertEquals(
        ofFirstFunction(language.parse(source)), ofFirstFunction(language.parse(withoutSemicolon)));
  }

  @Test
  void classesAndFileAverageTheirOwnFunctions() {
    final FileData file =
        TestLanguage.KOTLIN.parse(
            """
            abstract class A {
              abstract fun none(): Int
              fun one(): Int { return 1 }
              fun two() {
                g()
              }
            }
            fun three() { h(1) }
            """);
    final ClassData type = file.getClasses(0);

    // fun one ( : Int { return 1
    final double one = volume(8, 8);
    // fun two ( { g (
    final double two = volume(6, 5);
    // fun three ( { h ( 1
    final double three = volume(7, 6);

    assertFalse(type.getFunctions(0).containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
    assertIndex(indexOf(one, 1, 1), type.getFunctions(1).getMetricsMap());
    assertIndex(indexOf(two, 1, 3), type.getFunctions(2).getMetricsMap());
    assertIndex(indexOf((one + two) / 2, 1, 2), type.getMetricsMap());
    assertIndex(indexOf((one + two + three) / 3, 1, 5 / 3.0), file.getMetricsMap());
  }

  @Test
  void namedFunctionsInsideOfFunctionsCountOnTheirOwn() {
    final FileData file =
        TestLanguage.JAVASCRIPT.parse("function f() { function g() { return 1 + 2 + 3 } }");
    // function f ( {
    assertIndex(indexOf(volume(4, 4), 1, 1), file.getFunctions(0).getMetricsMap());
  }

  @Test
  void declaredFunctionsOfCppAreNotMeasured() {
    final ClassData type =
        TestLanguage.CPP.parse("class A { int f(); int g() = delete; };").getClasses(0);
    assertFalse(type.getFunctions(0).containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
    assertFalse(type.containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
  }

  @Test
  void typesAreNotMeasuredInC() {
    final FileData file = TestLanguage.C.parse("struct S { int x; };");
    assertFalse(file.getClasses(0).containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
    assertFalse(file.containsMetrics(CodeMetrics.UNMAINTAINABILITY_INDEX));
  }

  private static List<Example> examplesOf(final TestLanguage language) throws IOException {
    final Path file = DIRECTORY.resolve(language.directory()).resolve("halstead.txt");
    return Stream.of(Files.readString(file).split("(?m)^=== "))
        .filter(block -> !block.isBlank())
        .map(block -> block.split("\n", 2))
        .map(parts -> new Example(language, parts[0], parts[1]))
        .toList();
  }

  private static double ofFirstFunction(final FileData file) {
    return (file.getFunctionsCount() == 0
            ? file.getClasses(0).getFunctions(0)
            : file.getFunctions(0))
        .getMetricsOrThrow(CodeMetrics.UNMAINTAINABILITY_INDEX);
  }

  private static double indexOf(final double volume, final double complexity, final double lines) {
    final double maintainability =
        171 - 5.2 * Math.log(volume) - 0.23 * complexity - 16.2 * Math.log(lines);
    return 100 - maintainability * 100 / 171;
  }

  private static double volume(final int length, final int vocabulary) {
    return length * Math.log(vocabulary) / Math.log(2);
  }

  private static void assertIndex(final double expected, final Map<String, Double> metrics) {
    assertEquals(expected, metrics.get(CodeMetrics.UNMAINTAINABILITY_INDEX), DELTA);
  }

  /** Source code in the given language. */
  record Example(TestLanguage language, String expected, String source) {}
}
