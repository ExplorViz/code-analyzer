package net.explorviz.code.analysis.metrics.cpp;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of C++. Lambdas belong to the function around them. */
public final class CppControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case CPP14Parser.FunctionDefinitionContext ctx -> Construct.function(bodyOf(ctx));
      case CPP14Parser.ClassSpecifierContext ctx -> Construct.TYPE;
      case CPP14Parser.LambdaExpressionContext ctx -> Construct.BLOCK;
      case CPP14Parser.SelectionStatementContext ctx when ctx.Switch() != null ->
          Construct.switchOn(ctx.condition());
      case CPP14Parser.SelectionStatementContext ctx ->
          Construct.branch(ctx.condition(), ctx.statement(1));
      case CPP14Parser.IterationStatementContext ctx ->
          Construct.loop(
              ctx.forInitStatement(), ctx.condition(), ctx.expression(), ctx.forRangeInitializer());
      case CPP14Parser.LabeledStatementContext ctx when ctx.Case() != null ->
          Construct.label(
              1, ctx.constantExpression() == null ? List.of() : List.of(ctx.constantExpression()));
      case CPP14Parser.LabeledStatementContext ctx when ctx.Default() != null -> Construct.DEFAULT;
      case CPP14Parser.TryBlockContext ctx -> Construct.TRY;
      case CPP14Parser.FunctionTryBlockContext ctx -> Construct.TRY;
      case CPP14Parser.HandlerContext ctx -> Construct.CATCH;
      case CPP14Parser.ConditionalExpressionContext ctx when ctx.Question() != null ->
          Construct.TERNARY;
      case CPP14Parser.JumpStatementContext ctx when ctx.Return() != null -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return switch (context) {
      case CPP14Parser.LogicalAndExpressionContext ctx -> Trees.count(ctx, CPP14Parser.AndAnd);
      case CPP14Parser.LogicalOrExpressionContext ctx -> Trees.count(ctx, CPP14Parser.OrOr);
      default -> 0;
    };
  }

  /** Returns the body, {@code null} if the function is defaulted or deleted. */
  private static ParserRuleContext bodyOf(final CPP14Parser.FunctionDefinitionContext ctx) {
    final CPP14Parser.FunctionBodyContext body = ctx.functionBody();
    if (body == null) {
      return null;
    }
    return body.compoundStatement() == null ? body.functionTryBlock() : body.compoundStatement();
  }
}
