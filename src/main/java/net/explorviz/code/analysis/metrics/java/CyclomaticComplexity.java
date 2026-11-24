package net.explorviz.code.analysis.metrics.java;

import java.util.ArrayDeque;
import java.util.Deque;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Cyclomatic complexity (McCabe, 1976) of functions, with the decision points of Checkstyle's
 * {@code CyclomaticComplexity}.
 *
 * <p>The complexity is one plus the number of decision points: {@code if}, {@code for}, {@code
 * while}, {@code do}, {@code catch}, the ternary operator, {@code &&}, {@code ||}, {@code case} and
 * {@code when}.
 */
public final class CyclomaticComplexity extends FunctionListener<Integer> {

  /** Complexities of the functions around the current position, innermost first. */
  private final Deque<Integer> complexities = new ArrayDeque<>();

  private CyclomaticComplexity() {
    super();
  }

  /** Measures all functions of the file. */
  public static CyclomaticComplexity ofFile(final ParserRuleContext file) {
    final CyclomaticComplexity listener = new CyclomaticComplexity();
    JavaTreeWalker.walk(listener, file);
    return listener;
  }

  @Override
  /* package */ void enterFunction() {
    complexities.push(1);
  }

  @Override
  /* package */ Integer exitFunction() {
    return complexities.pop();
  }

  @Override
  public void enterStatement(final JavaParser.StatementContext ctx) {
    // WHILE covers while and do-while loops.
    addDecisionPointIf(ctx.IF() != null || ctx.FOR() != null || ctx.WHILE() != null);
  }

  @Override
  public void enterCatchClause(final JavaParser.CatchClauseContext ctx) {
    addDecisionPointIf(true);
  }

  @Override
  public void enterTernaryExpression(final JavaParser.TernaryExpressionContext ctx) {
    addDecisionPointIf(true);
  }

  @Override
  public void enterBinaryOperatorExpression(final JavaParser.BinaryOperatorExpressionContext ctx) {
    addDecisionPointIf(ctx.AND() != null || ctx.OR() != null);
  }

  @Override
  public void enterSwitchLabel(final JavaParser.SwitchLabelContext ctx) {
    addDecisionPointIf(ctx.CASE() != null);
  }

  @Override
  public void enterSwitchLabeledRule(final JavaParser.SwitchLabeledRuleContext ctx) {
    addDecisionPointIf(ctx.CASE() != null);
  }

  @Override
  public void enterGuard(final JavaParser.GuardContext ctx) {
    addDecisionPointIf(true);
  }

  private void addDecisionPointIf(final boolean isDecisionPoint) {
    if (isDecisionPoint && !complexities.isEmpty()) {
      complexities.push(complexities.pop() + 1);
    }
  }
}
