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
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Attaches code metrics to the functions, classes and the file of a source file. Functions count
 * for the class that they are exported with, not for the classes around it, and once for the file.
 */
public final class MetricsCollector {

  private final ControlFlowGrammar controlFlow;
  private final TokenGrammar tokens;
  private final MemberGrammar members;
  private final Map<ClassDataHandler, ParserRuleContext> classes = new IdentityHashMap<>();
  private final Map<MethodDataHandler, Function> functions = new IdentityHashMap<>();
  private Map<ParserRuleContext, FunctionMetrics> functionMetrics = Map.of();

  /**
   * Creates a collector for a file.
   *
   * @param controlFlow the control flow of the language
   * @param tokens the operators and operands of the language
   * @param members the members of the language, {@code null} if its types have no methods
   */
  public MetricsCollector(
      final ControlFlowGrammar controlFlow,
      final TokenGrammar tokens,
      final MemberGrammar members) {
    this.controlFlow = controlFlow;
    this.tokens = tokens;
    this.members = members;
  }

  /** Measures the functions of the file before any of them is added. */
  public void enterFile(final ParserRuleContext file) {
    functionMetrics = ControlFlowAnalysis.ofFile(file, controlFlow, tokens);
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
    final Map<ClassDataHandler, ClassMetrics> classMetrics = new IdentityHashMap<>();
    if (members != null) {
      classes.keySet().forEach(c -> classMetrics.put(c, new ClassMetrics()));
    }
    for (final Function function : functions.values()) {
      final FunctionMetrics metrics = function.metrics();
      fileMetrics.addFunction(
          metrics.cyclomaticComplexity(), metrics.npathComplexity().doubleValue());
      if (metrics.hasBody()) {
        fileMetrics.addCode(
            metrics.nestedBlockDepth(),
            metrics.halsteadVolume(),
            metrics.cyclomaticComplexity(),
            metrics.linesOfCode());
      }
      if (function.owner() != null && classMetrics.containsKey(function.owner())) {
        classMetrics.get(function.owner()).add(metrics);
      }
    }
    classMetrics.forEach((classData, metrics) -> addClassMetrics(classData, metrics, fileMetrics));
    fileMetrics.attachTo(file);
  }

  private void addClassMetrics(
      final ClassDataHandler classData, final ClassMetrics metrics, final FileMetrics fileMetrics) {
    final TypeMembers typeMembers = members.membersOf(classes.get(classData));
    final int lcom4 = typeMembers == null ? 0 : Lcom4.of(typeMembers, members);
    metrics.complexity.attachTo(classData::addMetric);
    if (lcom4 > 0) {
      classData.addMetric(CodeMetrics.LCOM4, String.valueOf(lcom4));
      fileMetrics.addClass(lcom4);
    }
    metrics.unmaintainability.attachTo(classData::addMetric);
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
    if (metrics.hasBody()) {
      function.addMetric(
          CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(metrics.nestedBlockDepth()));
      final UnmaintainabilityIndex unmaintainability = new UnmaintainabilityIndex();
      unmaintainability.add(
          metrics.halsteadVolume(), metrics.cyclomaticComplexity(), metrics.linesOfCode());
      unmaintainability.attachTo(function::addMetric);
    }
  }

  private static final class ClassMetrics {
    private final ComplexityAggregate complexity = new ComplexityAggregate();
    private final UnmaintainabilityIndex unmaintainability = new UnmaintainabilityIndex();

    private void add(final FunctionMetrics metrics) {
      complexity.add(metrics.cyclomaticComplexity());
      if (metrics.hasBody()) {
        unmaintainability.add(
            metrics.halsteadVolume(), metrics.cyclomaticComplexity(), metrics.linesOfCode());
      }
    }
  }

  private record Function(ClassDataHandler owner, FunctionMetrics metrics) {}
}
