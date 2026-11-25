package net.explorviz.code.analysis.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Compares the complexities of functions with those of equivalent Java methods. The resources
 * consist of blocks with the Java statements, followed by the statements in the other language.
 */
class ComplexityComparisonTest {

  private static final Path DIRECTORY = Path.of("src/test/resources/metrics");

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
  void isEquivalentToJava(final Example example) {
    final FileData file = example.language().parse(toFile(example));
    assertEquals(
        FunctionComplexity.ofJava(example.java()),
        FunctionComplexity.of(
            file.getFunctionsCount() == 0
                ? file.getClasses(0).getFunctions(0)
                : file.getFunctions(0)));
  }

  @ParameterizedTest
  @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "JAVA")
  void everyLanguageIsCompared(final TestLanguage language) throws IOException {
    assertTrue(examplesOf(language).size() > 10);
  }

  private static List<Example> examplesOf(final TestLanguage language) throws IOException {
    // JavaScript is also TypeScript.
    final String name = language == TestLanguage.TYPESCRIPT ? "javascript" : language.directory();
    final String examples = Files.readString(DIRECTORY.resolve(name).resolve("complexity.txt"));
    return Stream.of(examples.split("(?m)^=== "))
        .filter(block -> !block.isBlank())
        .map(
            block -> {
              final String[] parts = block.split("\n", 2);
              return new Example(language, parts[0], parts[1]);
            })
        .toList();
  }

  private static String toFile(final Example example) {
    final String body = example.statements();
    return switch (example.language()) {
      case C, CPP -> "int f(int a) {\n" + body + "}\n";
      case GO -> "package p\n\nfunc f(a int) int {\n" + body + "}\n";
      case JAVASCRIPT, TYPESCRIPT -> "function f(a) {\n" + body + "}\n";
      case KOTLIN -> "fun f(a: Int): Int {\n" + body + "}\n";
      case PHP -> "<?php\nfunction f($a) {\n" + body + "}\n";
      case PYTHON -> "def f(a):\n" + body.indent(4);
      case RUST -> "fn f(a: bool) {\n" + body + "}\n";
      case SWIFT -> "func f(a: Int) {\n" + body + "}\n";
      case CSHARP -> "class A { int F(int a) {\n" + body + "} }\n";
      case JAVA -> throw new IllegalArgumentException();
    };
  }

  /** Statements in Java and another language. */
  record Example(TestLanguage language, String java, String statements) {

    @Override
    public String toString() {
      return language + ": " + java;
    }
  }
}
