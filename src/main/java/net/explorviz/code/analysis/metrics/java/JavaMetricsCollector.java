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
import net.explorviz.code.analysis.metrics.FileMetrics;
import net.explorviz.code.analysis.metrics.UnmaintainabilityIndex;
import net.explorviz.code.analysis.metrics.java.lcom4.Lcom4;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Attaches code metrics to the functions, classes and the file of a Java source file. Functions
 * count for the class that declares them, not for the classes around it, and once for the file.
 */
public final class JavaMetricsCollector {

  private final FileMetrics fileMetrics = new FileMetrics();
  private final UnmaintainabilityIndex fileUnmaintainability = new UnmaintainabilityIndex();
  private final Deque<ClassMetrics> classes = new ArrayDeque<>();
  private CyclomaticComplexity cyclomaticComplexities;
  private NpathComplexity npathComplexities;

  /** Measures the functions of the file before any of them is added. */
  public void enterFile(final JavaParser.CompilationUnitContext file) {
    cyclomaticComplexities = CyclomaticComplexity.ofFile(file);
    npathComplexities = NpathComplexity.ofFile(file);
  }

  public void enterClass() {
    classes.push(new ClassMetrics(new ComplexityAggregate(), new UnmaintainabilityIndex()));
  }

  /** Adds the metrics of the class that was entered last. */
  public void exitClass(final ClassDataHandler classData, final ParserRuleContext type) {
    final ClassMetrics metrics = classes.pop();
    final ComplexityAggregate complexity = metrics.complexity();
    final int lcom4 = Lcom4.of(type);
    classData.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(complexity.sum()));
    classData.addMetric(
        CodeMetrics.CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(complexity.average()));
    classData.addMetric(CodeMetrics.LCOM4, String.valueOf(lcom4));
    classData.addMetric(
        CodeMetrics.UNMAINTAINABILITY_INDEX, String.valueOf(metrics.unmaintainability().value()));
    fileMetrics.addClass(lcom4);
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
    fileMetrics.attachTo(file, true);
    file.addMetric(
        CodeMetrics.UNMAINTAINABILITY_INDEX, String.valueOf(fileUnmaintainability.value()));
  }

  private void addFunction(
      final MethodDataHandler function,
      final ParserRuleContext declaration,
      final JavaParser.BlockContext body) {
    final int blockDepth = body == null ? 0 : NestedBlockDepth.of(body);
    if (body != null) {
      function.addMetric(CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(blockDepth));
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
    fileMetrics.addFunction(complexity, npath.doubleValue(), blockDepth);
    if (!classes.isEmpty()) {
      classes.peek().complexity().add(complexity);
    }
    if (body != null) {
      addUnmaintainability(function, declaration, complexity);
    }
  }

  private void addUnmaintainability(
      final MethodDataHandler function, final ParserRuleContext declaration, final int complexity) {
    final HalsteadVolume volume = HalsteadVolume.of(declaration);
    final UnmaintainabilityIndex unmaintainability = new UnmaintainabilityIndex();
    unmaintainability.add(volume.value(), complexity, volume.lines());
    function.addMetric(
        CodeMetrics.UNMAINTAINABILITY_INDEX, String.valueOf(unmaintainability.value()));
    fileUnmaintainability.add(volume.value(), complexity, volume.lines());
    if (!classes.isEmpty()) {
      classes.peek().unmaintainability().add(volume.value(), complexity, volume.lines());
    }
  }

  private record ClassMetrics(
      ComplexityAggregate complexity, UnmaintainabilityIndex unmaintainability) {}
}
