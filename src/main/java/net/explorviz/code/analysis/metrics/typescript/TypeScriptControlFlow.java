package net.explorviz.code.analysis.metrics.typescript;

import java.util.List;
import java.util.function.Predicate;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Control flow of TypeScript and JavaScript. Function expressions belong to the function around
 * them, unless they have a name.
 */
public final class TypeScriptControlFlow implements ControlFlowGrammar {

  private final Predicate<TypeScriptParser.ArrowFunctionDeclarationContext> isNamed;

  /** Creates the control flow with the given check for named arrow functions. */
  public TypeScriptControlFlow(
      final Predicate<TypeScriptParser.ArrowFunctionDeclarationContext> isNamed) {
    this.isNamed = isNamed;
  }

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    final Construct function = functionOf(context);
    return function == Construct.NONE ? statementOf(context) : function;
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    final boolean isShortCircuit =
        context instanceof TypeScriptParser.LogicalAndExpressionContext
            || context instanceof TypeScriptParser.LogicalOrExpressionContext
            || context instanceof TypeScriptParser.CoalesceExpressionContext
            || context instanceof TypeScriptParser.AssignmentOperatorExpressionContext assignment
                && assignment.assignmentOperator() != null
                && "??=".equals(assignment.assignmentOperator().getText());
    return isShortCircuit ? 1 : 0;
  }

  private Construct functionOf(final ParserRuleContext context) {
    return switch (context) {
      case TypeScriptParser.FunctionDeclarationContext ctx ->
          Construct.function(ctx.functionBody());
      case TypeScriptParser.MethodDeclarationExpressionContext ctx ->
          Construct.function(ctx.functionBody());
      case TypeScriptParser.ConstructorDeclarationContext ctx ->
          Construct.function(ctx.functionBody());
      case TypeScriptParser.ArrowFunctionDeclarationContext ctx when isNamed.test(ctx) ->
          Construct.function(ctx.arrowFunctionBody());
      case TypeScriptParser.ArrowFunctionBodyContext ctx when ctx.functionBody() == null ->
          isNamed.test((TypeScriptParser.ArrowFunctionDeclarationContext) ctx.getParent())
              ? Construct.RETURN
              : Construct.NONE;
      case TypeScriptParser.ArrowFunctionDeclarationContext ctx
          when ctx.arrowFunctionBody() != null && ctx.arrowFunctionBody().functionBody() != null ->
          Construct.BLOCK;
      case TypeScriptParser.AnonymousFunctionContext ctx when ctx.functionBody() != null ->
          Construct.BLOCK;
      case TypeScriptParser.GeneratorFunctionDeclarationContext ctx -> Construct.BLOCK;
      case TypeScriptParser.GeneratorMethodContext ctx -> Construct.BLOCK;
      case TypeScriptParser.GetAccessorContext ctx -> Construct.BLOCK;
      case TypeScriptParser.SetAccessorContext ctx -> Construct.BLOCK;
      case TypeScriptParser.ClassTailContext ctx -> Construct.TYPE;
      default -> Construct.NONE;
    };
  }

  private static Construct statementOf(final ParserRuleContext context) {
    return switch (context) {
      case TypeScriptParser.IfStatementContext ctx ->
          Construct.branch(ctx.expressionSequence(), ctx.statement(1));
      case TypeScriptParser.DoStatementContext ctx -> Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.WhileStatementContext ctx -> Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.ForStatementContext ctx -> loopOf(ctx.expressionSequence());
      case TypeScriptParser.ForVarStatementContext ctx ->
          Construct.loop(
              ctx.variableDeclarationList(), ctx.expressionSequence(0), ctx.expressionSequence(1));
      case TypeScriptParser.ForInStatementContext ctx -> Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.ForVarInStatementContext ctx ->
          Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.ForOfStatementContext ctx -> Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.ForVarOfStatementContext ctx ->
          Construct.loop(ctx.expressionSequence());
      case TypeScriptParser.SwitchStatementContext ctx ->
          Construct.switchOn(ctx.expressionSequence());
      case TypeScriptParser.CaseClauseContext ctx ->
          Construct.label(
              1, ctx.expressionSequence() == null ? List.of() : List.of(ctx.expressionSequence()));
      case TypeScriptParser.DefaultClauseContext ctx -> Construct.DEFAULT;
      case TypeScriptParser.TryStatementContext ctx -> Construct.TRY;
      case TypeScriptParser.CatchProductionContext ctx -> Construct.CATCH;
      case TypeScriptParser.TernaryExpressionContext ctx -> Construct.TERNARY;
      case TypeScriptParser.ReturnStatementContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  private static Construct loopOf(final List<? extends ParserRuleContext> conditions) {
    return Construct.loop(conditions.toArray(ParserRuleContext[]::new));
  }
}
