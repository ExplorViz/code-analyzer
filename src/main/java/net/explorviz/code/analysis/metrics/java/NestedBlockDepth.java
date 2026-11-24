package net.explorviz.code.analysis.metrics.java;

import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserBaseListener;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Nested block depth of a function. The function body has depth one. {@code if}, loops, {@code
 * switch}, {@code try}, {@code synchronized} and lambdas with a block body each add a level, with
 * or without braces. An {@code else if} is on the same level as the {@code if} it belongs to.
 */
public final class NestedBlockDepth extends JavaParserBaseListener {

  private int depth = 1;
  private int maxDepth = 1;

  private NestedBlockDepth() {
    super();
  }

  /** Calculates the nested block depth of the given function body. */
  public static int of(final ParserRuleContext body) {
    final NestedBlockDepth listener = new NestedBlockDepth();
    JavaTreeWalker.walkFunction(listener, body);
    return listener.maxDepth;
  }

  @Override
  public void enterEveryRule(final ParserRuleContext ctx) {
    if (opensLevel(ctx)) {
      depth++;
      maxDepth = Math.max(maxDepth, depth);
    }
  }

  @Override
  public void exitEveryRule(final ParserRuleContext ctx) {
    if (opensLevel(ctx)) {
      depth--;
    }
  }

  private static boolean opensLevel(final ParserRuleContext ctx) {
    if (ctx instanceof JavaParser.StatementContext statement) {
      return isNestingStatement(statement);
    }
    if (ctx instanceof JavaParser.LambdaBodyContext lambda) {
      return lambda.block() != null;
    }
    return ctx instanceof JavaParser.SwitchExpressionContext;
  }

  private static boolean isNestingStatement(final JavaParser.StatementContext ctx) {
    return ctx.IF() != null && !isElseIf(ctx)
        || ctx.FOR() != null
        || ctx.WHILE() != null
        || ctx.TRY() != null
        || ctx.SWITCH() != null
        || ctx.SYNCHRONIZED() != null;
  }

  private static boolean isElseIf(final JavaParser.StatementContext ctx) {
    return ctx.getParent() instanceof JavaParser.StatementContext parent
        && parent.IF() != null
        && parent.statement(1) == ctx;
  }
}
