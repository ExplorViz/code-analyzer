package net.explorviz.code.analysis.metrics.rust;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Control flow of Rust. Closures belong to the function around them. The arguments of macros are
 * not parsed and hence have no control flow.
 */
public final class RustControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case RustParser.Function_Context ctx -> Construct.function(ctx.blockExpression());
      case RustParser.ImplementationContext ctx -> Construct.TYPE;
      case RustParser.Trait_Context ctx -> Construct.TYPE;
      case RustParser.ClosureExpressionContext ctx when hasBlock(ctx) -> Construct.BLOCK;
      case RustParser.IfExpressionContext ctx ->
          Construct.branch(
              ctx.expression(),
              firstOf(ctx.ifExpression(), ctx.ifLetExpression(), ctx.blockExpression(1)));
      case RustParser.IfLetExpressionContext ctx ->
          Construct.branch(
              ctx.expression(),
              firstOf(ctx.ifExpression(), ctx.ifLetExpression(), ctx.blockExpression(1)));
      case RustParser.InfiniteLoopExpressionContext ctx -> Construct.loop();
      case RustParser.PredicateLoopExpressionContext ctx -> Construct.loop(ctx.expression());
      case RustParser.PredicatePatternLoopExpressionContext ctx -> Construct.loop(ctx.expression());
      case RustParser.IteratorLoopExpressionContext ctx -> Construct.loop(ctx.expression());
      case RustParser.MatchExpressionContext ctx -> Construct.switchOn(ctx.expression());
      case RustParser.MatchArmContext ctx -> labelOf(ctx);
      case RustParser.MatchArmGuardContext ctx -> Construct.guard(ctx.expression());
      case RustParser.ReturnExpressionContext ctx -> Construct.RETURN;
      case RustParser.ExpressionContext ctx when isReturned(ctx) -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return context instanceof RustParser.LazyBooleanExpressionContext ? 1 : 0;
  }

  private static boolean hasBlock(final RustParser.ClosureExpressionContext ctx) {
    return ctx.blockExpression() != null
        || ctx.expression() instanceof RustParser.ExpressionWithBlock_Context body
            && body.expressionWithBlock() != null
            && body.expressionWithBlock().blockExpression() != null;
  }

  private static Construct labelOf(final RustParser.MatchArmContext arm) {
    if (arm.pattern() == null) {
      return Construct.label(1, List.of());
    }
    return arm.matchArmGuard() == null && "_".equals(arm.pattern().getText())
        ? Construct.DEFAULT
        : Construct.label(arm.pattern().patternNoTopAlt().size(), List.of());
  }

  /** Whether the expression is the last one of a function without being a block itself. */
  private static boolean isReturned(final RustParser.ExpressionContext expression) {
    return !(expression instanceof RustParser.ExpressionWithBlock_Context)
        && expression.getParent() instanceof RustParser.StatementsContext statements
        && statements.getParent() != null
        && statements.getParent().getParent() instanceof RustParser.Function_Context function
        && function.functionReturnType() != null;
  }

  private static ParserRuleContext firstOf(final ParserRuleContext... contexts) {
    for (final ParserRuleContext context : contexts) {
      if (context != null) {
        return context;
      }
    }
    return null;
  }
}
