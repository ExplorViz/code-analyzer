package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.explorviz.code.proto.ClassData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests LCOM4. The resources consist of blocks with the expected value of the first class, "none"
 * if it has no value, followed by the source code.
 */
class CohesionTest {

  private static final Path DIRECTORY = Path.of("src/test/resources/metrics");

  static Stream<Example> examples() throws IOException {
    final List<Example> examples = new ArrayList<>();
    for (final TestLanguage language : TestLanguage.values()) {
      final Path file = DIRECTORY.resolve(language.directory()).resolve("lcom4.txt");
      if (Files.exists(file)) {
        Stream.of(Files.readString(file).split("(?m)^=== "))
            .filter(block -> !block.isBlank())
            .map(block -> block.split("\n", 2))
            .forEach(
                parts ->
                    examples.add(
                        new Example(
                            language,
                            "none".equals(parts[0]) ? null : Double.valueOf(parts[0]),
                            parts[1].strip())));
      }
    }
    return examples.stream();
  }

  @ParameterizedTest
  @MethodSource("examples")
  void ofClass(final Example example) {
    assertEquals(
        example.expected(),
        example
            .language()
            .parse(example.source())
            .getClasses(0)
            .getMetricsMap()
            .get(CodeMetrics.LCOM4));
  }

  @Test
  void typesWithoutMethodsAreNotMeasuredInC() {
    final ClassData struct = TestLanguage.C.parse("struct S { int x; };").getClasses(0);
    assertFalse(struct.containsMetrics(CodeMetrics.LCOM4));
    assertFalse(struct.containsMetrics(CodeMetrics.CYCLOMATIC_COMPLEXITY));
  }

  /** Source code in the given language. */
  record Example(TestLanguage language, Double expected, String source) {}
}
