package net.explorviz.code.analysis.metrics.swift;

import java.util.List;
import java.util.Set;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5Parser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of Swift. Closures and local functions belong to the function around them. */
public final class SwiftControlFlow implements ControlFlowGrammar {

  private static final Set<String> CONTROL_STRUCTURES =
      Set.of("if", "guard", "switch", "for", "while", "repeat", "do");

  private static final Set<String> SHORT_CIRCUIT_OPERATORS = Set.of("&&", "||", "??");

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    final Construct function = functionOf(context);
    return function == Construct.NONE ? statementOf(context) : function;
  }

  /** The comma between conditions has the same meaning as a logical and. */
  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.Binary_operatorContext ctx ->
          SHORT_CIRCUIT_OPERATORS.contains(ctx.getText()) ? 1 : 0;
      case Swift5Parser.Condition_listContext ctx -> Math.max(0, ctx.condition().size() - 1);
      default -> 0;
    };
  }

  private static Construct functionOf(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.Function_declarationContext ctx when isLocal(ctx) -> Construct.BLOCK;
      case Swift5Parser.Function_declarationContext ctx -> Construct.function(ctx.function_body());
      case Swift5Parser.Initializer_declarationContext ctx ->
          Construct.function(ctx.initializer_body());
      case Swift5Parser.StatementContext ctx when isReturned(ctx) -> Construct.RETURN;
      case Swift5Parser.Closure_expressionContext ctx when isBlock(ctx.statements()) ->
          Construct.BLOCK;
      case Swift5Parser.Class_bodyContext ctx -> Construct.TYPE;
      case Swift5Parser.Struct_bodyContext ctx -> Construct.TYPE;
      case Swift5Parser.Union_style_enum_membersContext ctx -> Construct.TYPE;
      case Swift5Parser.Raw_value_style_enum_membersContext ctx -> Construct.TYPE;
      case Swift5Parser.Protocol_bodyContext ctx -> Construct.TYPE;
      case Swift5Parser.Extension_bodyContext ctx -> Construct.TYPE;
      default -> Construct.NONE;
    };
  }

  private static Construct statementOf(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.If_statementContext ctx ->
          Construct.branch(ctx.condition_list(), elseOf(ctx.else_clause()));
      case Swift5Parser.Guard_statementContext ctx -> Construct.branch(ctx.condition_list(), null);
      case Swift5Parser.For_in_statementContext ctx -> Construct.loop(ctx.expression());
      case Swift5Parser.While_statementContext ctx -> Construct.loop(ctx.condition_list());
      case Swift5Parser.Repeat_while_statementContext ctx -> Construct.loop(ctx.expression());
      case Swift5Parser.Where_clauseContext ctx -> Construct.guard(ctx.where_expression());
      case Swift5Parser.Switch_statementContext ctx -> Construct.switchOn(ctx.expression());
      case Swift5Parser.Case_labelContext ctx ->
          Construct.label(
              ctx.case_item_list() == null ? 1 : ctx.case_item_list().pattern().size(), List.of());
      case Swift5Parser.Default_labelContext ctx -> Construct.DEFAULT;
      case Swift5Parser.Do_statementContext ctx when ctx.catch_clauses() != null -> Construct.TRY;
      case Swift5Parser.Catch_clauseContext ctx -> Construct.CATCH;
      case Swift5Parser.ExpressionContext ctx when countTernaries(ctx) > 0 ->
          Construct.ternaries(countTernaries(ctx));
      case Swift5Parser.Return_statementContext ctx -> Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  private static boolean isLocal(final Swift5Parser.Function_declarationContext ctx) {
    final ParserRuleContext owner =
        Trees.enclosing(
            ctx,
            Swift5Parser.Class_bodyContext.class,
            Swift5Parser.Struct_bodyContext.class,
            Swift5Parser.Union_style_enum_membersContext.class,
            Swift5Parser.Raw_value_style_enum_membersContext.class,
            Swift5Parser.Protocol_bodyContext.class,
            Swift5Parser.Extension_bodyContext.class,
            Swift5Parser.Function_declarationContext.class,
            Swift5Parser.Initializer_declarationContext.class,
            Swift5Parser.Deinitializer_declarationContext.class,
            Swift5Parser.Subscript_declarationContext.class,
            Swift5Parser.Getter_setter_blockContext.class,
            Swift5Parser.WillSet_didSet_blockContext.class,
            Swift5Parser.Closure_expressionContext.class);
    return owner instanceof Swift5Parser.Function_declarationContext
        || owner instanceof Swift5Parser.Initializer_declarationContext
        || owner instanceof Swift5Parser.Deinitializer_declarationContext
        || owner instanceof Swift5Parser.Subscript_declarationContext
        || owner instanceof Swift5Parser.Getter_setter_blockContext
        || owner instanceof Swift5Parser.WillSet_didSet_blockContext
        || owner instanceof Swift5Parser.Closure_expressionContext;
  }

  /** Whether the statement is the only expression of a function with a result. */
  private static boolean isReturned(final Swift5Parser.StatementContext statement) {
    return statement.expression() != null
        && statement.getParent() instanceof Swift5Parser.StatementsContext statements
        && statements.statement().size() == 1
        && statements.getParent() != null
        && statements.getParent().getParent() instanceof Swift5Parser.Function_bodyContext body
        && body.getParent() instanceof Swift5Parser.Function_declarationContext function
        && function.function_signature() != null
        && function.function_signature().function_result() != null;
  }

  /** Whether the closure is more than a single expression. */
  private static boolean isBlock(final Swift5Parser.StatementsContext statements) {
    return statements != null
        && (statements.statement().size() > 1
            || statements.statement().stream()
                .anyMatch(statement -> CONTROL_STRUCTURES.contains(firstTokenOf(statement))));
  }

  private static String firstTokenOf(final ParserRuleContext context) {
    return Trees.firstTokens(context, 1).stream().findFirst().orElse("");
  }

  private static ParserRuleContext elseOf(final Swift5Parser.Else_clauseContext clause) {
    if (clause == null) {
      return null;
    }
    return clause.if_statement() == null ? clause.code_block() : clause.if_statement();
  }

  private static int countTernaries(final Swift5Parser.ExpressionContext expression) {
    if (expression.binary_expressions() == null) {
      return 0;
    }
    return (int)
        expression.binary_expressions().binary_expression().stream()
            .filter(operator -> operator.conditional_operator() != null)
            .count();
  }
}
