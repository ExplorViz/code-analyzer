package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.CYCLOMATIC_COMPLEXITY;
import static net.explorviz.code.analysis.metrics.CodeMetrics.NPATH_COMPLEXITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.explorviz.code.analysis.antlr.generated.java.JavaLexer;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.metrics.java.CheckstyleReference.Position;
import net.explorviz.code.proto.ClassData;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The cyclomatic and NPath complexities must be the ones that Checkstyle reports. */
class CheckstyleComparisonTest {

  static List<Path> javaFiles() throws IOException {
    final List<Path> files = new ArrayList<>();
    for (final String root : List.of("src/main/java", "src/test/java", "src/test/resources")) {
      try (Stream<Path> paths = Files.walk(Path.of(root))) {
        paths.filter(path -> path.toString().endsWith(".java")).sorted().forEach(files::add);
      }
    }
    return files;
  }

  @ParameterizedTest
  @MethodSource("javaFiles")
  void everyFunctionMatches(final Path file) throws IOException, CheckstyleException {
    final CheckstyleReference checkstyle = CheckstyleReference.of(file);
    final JavaParser.CompilationUnitContext unit = parse(file);

    final Map<ParserRuleContext, Integer> cyclomatic = CyclomaticComplexity.ofFile(unit).values();
    final Map<ParserRuleContext, BigInteger> npath = NpathComplexity.ofFile(unit).values();

    assertEquals(byLine(checkstyle.cyclomaticComplexities()), byLine(withPositions(cyclomatic)));
    assertEquals(byLine(checkstyle.npathComplexities()), byLine(withPositions(npath)));
    assertEquals(withoutInitializers(cyclomatic), exported(file, CYCLOMATIC_COMPLEXITY));
    assertEquals(withoutInitializers(npath), exported(file, NPATH_COMPLEXITY));
  }

  /** The columns differ, as Checkstyle positions some functions at their return type. */
  private static List<String> byLine(final Map<Position, BigInteger> complexities) {
    return complexities.entrySet().stream()
        .map(entry -> "line " + entry.getKey().line() + ": " + entry.getValue())
        .toList();
  }

  private static JavaParser.CompilationUnitContext parse(final Path file) throws IOException {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromPath(file));
    final JavaParser parser = new JavaParser(new CommonTokenStream(lexer));
    final JavaParser.CompilationUnitContext unit = parser.compilationUnit();
    assertEquals(0, parser.getNumberOfSyntaxErrors());
    return unit;
  }

  private static Map<Position, BigInteger> withPositions(
      final Map<ParserRuleContext, ? extends Number> values) {
    final Map<Position, BigInteger> result = new TreeMap<>();
    values.forEach(
        (declaration, value) -> {
          ParserRuleContext member = declaration;
          while (!isMember(member)) {
            member = member.getParent();
          }
          final Position position =
              new Position(
                  member.getStart().getLine(), member.getStart().getCharPositionInLine() + 1);
          assertFalse(result.containsKey(position));
          result.put(position, new BigInteger(value.toString()));
        });
    return result;
  }

  private static boolean isMember(final ParserRuleContext ctx) {
    return ctx instanceof JavaParser.ClassBodyDeclarationContext
        || ctx instanceof JavaParser.InterfaceBodyDeclarationContext
        || ctx instanceof JavaParser.CompactConstructorDeclarationContext;
  }

  /** Initializer blocks are no functions of the exported data. */
  private static List<Double> withoutInitializers(
      final Map<ParserRuleContext, ? extends Number> values) {
    return values.entrySet().stream()
        .filter(entry -> !(entry.getKey() instanceof JavaParser.ClassBodyDeclarationContext))
        .map(entry -> entry.getValue().doubleValue())
        .sorted()
        .toList();
  }

  private static List<Double> exported(final Path file, final String metric) throws IOException {
    final List<Double> values = new ArrayList<>();
    JavaMetricsTestSupport.parse(Files.readString(file))
        .getClassesList()
        .forEach(classData -> addExported(classData, metric, values));
    values.sort(null);
    return values;
  }

  private static void addExported(
      final ClassData classData, final String metric, final List<Double> values) {
    // The elements of annotations are exported as functions, but have no complexity.
    classData.getFunctionsList().stream()
        .filter(function -> function.containsMetrics(metric))
        .forEach(function -> values.add(function.getMetricsOrThrow(metric)));
    classData.getInnerClassesList().forEach(inner -> addExported(inner, metric, values));
  }
}
