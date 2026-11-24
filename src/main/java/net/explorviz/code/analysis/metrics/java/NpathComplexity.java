package net.explorviz.code.analysis.metrics.java;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * NPath complexity (Nejmeh, 1988) of functions, i.e. the number of acyclic execution paths, with
 * the same results as Checkstyle's {@code NPathComplexity}.
 *
 * <p>Like Checkstyle, this visits a file once and remembers the end of the last measured
 * expression: the operators of an expression are counted at its start, so that ternary operators
 * and returns up to its end are skipped.
 */
public final class NpathComplexity extends FunctionListener<BigInteger> {

  private final NpathPaths paths = new NpathPaths();

  /** Index of the last token of the expressions that were measured so far. */
  private int measuredUntil = -1;

  /** For each open return and ternary operator, whether it is measured on its own. */
  private final Deque<Boolean> measuredExpressions = new ArrayDeque<>();

  private NpathComplexity() {
    super();
  }

  /** Measures all functions of the file. */
  public static NpathComplexity ofFile(final ParserRuleContext file) {
    final NpathComplexity listener = new NpathComplexity();
    JavaTreeWalker.walk(listener, file);
    return listener;
  }

  @Override
  /* package */ void enterFunction() {
    paths.enter(0);
  }

  @Override
  /* package */ BigInteger exitFunction() {
    return paths.leaveFunction();
  }

  @Override
  public void enterStatement(final JavaParser.StatementContext ctx) {
    if (isElse(ctx)) {
      paths.enterElse();
    }
    if (ctx.FOR() != null) {
      enterConditional(ctx.forControl());
    } else if (isConditional(ctx)) {
      enterConditional(ctx.expression(0));
    } else if (ctx.TRY() != null) {
      paths.enter(1);
    } else if (ctx.RETURN() != null) {
      enterExpression(ctx, ctx.RETURN().getSymbol());
    }
  }

  @Override
  public void exitStatement(final JavaParser.StatementContext ctx) {
    if (isConditional(ctx)) {
      paths.leaveConditional();
    } else if (ctx.TRY() != null) {
      paths.leaveTry();
    } else if (ctx.RETURN() != null) {
      leaveExpression();
    }
    if (isElse(ctx)) {
      paths.leaveBranch();
    }
  }

  @Override
  public void enterCatchClause(final JavaParser.CatchClauseContext ctx) {
    paths.enter(1);
  }

  @Override
  public void exitCatchClause(final JavaParser.CatchClauseContext ctx) {
    paths.leaveCatch();
  }

  @Override
  public void enterTernaryExpression(final JavaParser.TernaryExpressionContext ctx) {
    enterExpression(ctx, ctx.bop);
  }

  @Override
  public void exitTernaryExpression(final JavaParser.TernaryExpressionContext ctx) {
    leaveExpression();
  }

  @Override
  public void enterSwitchExpression(final JavaParser.SwitchExpressionContext ctx) {
    enterConditional(ctx.expression());
  }

  @Override
  public void exitSwitchExpression(final JavaParser.SwitchExpressionContext ctx) {
    paths.leaveConditional();
  }

  @Override
  public void enterSwitchBlockStatementGroup(
      final JavaParser.SwitchBlockStatementGroupContext ctx) {
    paths.enterCase(SwitchCases.countCases(ctx));
  }

  @Override
  public void exitSwitchBlockStatementGroup(final JavaParser.SwitchBlockStatementGroupContext ctx) {
    paths.leaveBranch();
  }

  @Override
  public void enterSwitchLabel(final JavaParser.SwitchLabelContext ctx) {
    if (ctx.DEFAULT() != null) {
      addDefault();
    }
  }

  @Override
  public void enterSwitchLabeledRule(final JavaParser.SwitchLabeledRuleContext ctx) {
    if (SwitchCases.isFirstOfGroup(ctx)) {
      paths.enterCase(SwitchCases.countCases(ctx));
    }
    if (ctx.DEFAULT() != null) {
      addDefault();
    }
  }

  @Override
  public void exitSwitchLabeledRule(final JavaParser.SwitchLabeledRuleContext ctx) {
    if (SwitchCases.isLastOfGroup(ctx)) {
      paths.leaveBranch();
    }
  }

  @Override
  public void enterGuard(final JavaParser.GuardContext ctx) {
    enterConditional(ctx.expression());
  }

  @Override
  public void exitGuard(final JavaParser.GuardContext ctx) {
    paths.leaveConditional();
  }

  @Override
  public void enterInterfaceMethodModifier(final JavaParser.InterfaceMethodModifierContext ctx) {
    if (ctx.DEFAULT() != null) {
      // Checkstyle does not tell the modifier and the label apart.
      addDefault();
    }
  }

  private void addDefault() {
    paths.enter(1);
    paths.leaveDefault();
  }

  private void enterConditional(final ParserRuleContext condition) {
    measureUntil(condition);
    paths.enter(1 + ConditionalOperators.countPaths(condition));
  }

  private void enterExpression(final ParserRuleContext expression, final Token operator) {
    final boolean isMeasured = operator.getTokenIndex() > measuredUntil;
    measuredExpressions.push(isMeasured);
    if (isMeasured) {
      measureUntil(expression);
      paths.enter(ConditionalOperators.countPaths(expression));
    }
  }

  private void leaveExpression() {
    if (measuredExpressions.pop()) {
      paths.leaveExpression();
    }
  }

  private void measureUntil(final ParserRuleContext ctx) {
    if (ctx != null && ctx.getStop() != null) {
      measuredUntil = Math.max(measuredUntil, ctx.getStop().getTokenIndex());
    }
  }

  private static boolean isConditional(final JavaParser.StatementContext ctx) {
    // WHILE covers while and do-while loops.
    return ctx.IF() != null || ctx.FOR() != null || ctx.WHILE() != null || ctx.SWITCH() != null;
  }

  private static boolean isElse(final JavaParser.StatementContext ctx) {
    return ctx.getParent() instanceof JavaParser.StatementContext parent
        && parent.ELSE() != null
        && parent.statement(1) == ctx;
  }
}
