package net.explorviz.code.analysis.metrics;

import java.util.IdentityHashMap;
import java.util.Map;
import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.metrics.cohesion.Lcom4;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowAnalysis;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import net.explorviz.code.analysis.metrics.controlflow.FunctionMetrics;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Attaches code metrics to the functions, classes and the file of a source file. Functions count
 * for the class that they are exported with, not for the classes around it, and once for the file.
 */
public final class MetricsCollector {

  private final ControlFlowGrammar controlFlow;
  private final MemberGrammar members;
  private final Map<ClassDataHandler, ParserRuleContext> classes = new IdentityHashMap<>();
  private final Map<MethodDataHandler, Function> functions = new IdentityHashMap<>();
  private Map<ParserRuleContext, FunctionMetrics> functionMetrics = Map.of();

  /**
   * Creates a collector for a file.
   *
   * @param controlFlow the control flow of the language
   * @param members the members of the language, {@code null} if its types have no methods
   */
  public MetricsCollector(final ControlFlowGrammar controlFlow, final MemberGrammar members) {
    this.controlFlow = controlFlow;
    this.members = members;
  }

  /** Measures the functions of the file before any of them is added. */
  public void enterFile(final ParserRuleContext file) {
    functionMetrics = ControlFlowAnalysis.ofFile(file, controlFlow);
  }

  /** Adds a class that is declared by the given rule. */
  public void addClass(final ClassDataHandler classData, final ParserRuleContext declaration) {
    if (classData != null) {
      classes.put(classData, declaration);
    }
  }

  /**
   * Adds the metrics of a function.
   *
   * @param function the exported function
   * @param owner the class that the function is exported with, {@code null} if there is none
   * @param declaration the rule that declares the function
   */
  public void addFunction(
      final MethodDataHandler function,
      final ClassDataHandler owner,
      final ParserRuleContext declaration) {
    final FunctionMetrics metrics = functionMetrics.get(declaration);
    if (metrics != null) {
      attach(function, owner, metrics);
    }
  }

  /** Adds the metrics of a function that is only declared, i.e. without a body. */
  public void addDeclaredFunction(final MethodDataHandler function, final ClassDataHandler owner) {
    attach(function, owner, FunctionMetrics.WITHOUT_BODY);
  }

  /** Ignores a function that is not exported anymore. */
  public void removeFunction(final MethodDataHandler function) {
    functions.remove(function);
  }

  /** Adds the metrics of the classes and the file after everything else has been added. */
  public void exitFile(final AbstractFileDataHandler file) {
    final FileMetrics fileMetrics = new FileMetrics();
    final Map<ClassDataHandler, ComplexityAggregate> classComplexities = new IdentityHashMap<>();
    if (members != null) {
      classes.keySet().forEach(c -> classComplexities.put(c, new ComplexityAggregate()));
    }
    for (final Function function : functions.values()) {
      final FunctionMetrics metrics = function.metrics();
      fileMetrics.addFunction(
          metrics.cyclomaticComplexity(),
          metrics.npathComplexity().doubleValue(),
          metrics.nestedBlockDepth());
      if (function.owner() != null && classComplexities.containsKey(function.owner())) {
        classComplexities.get(function.owner()).add(metrics.cyclomaticComplexity());
      }
    }
    classComplexities.forEach(
        (classData, complexity) -> addClassMetrics(classData, complexity, fileMetrics));
    fileMetrics.attachTo(file, members != null);
  }

  private void addClassMetrics(
      final ClassDataHandler classData,
      final ComplexityAggregate complexity,
      final FileMetrics fileMetrics) {
    final TypeMembers typeMembers = members.membersOf(classes.get(classData));
    final int lcom4 = typeMembers == null ? 0 : Lcom4.of(typeMembers, members);
    classData.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(complexity.sum()));
    classData.addMetric(
        CodeMetrics.CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(complexity.average()));
    classData.addMetric(CodeMetrics.LCOM4, String.valueOf(lcom4));
    fileMetrics.addClass(lcom4);
  }

  private void attach(
      final MethodDataHandler function,
      final ClassDataHandler owner,
      final FunctionMetrics metrics) {
    functions.put(function, new Function(owner, metrics));
    function.addMetric(
        CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(metrics.cyclomaticComplexity()));
    function.addMetric(
        CodeMetrics.NPATH_COMPLEXITY,
        String.valueOf(Math.min(metrics.npathComplexity().doubleValue(), Double.MAX_VALUE)));
    if (metrics.nestedBlockDepth() > 0) {
      function.addMetric(
          CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(metrics.nestedBlockDepth()));
    }
  }

  private record Function(ClassDataHandler owner, FunctionMetrics metrics) {}
}
