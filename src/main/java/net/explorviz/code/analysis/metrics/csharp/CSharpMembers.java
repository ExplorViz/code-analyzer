package net.explorviz.code.analysis.metrics.csharp;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Members of the classes, structs and interfaces of C# without operators. */
public final class CSharpMembers implements MemberGrammar {

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.Class_declarationContext ctx when ctx.class_body() != null ->
          typeMembersOf(ctx.identifier(), ctx.class_body().class_member_declaration());
      case CSharpParser.Struct_declarationContext ctx when ctx.struct_body() != null ->
          typeMembersOf(ctx.identifier(), ctx.struct_body().struct_member_declaration());
      case CSharpParser.Interface_declarationContext ctx when ctx.interface_body() != null ->
          typeMembersOf(ctx.identifier(), ctx.interface_body().interface_member_declaration());
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.Simple_nameContext ctx when ctx.identifier() != null ->
          List.of(accessOf(null, ctx.identifier(), ctx.getParent()));
      case CSharpParser.Primary_expressionContext ctx -> memberOf(ctx, 1);
      case CSharpParser.Member_accessContext ctx -> memberOf(ctx, 1);
      case CSharpParser.Null_conditional_member_accessContext ctx -> memberOf(ctx, 2);
      default -> List.of();
    };
  }

  @Override
  public boolean opensScope(final ParserRuleContext context) {
    return context instanceof CSharpParser.BlockContext
        || context instanceof CSharpParser.For_statementContext
        || context instanceof CSharpParser.Foreach_statementContext
        || context instanceof CSharpParser.Using_statementContext
        || context instanceof CSharpParser.Fixed_statementContext
        || context instanceof CSharpParser.Switch_blockContext
        || context instanceof CSharpParser.Switch_expression_armContext
        || context instanceof CSharpParser.Specific_catch_clauseContext
        || context instanceof CSharpParser.Lambda_expressionContext
        || context instanceof CSharpParser.Anonymous_method_expressionContext
        || context instanceof CSharpParser.Local_function_declarationContext
        || context instanceof CSharpParser.Query_expressionContext;
  }

  @Override
  public List<String> scopeVariables(final ParserRuleContext context) {
    return context instanceof CSharpParser.Foreach_statementContext loop
        ? namesOf(loop.identifier())
        : List.of();
  }

  @Override
  public List<String> declaredVariables(final ParserRuleContext context) {
    return switch (context) {
      case CSharpParser.Local_variable_declaratorContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Constant_declaratorContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Fixed_pointer_declaratorContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Declaration_expressionContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Deconstruction_elementContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Single_variable_designationContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Fixed_parameterContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Parameter_arrayContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Explicit_anonymous_function_parameterContext ctx ->
          namesOf(ctx.identifier());
      case CSharpParser.Implicit_anonymous_function_parameterContext ctx ->
          namesOf(ctx.identifier());
      case CSharpParser.Exception_specifierContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.From_clauseContext ctx -> namesOf(ctx.identifier());
      case CSharpParser.Let_clauseContext ctx -> namesOf(ctx.identifier());
      default -> List.of();
    };
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof CSharpParser.AttributesContext;
  }

  private static TypeMembers typeMembersOf(
      final CSharpParser.IdentifierContext name,
      final List<? extends ParserRuleContext> declarations) {
    final TypeMembers members = new TypeMembers(Trees.text(name));
    for (final ParserRuleContext declaration : declarations) {
      final ParseTree member = declaration.getChild(0);
      if (member != null) {
        addMember(members, member);
      }
    }
    return members;
  }

  private static void addMember(final TypeMembers members, final ParseTree member) {
    switch (member) {
      case CSharpParser.Field_declarationContext ctx -> {
        final List<String> modifiers =
            ctx.field_modifier().stream().map(ParserRuleContext::getText).toList();
        final boolean isConstant = modifiers.contains("static") && modifiers.contains("readonly");
        addFields(members, ctx.variable_declarators(), isConstant);
      }
      case CSharpParser.Constant_declarationContext ctx when ctx.constant_declarators() != null ->
          ctx.constant_declarators().constant_declarator().stream()
              .flatMap(declarator -> namesOf(declarator.identifier()).stream())
              .forEach(name -> members.addField(name, true));
      case CSharpParser.Event_declarationContext ctx ->
          addFields(members, ctx.variable_declarators(), false);
      case CSharpParser.Property_declarationContext ctx -> addProperty(members, ctx);
      case CSharpParser.Indexer_declarationContext ctx -> addIndexer(members, ctx);
      case CSharpParser.Method_declarationContext ctx -> addMethod(members, ctx);
      default -> {
        // Constructors, finalizers, operators and nested types
      }
    }
  }

  private static void addFields(
      final TypeMembers members,
      final CSharpParser.Variable_declaratorsContext declarators,
      final boolean isConstant) {
    if (declarators != null) {
      declarators.variable_declarator().stream()
          .flatMap(declarator -> namesOf(declarator.identifier()).stream())
          .forEach(name -> members.addField(name, isConstant));
    }
  }

  private static void addProperty(
      final TypeMembers members, final CSharpParser.Property_declarationContext property) {
    if (property.member_name() == null || property.member_name().identifier() == null) {
      return;
    }
    final String name = property.member_name().identifier().getText();
    final ParserRuleContext body =
        property.property_body() == null ? property.ref_property_body() : property.property_body();
    if (hasCode(body)) {
      members.addMethod(Method.property(name, true, body));
    } else {
      members.addField(name);
    }
  }

  private static void addIndexer(
      final TypeMembers members, final CSharpParser.Indexer_declarationContext indexer) {
    final ParserRuleContext body =
        indexer.indexer_body() == null ? indexer.ref_indexer_body() : indexer.indexer_body();
    if (hasCode(body)) {
      members.addMethod(Method.property("this[]", true, indexer.indexer_declarator(), body));
    }
  }

  /** Whether the body of a property has code, in contrast to {@code { get; set; }}. */
  private static boolean hasCode(final ParserRuleContext body) {
    return body != null
        && (!Trees.find(body, CSharpParser.BlockContext.class).isEmpty()
            || Trees.tokens(body).contains("=>"));
  }

  private static void addMethod(
      final TypeMembers members, final CSharpParser.Method_declarationContext method) {
    final CSharpParser.Method_headerContext header = method.method_header();
    if (header == null
        || header.member_name() == null
        || header.member_name().identifier() == null) {
      return;
    }
    final ParserRuleContext body =
        method.method_body() == null ? method.ref_method_body() : method.method_body();
    members.addMethod(
        Method.of(
            header.member_name().identifier().getText(),
            Access.SELF,
            argumentsOf(header.parameter_list()),
            body != null && !";".equals(body.getText()) && !"{}".equals(body.getText()),
            header.parameter_list(),
            body));
  }

  private static Method.Arguments argumentsOf(final CSharpParser.Parameter_listContext parameters) {
    if (parameters == null) {
      return Method.Arguments.of(0, 0, false);
    }
    int required = 0;
    int optional = 0;
    if (parameters.fixed_parameters() != null) {
      for (final CSharpParser.Fixed_parameterContext parameter :
          parameters.fixed_parameters().fixed_parameter()) {
        if (parameter.default_argument() == null) {
          required++;
        } else {
          optional++;
        }
      }
    }
    return Method.Arguments.of(required, optional, parameters.parameter_array() != null);
  }

  /** Returns the member in {@code object.member}, where the operator has the given length. */
  private static List<Access> memberOf(final ParserRuleContext context, final int operatorTokens) {
    if (context.getChildCount() < operatorTokens + 2
        || !(context.getChild(0) instanceof CSharpParser.Primary_expressionContext object)
        || !".".equals(context.getChild(operatorTokens).getText())
        || !(context.getChild(operatorTokens + 1)
            instanceof CSharpParser.IdentifierContext member)) {
      return List.of();
    }
    final boolean isName = object.simple_name() != null || object.this_access() != null;
    return isName ? List.of(accessOf(object.getText(), member, context)) : List.of();
  }

  /**
   * Returns the access of a name.
   *
   * @param qualifier the qualifier or {@code null}
   * @param name the name
   * @param expression the expression that consists of the name and its qualifier
   */
  private static Access accessOf(
      final String qualifier,
      final CSharpParser.IdentifierContext name,
      final ParserRuleContext expression) {
    final ParserRuleContext call = expression.getParent();
    final boolean isCall =
        call != null
            && call.getChildCount() > 1
            && call.getChild(0) == expression
            && "(".equals(call.getChild(1).getText());
    if (!isCall) {
      return Access.of(qualifier, name.getText());
    }
    final CSharpParser.Argument_listContext arguments =
        call.getRuleContext(CSharpParser.Argument_listContext.class, 0);
    return Access.call(
        qualifier, name.getText(), arguments == null ? 0 : arguments.argument().size());
  }

  private static List<String> namesOf(final CSharpParser.IdentifierContext identifier) {
    return identifier == null ? List.of() : List.of(identifier.getText());
  }
}
