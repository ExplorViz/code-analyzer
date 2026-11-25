package net.explorviz.code.analysis.metrics.c;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.c.CParser;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of C. */
public final class CControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case CParser.FunctionDefinitionContext ctx -> Construct.function(ctx.functionBody());
      case CParser.SelectionStatementContext ctx when startsWith(ctx, "switch") ->
          Construct.switchOn(ctx.expression());
      case CParser.SelectionStatementContext ctx ->
          Construct.branch(ctx.expression(), ctx.statement(1));
      case CParser.IterationStatementContext ctx ->
          Construct.loop(ctx.expression(), ctx.forCondition());
      case CParser.LabeledStatementContext ctx when startsWith(ctx, "case") ->
          Construct.label(
              1, ctx.constantExpression() == null ? List.of() : List.of(ctx.constantExpression()));
      case CParser.LabeledStatementContext ctx when startsWith(ctx, "default") -> Construct.DEFAULT;
      case CParser.ConditionalExpressionContext ctx when ctx.conditionalExpression() != null ->
          Construct.TERNARY;
      case CParser.JumpStatementContext ctx when startsWith(ctx, "return") -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return switch (context) {
      case CParser.LogicalAndExpressionContext ctx -> ctx.inclusiveOrExpression().size() - 1;
      case CParser.LogicalOrExpressionContext ctx -> ctx.logicalAndExpression().size() - 1;
      default -> 0;
    };
  }

  private static boolean startsWith(final ParserRuleContext context, final String keyword) {
    return context.getStart() != null && keyword.equals(context.getStart().getText());
  }
}
