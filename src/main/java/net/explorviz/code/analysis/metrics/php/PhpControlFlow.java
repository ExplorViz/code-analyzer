package net.explorviz.code.analysis.metrics.php;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.php.PhpParser;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of PHP. Anonymous functions belong to the function around them. */
public final class PhpControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case PhpParser.FunctionDeclarationContext ctx -> Construct.function(ctx.blockStatement());
      case PhpParser.ClassStatementContext ctx when ctx.Function_() != null ->
          Construct.function(ctx.methodBody() == null ? null : ctx.methodBody().blockStatement());
      case PhpParser.ClassDeclarationContext ctx -> Construct.TYPE;
      case PhpParser.AnonymousClassContext ctx -> Construct.TYPE;
      case PhpParser.EnumDeclarationContext ctx -> Construct.TYPE;
      case PhpParser.LambdaFunctionExprContext ctx when ctx.blockStatement() != null ->
          Construct.BLOCK;
      case PhpParser.IfStatementContext ctx -> Construct.branch(ctx.parentheses(), elseOf(ctx));
      case PhpParser.ElseIfStatementContext ctx -> Construct.elseIf(ctx.parentheses());
      case PhpParser.ElseIfColonStatementContext ctx -> Construct.elseIf(ctx.parentheses());
      case PhpParser.WhileStatementContext ctx -> Construct.loop(ctx.parentheses());
      case PhpParser.DoWhileStatementContext ctx -> Construct.loop(ctx.parentheses());
      case PhpParser.ForStatementContext ctx ->
          Construct.loop(ctx.forInit(), ctx.expressionList(), ctx.forUpdate());
      case PhpParser.ForeachStatementContext ctx ->
          Construct.loop(ctx.expression() == null ? ctx.chain(0) : ctx.expression());
      case PhpParser.SwitchStatementContext ctx -> Construct.switchOn(ctx.parentheses());
      case PhpParser.SwitchBlockContext ctx when ctx.Case().isEmpty() -> Construct.DEFAULT;
      case PhpParser.SwitchBlockContext ctx ->
          Construct.labels(ctx.Case().size(), ctx.Case().size(), ctx.expression());
      case PhpParser.MatchExprContext ctx -> Construct.switchOn(ctx.expression());
      case PhpParser.MatchItemContext ctx -> labelOf(ctx);
      case PhpParser.TryCatchFinallyContext ctx -> Construct.TRY;
      case PhpParser.CatchClauseContext ctx -> Construct.CATCH;
      // The grammar does not know ?->, whose error recovery ends up here.
      case PhpParser.ConditionalExpressionContext ctx when ctx.Colon() != null -> Construct.TERNARY;
      case PhpParser.ReturnStatementContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    final boolean isShortCircuit =
        context instanceof PhpParser.LogicalAndExpressionContext
            || context instanceof PhpParser.LogicalIncOrExpressionContext
            || context instanceof PhpParser.CoalesceExpressionContext
            || context instanceof PhpParser.AssignmentExpressionContext assignment
                && assignment.assignmentOperator() != null
                && "??=".equals(assignment.assignmentOperator().getText());
    return isShortCircuit ? 1 : 0;
  }

  private static ParserRuleContext elseOf(final PhpParser.IfStatementContext ctx) {
    return ctx.elseStatement() == null ? ctx.elseColonStatement() : ctx.elseStatement().statement();
  }

  private static Construct labelOf(final PhpParser.MatchItemContext ctx) {
    final List<PhpParser.ExpressionContext> expressions = ctx.expression();
    if (expressions.isEmpty() || "default".equalsIgnoreCase(expressions.getFirst().getText())) {
      return Construct.DEFAULT;
    }
    // The last expression is the result.
    final List<PhpParser.ExpressionContext> values =
        expressions.subList(0, Math.max(1, expressions.size() - 1));
    return Construct.label(values.size(), values);
  }
}
