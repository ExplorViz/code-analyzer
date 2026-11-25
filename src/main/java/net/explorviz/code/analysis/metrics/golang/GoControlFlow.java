package net.explorviz.code.analysis.metrics.golang;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of Go. Function literals belong to the function around them. */
public final class GoControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case GoParser.FunctionDeclContext ctx -> Construct.function(ctx.block());
      case GoParser.MethodDeclContext ctx -> Construct.function(ctx.block());
      case GoParser.FunctionLitContext ctx -> Construct.BLOCK;
      case GoParser.IfStmtContext ctx -> Construct.branch(ctx.expression(), elseOf(ctx));
      case GoParser.ForStmtContext ctx ->
          Construct.loop(ctx.condition(), ctx.forClause(), ctx.rangeClause());
      case GoParser.ExprSwitchStmtContext ctx -> Construct.switchOn(ctx.expression());
      case GoParser.TypeSwitchStmtContext ctx -> Construct.switchOn(ctx.typeSwitchGuard());
      case GoParser.SelectStmtContext ctx -> Construct.switchOn(null);
      case GoParser.ExprSwitchCaseContext ctx when ctx.CASE() != null ->
          labelOf(ctx.expressionList());
      case GoParser.ExprSwitchCaseContext ctx -> Construct.DEFAULT;
      case GoParser.TypeSwitchCaseContext ctx when ctx.CASE() != null ->
          Construct.label(countTypes(ctx.typeList()), List.of());
      case GoParser.TypeSwitchCaseContext ctx -> Construct.DEFAULT;
      case GoParser.CommCaseContext ctx when ctx.CASE() != null -> Construct.label(1, List.of());
      case GoParser.CommCaseContext ctx -> Construct.DEFAULT;
      case GoParser.ReturnStmtContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return context instanceof GoParser.ExpressionContext
        ? Trees.count(context, GoParser.LOGICAL_AND) + Trees.count(context, GoParser.LOGICAL_OR)
        : 0;
  }

  private static ParserRuleContext elseOf(final GoParser.IfStmtContext ctx) {
    return ctx.ifStmt() == null ? ctx.block(1) : ctx.ifStmt();
  }

  private static Construct labelOf(final GoParser.ExpressionListContext values) {
    return values == null
        ? Construct.label(1, List.of())
        : Construct.label(values.expression().size(), values.expression());
  }

  private static int countTypes(final GoParser.TypeListContext types) {
    return types == null ? 1 : types.type_().size() + types.NIL_LIT().size();
  }
}
