package net.explorviz.code.analysis.metrics.swift;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5Parser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Members of the classes, structures, enumerations and protocols of Swift without extensions. */
public final class SwiftMembers implements MemberGrammar {

  private static final String SELF = "self";

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.Class_declarationContext ctx ->
          typeMembersOf(
              ctx.class_name(),
              Trees.find(
                  ctx.class_body(),
                  Swift5Parser.Class_memberContext.class,
                  Swift5Parser.DeclarationContext.class));
      case Swift5Parser.Struct_declarationContext ctx ->
          typeMembersOf(
              ctx.struct_name(),
              Trees.find(
                  ctx.struct_body(),
                  Swift5Parser.Struct_memberContext.class,
                  Swift5Parser.DeclarationContext.class));
      case Swift5Parser.Union_style_enumContext ctx ->
          typeMembersOf(
              ctx.enum_name(),
              Trees.find(
                  ctx.union_style_enum_members(),
                  Swift5Parser.Union_style_enum_memberContext.class,
                  Swift5Parser.DeclarationContext.class));
      case Swift5Parser.Raw_value_style_enumContext ctx ->
          typeMembersOf(
              ctx.enum_name(),
              Trees.find(
                  ctx.raw_value_style_enum_members(),
                  Swift5Parser.Raw_value_style_enum_memberContext.class,
                  Swift5Parser.DeclarationContext.class));
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.Postfix_expressionContext ctx -> expressionAccessesOf(ctx);
      case Swift5Parser.Function_call_argumentContext ctx when ctx.identifier() != null ->
          List.of(Access.of(null, ctx.identifier().getText()));
      case Swift5Parser.In_out_expressionContext ctx when ctx.identifier() != null ->
          List.of(Access.of(null, ctx.identifier().getText()));
      default -> List.of();
    };
  }

  @Override
  public boolean opensScope(final ParserRuleContext context) {
    return context instanceof Swift5Parser.Code_blockContext
        || context instanceof Swift5Parser.Closure_expressionContext
        || context instanceof Swift5Parser.Function_declarationContext
        || context instanceof Swift5Parser.If_statementContext
        || context instanceof Swift5Parser.For_in_statementContext
        || context instanceof Swift5Parser.While_statementContext
        || context instanceof Swift5Parser.Switch_caseContext
        || context instanceof Swift5Parser.Catch_clauseContext
        || context instanceof Swift5Parser.Setter_clauseContext;
  }

  @Override
  public List<String> declaredVariables(final ParserRuleContext context) {
    return switch (context) {
      case Swift5Parser.Pattern_initializerContext ctx -> namesOf(ctx.pattern());
      case Swift5Parser.Optional_binding_conditionContext ctx -> namesOf(ctx.pattern());
      case Swift5Parser.Value_binding_patternContext ctx -> namesOf(ctx.pattern());
      case Swift5Parser.PatternContext ctx
          when ctx.getParent() instanceof Swift5Parser.For_in_statementContext ->
          namesOf(ctx);
      case Swift5Parser.Variable_nameContext ctx -> List.of(ctx.getText());
      case Swift5Parser.Local_parameter_nameContext ctx -> List.of(ctx.getText());
      case Swift5Parser.Closure_parameterContext ctx when ctx.closure_parameter_name != null ->
          List.of(ctx.closure_parameter_name.getText());
      case Swift5Parser.Closure_parameter_clauseContext ctx when ctx.identifier_list() != null ->
          ctx.identifier_list().identifier().stream().map(ParserRuleContext::getText).toList();
      case Swift5Parser.Setter_nameContext ctx when ctx.identifier() != null ->
          List.of(ctx.identifier().getText());
      default -> List.of();
    };
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof Swift5Parser.AttributesContext;
  }

  private static TypeMembers typeMembersOf(
      final ParserRuleContext name, final List<? extends ParserRuleContext> declarations) {
    final TypeMembers members = new TypeMembers(Trees.text(name), "Self");
    for (final ParserRuleContext member : declarations) {
      final Swift5Parser.DeclarationContext declaration =
          member.getRuleContext(Swift5Parser.DeclarationContext.class, 0);
      if (declaration != null) {
        addMember(members, declaration);
      }
    }
    return members;
  }

  private static void addMember(
      final TypeMembers members, final Swift5Parser.DeclarationContext declaration) {
    if (declaration.variable_declaration() != null) {
      addVariable(members, declaration.variable_declaration());
    } else if (declaration.constant_declaration() != null) {
      addConstant(members, declaration.constant_declaration());
    } else if (declaration.function_declaration() != null) {
      addMethod(members, declaration.function_declaration());
    } else if (declaration.subscript_declaration() != null) {
      members.addMethod(
          Method.of(
              "subscript", SELF, Method.Arguments.ANY, true, declaration.subscript_declaration()));
    }
  }

  private static void addVariable(
      final TypeMembers members, final Swift5Parser.Variable_declarationContext variable) {
    if (variable.variable_name() == null) {
      addFields(members, variable.pattern_initializer_list(), false);
      return;
    }
    final String name = variable.variable_name().getText();
    if (variable.getter_setter_block() == null) {
      members.addField(name);
    } else {
      members.addMethod(
          Method.of(name, SELF, Method.Arguments.ANY, true, variable.getter_setter_block()));
    }
  }

  private static void addConstant(
      final TypeMembers members, final Swift5Parser.Constant_declarationContext constant) {
    addFields(
        members,
        constant.pattern_initializer_list(),
        constant.declaration_modifiers() != null
            && Trees.tokens(constant.declaration_modifiers()).contains("static"));
  }

  private static void addFields(
      final TypeMembers members,
      final Swift5Parser.Pattern_initializer_listContext list,
      final boolean isConstant) {
    if (list != null) {
      for (final Swift5Parser.Pattern_initializerContext initializer : list.pattern_initializer()) {
        namesOf(initializer.pattern()).forEach(name -> members.addField(name, isConstant));
      }
    }
  }

  private static void addMethod(
      final TypeMembers members, final Swift5Parser.Function_declarationContext function) {
    if (function.function_name() == null) {
      return;
    }
    final Swift5Parser.Parameter_clauseContext parameters =
        function.function_signature() == null
            ? null
            : function.function_signature().parameter_clause();
    final Swift5Parser.Function_bodyContext body = function.function_body();
    members.addMethod(
        Method.of(
            function.function_name().getText(),
            SELF,
            argumentsOf(parameters),
            body != null && body.code_block() != null && body.code_block().statements() != null,
            parameters,
            body));
  }

  private static Method.Arguments argumentsOf(
      final Swift5Parser.Parameter_clauseContext parameters) {
    if (parameters == null) {
      return Method.Arguments.ANY;
    }
    int required = 0;
    int optional = 0;
    boolean isVariadic = false;
    if (parameters.parameter_list() != null) {
      for (final Swift5Parser.ParameterContext parameter :
          parameters.parameter_list().parameter()) {
        isVariadic |= parameter.range_operator() != null;
        if (parameter.default_argument_clause() != null || parameter.range_operator() != null) {
          optional++;
        } else {
          required++;
        }
      }
    }
    return Method.Arguments.of(required, optional, isVariadic);
  }

  /** The expression is a name or call, followed by members, calls etc. */
  private static List<Access> expressionAccessesOf(
      final Swift5Parser.Postfix_expressionContext ctx) {
    final List<ParseTree> parts = new ArrayList<>(ctx.children == null ? List.of() : ctx.children);
    parts.removeIf(
        part ->
            part instanceof Swift5Parser.Optional_chaining_suffixContext
                || part instanceof Swift5Parser.Forced_value_suffixContext);
    if (ctx.primary_expression() == null) {
      return List.of();
    }
    final Swift5Parser.Primary_expressionContext primary = ctx.primary_expression();
    if (primary.self_expression() instanceof Swift5Parser.Self_method_expressionContext self
        && self.identifier() != null) {
      return List.of(accessOf(SELF, self.identifier().getText(), parts, 1));
    }
    if (primary.unqualified_name() == null || primary.unqualified_name().identifier() == null) {
      return List.of();
    }
    final String name = primary.unqualified_name().identifier().getText();
    final List<Access> accesses = new ArrayList<>();
    accesses.add(accessOf(null, name, parts, 1));
    if (parts.size() > 1
        && parts.get(1) instanceof Swift5Parser.Explicit_member_suffixContext member
        && member.identifier() != null) {
      accesses.add(accessOf(name, member.identifier().getText(), parts, 2));
    }
    return accesses;
  }

  private static Access accessOf(
      final String qualifier, final String name, final List<ParseTree> parts, final int next) {
    return parts.size() > next
            && parts.get(next) instanceof Swift5Parser.Function_call_suffixContext call
        ? Access.call(qualifier, name, countArguments(call))
        : Access.of(qualifier, name);
  }

  private static int countArguments(final Swift5Parser.Function_call_suffixContext call) {
    int arguments = 0;
    if (call.function_call_argument_clause() != null
        && call.function_call_argument_clause().function_call_argument_list() != null) {
      arguments +=
          call.function_call_argument_clause()
              .function_call_argument_list()
              .function_call_argument()
              .size();
    }
    if (call.trailing_closures() != null) {
      arguments++;
      if (call.trailing_closures().labeled_trailing_closures() != null) {
        arguments +=
            call.trailing_closures().labeled_trailing_closures().labeled_trailing_closure().size();
      }
    }
    return arguments;
  }

  private static List<String> namesOf(final Swift5Parser.PatternContext pattern) {
    return Trees.find(pattern, Swift5Parser.Identifier_patternContext.class).stream()
        .map(ParserRuleContext::getText)
        // "let self = self" still is the own object.
        .filter(name -> !SELF.equals(name))
        .toList();
  }
}
