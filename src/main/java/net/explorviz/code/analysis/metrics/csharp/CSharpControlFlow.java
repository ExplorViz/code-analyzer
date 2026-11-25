package net.explorviz.code.analysis.metrics.csharp;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of C#. Lambdas and local functions belong to the function around them. */
public final class CSharpControlFlow implements ControlFlowGrammar {

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    final Construct function = functionOf(context);
    return function == Construct.NONE ? statementOf(context) : function;
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.Conditional_and_expressionContext ctx
          when ctx.conditional_and_expression() != null ->
          1;
      case CSharpParser.Conditional_or_expressionContext ctx
          when ctx.conditional_or_expression() != null ->
          1;
      case CSharpParser.Null_coalescing_expressionContext ctx
          when ctx.null_coalescing_expression() != null ->
          1;
      case CSharpParser.Assignment_operatorContext ctx when "??=".equals(ctx.getText()) -> 1;
      default -> 0;
    };
  }

  private static Construct functionOf(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.Method_declarationContext ctx ->
          Construct.function(
              ctx.method_body() == null
                  ? orNull(ctx.ref_method_body())
                  : orNull(ctx.method_body()));
      case CSharpParser.Constructor_declarationContext ctx ->
          Construct.function(orNull(ctx.constructor_body()));
      case CSharpParser.Method_bodyContext ctx when isReturned(ctx) -> Construct.RETURN;
      case CSharpParser.Local_function_bodyContext ctx when ctx.block() != null -> Construct.BLOCK;
      case CSharpParser.Ref_local_function_bodyContext ctx when ctx.block() != null ->
          Construct.BLOCK;
      case CSharpParser.Anonymous_function_bodyContext ctx when ctx.block() != null ->
          Construct.BLOCK;
      case CSharpParser.Anonymous_method_expressionContext ctx -> Construct.BLOCK;
      case CSharpParser.Class_bodyContext ctx -> Construct.TYPE;
      case CSharpParser.Struct_bodyContext ctx -> Construct.TYPE;
      case CSharpParser.Interface_bodyContext ctx -> Construct.TYPE;
      default -> Construct.NONE;
    };
  }

  private static Construct statementOf(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.If_statementContext ctx ->
          Construct.branch(ctx.boolean_expression(), ctx.embedded_statement(1));
      case CSharpParser.While_statementContext ctx -> Construct.loop(ctx.boolean_expression());
      case CSharpParser.Do_statementContext ctx -> Construct.loop(ctx.boolean_expression());
      case CSharpParser.For_statementContext ctx ->
          Construct.loop(ctx.for_initializer(), ctx.for_condition(), ctx.for_iterator());
      case CSharpParser.Foreach_statementContext ctx -> Construct.loop(ctx.expression());
      case CSharpParser.Switch_statementContext ctx ->
          Construct.switchOn(ctx.selector_expression());
      case CSharpParser.Switch_labelContext ctx ->
          ctx.pattern() == null ? Construct.DEFAULT : Construct.label(1, List.of());
      case CSharpParser.Switch_expressionContext ctx when ctx.switch_expression() != null ->
          Construct.switchOn(ctx.switch_expression());
      case CSharpParser.Switch_expression_armContext ctx -> labelOf(ctx);
      case CSharpParser.Case_guardContext ctx -> Construct.guard(ctx.null_coalescing_expression());
      case CSharpParser.Exception_filterContext ctx -> Construct.guard(ctx.boolean_expression());
      case CSharpParser.Try_statementContext ctx -> Construct.TRY;
      case CSharpParser.Specific_catch_clauseContext ctx -> Construct.CATCH;
      case CSharpParser.General_catch_clauseContext ctx -> Construct.CATCH;
      case CSharpParser.Lock_statementContext ctx -> Construct.BLOCK;
      case CSharpParser.Using_statementContext ctx -> Construct.BLOCK;
      case CSharpParser.Fixed_statementContext ctx -> Construct.BLOCK;
      case CSharpParser.Conditional_expressionContext ctx when ctx.getChildCount() > 1 ->
          Construct.TERNARY;
      case CSharpParser.Return_statementContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  private static Construct labelOf(final CSharpParser.Switch_expression_armContext arm) {
    final boolean isDefault = arm.case_guard() == null && "_".equals(Trees.text(arm.pattern()));
    return isDefault ? Construct.DEFAULT : Construct.label(1, List.of());
  }

  /** Whether the body is an expression whose value is returned. */
  private static boolean isReturned(final CSharpParser.Method_bodyContext body) {
    return body.block() == null
        && orNull(body) != null
        && body.getParent() instanceof CSharpParser.Method_declarationContext method
        && !"void".equals(Trees.text(method.return_type()));
  }

  private static ParserRuleContext orNull(final ParserRuleContext body) {
    return body == null || List.of(";").equals(Trees.firstTokens(body, 1)) ? null : body;
  }
}
