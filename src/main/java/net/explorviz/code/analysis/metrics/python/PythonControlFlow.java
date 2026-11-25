package net.explorviz.code.analysis.metrics.python;

import net.explorviz.code.analysis.antlr.generated.python.PythonParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of Python. Local functions belong to the function around them. */
public final class PythonControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case PythonParser.FuncdefContext ctx when isLocal(ctx) -> Construct.BLOCK;
      case PythonParser.FuncdefContext ctx -> Construct.function(ctx.suite());
      case PythonParser.ClassdefContext ctx -> Construct.TYPE;
      case PythonParser.If_stmtContext ctx -> Construct.branch(ctx.cond, ctx.else_clause());
      case PythonParser.Elif_clauseContext ctx -> Construct.elseIf(ctx.test());
      case PythonParser.While_stmtContext ctx -> Construct.loopElse(ctx.test(), ctx.else_clause());
      case PythonParser.For_stmtContext ctx ->
          Construct.loopElse(ctx.testlist(), ctx.else_clause());
      case PythonParser.Comp_forContext ctx -> Construct.guard(ctx.logical_test());
      case PythonParser.Comp_iterContext ctx when ctx.IF() != null -> Construct.guard(ctx.test());
      case PythonParser.Try_stmtContext ctx -> Construct.TRY;
      case PythonParser.Except_clauseContext ctx -> Construct.CATCH;
      case PythonParser.With_stmtContext ctx -> Construct.BLOCK;
      case PythonParser.TestContext ctx when ctx.IF() != null -> Construct.TERNARY;
      case PythonParser.Return_stmtContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return context instanceof PythonParser.Logical_testContext
        ? Trees.count(context, PythonParser.AND) + Trees.count(context, PythonParser.OR)
        : 0;
  }

  private static boolean isLocal(final PythonParser.FuncdefContext ctx) {
    return Trees.enclosing(
            ctx, PythonParser.FuncdefContext.class, PythonParser.ClassdefContext.class)
        instanceof PythonParser.FuncdefContext;
  }
}
