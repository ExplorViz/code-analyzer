package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.CYCLOMATIC_COMPLEXITY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.java.JavaLexer;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserBaseListener;
import net.explorviz.code.analysis.metrics.java.lcom4.Lcom4;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.Test;

class JavaMetricsRobustnessTest {

  private static final Path FIXTURE =
      Path.of("src/test/resources/regression/java/ParserTestInput.java");

  private static final int STEP = 29;

  /** Error recovery yields trees with missing children, which must not fail the analysis. */
  @Test
  void incompleteTreesAreMeasured() throws IOException {
    final String source = Files.readString(FIXTURE);
    for (int end = 0; end < source.length(); end += STEP) {
      final int hole = end / 2;
      final String truncated = source.substring(0, end);
      final String withHole = source.substring(0, hole) + source.substring(hole + STEP);
      assertDoesNotThrow(() -> measure(truncated), truncated);
      assertDoesNotThrow(() -> measure(withHole), withHole);
    }
  }

  @Test
  void longExpressionsAreMeasured() {
    assertEquals(
        3000.0,
        JavaMetricsTestSupport.ofBody(
            CYCLOMATIC_COMPLEXITY, "return a" + " && a".repeat(2999) + ";"));
  }

  private static void measure(final String source) {
    final JavaLexer lexer = new JavaLexer(CharStreams.fromString(source));
    lexer.removeErrorListeners();
    final JavaParser parser = new JavaParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    ParseTreeWalker.DEFAULT.walk(new MeasureListener(), parser.compilationUnit());
  }

  private static final class MeasureListener extends JavaParserBaseListener {
    @Override
    public void enterCompilationUnit(final JavaParser.CompilationUnitContext ctx) {
      CyclomaticComplexity.ofFile(ctx);
      NpathComplexity.ofFile(ctx);
    }

    @Override
    public void enterBlock(final JavaParser.BlockContext ctx) {
      NestedBlockDepth.of(ctx);
    }

    @Override
    public void enterClassDeclaration(final JavaParser.ClassDeclarationContext ctx) {
      Lcom4.of(ctx);
    }

    @Override
    public void enterInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext ctx) {
      Lcom4.of(ctx);
    }

    @Override
    public void enterEnumDeclaration(final JavaParser.EnumDeclarationContext ctx) {
      Lcom4.of(ctx);
    }

    @Override
    public void enterRecordDeclaration(final JavaParser.RecordDeclarationContext ctx) {
      Lcom4.of(ctx);
    }

    @Override
    public void enterClassCreatorRest(final JavaParser.ClassCreatorRestContext ctx) {
      Lcom4.of(ctx);
    }
  }
}
