package net.explorviz.code.analysis.metrics.java;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.metrics.CodeMetrics;
import net.explorviz.code.analysis.metrics.ComplexityAggregate;
import net.explorviz.code.analysis.metrics.java.lcom4.Lcom4;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Attaches code metrics to the functions, classes and the file of a Java source file. Functions
 * count for the class that declares them, not for the classes around it, and once for the file.
 */
public final class JavaMetricsCollector {

  private final ComplexityAggregate fileComplexity = new ComplexityAggregate();
  private final Deque<ComplexityAggregate> classComplexities = new ArrayDeque<>();
  private CyclomaticComplexity cyclomaticComplexities;
  private NpathComplexity npathComplexities;

  /** Measures the functions of the file before any of them is added. */
  public void enterFile(final JavaParser.CompilationUnitContext file) {
    cyclomaticComplexities = CyclomaticComplexity.ofFile(file);
    npathComplexities = NpathComplexity.ofFile(file);
  }

  public void enterClass() {
    classComplexities.push(new ComplexityAggregate());
  }

  /** Adds the metrics of the class that was entered last. */
  public void exitClass(final ClassDataHandler classData, final ParserRuleContext type) {
    final ComplexityAggregate complexity = classComplexities.pop();
    classData.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(complexity.sum()));
    classData.addMetric(
        CodeMetrics.CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(complexity.average()));
    classData.addMetric(CodeMetrics.LCOM4, String.valueOf(Lcom4.of(type)));
  }

  /** Adds the metrics of a method. */
  public void addMethod(
      final MethodDataHandler method,
      final ParserRuleContext declaration,
      final JavaParser.MethodBodyContext methodBody) {
    addFunction(method, declaration, methodBody == null ? null : methodBody.block());
  }

  /** Adds the metrics of a constructor. */
  public void addConstructor(
      final MethodDataHandler constructor,
      final ParserRuleContext declaration,
      final JavaParser.BlockContext body) {
    addFunction(constructor, declaration, body);
  }

  /** Adds the metrics of the file after all classes have been left. */
  public void exitFile(final JavaFileDataHandler file) {
    file.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(fileComplexity.sum()));
    file.addMetric(
        CodeMetrics.CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(fileComplexity.average()));
  }

  private void addFunction(
      final MethodDataHandler function,
      final ParserRuleContext declaration,
      final JavaParser.BlockContext body) {
    if (body != null) {
      function.addMetric(CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(NestedBlockDepth.of(body)));
    }
    final BigInteger npath = npathComplexities.values().get(declaration);
    final Integer complexity = cyclomaticComplexities.values().get(declaration);
    if (npath == null || complexity == null) {
      return;
    }
    function.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(complexity));
    function.addMetric(
        CodeMetrics.NPATH_COMPLEXITY,
        String.valueOf(Math.min(npath.doubleValue(), Double.MAX_VALUE)));
    fileComplexity.add(complexity);
    if (!classComplexities.isEmpty()) {
      classComplexities.peek().add(complexity);
    }
  }
}
