package net.explorviz.code.analysis.metrics.typescript;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Members of the classes of TypeScript and JavaScript. Since fields need no declaration in
 * JavaScript, all {@code this.x} that are neither called nor methods are fields.
 */
public final class TypeScriptMembers implements MemberGrammar {

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case TypeScriptParser.ClassDeclarationContext ctx when ctx.classTail() != null ->
          classMembersOf(Trees.text(ctx.identifier()), ctx.classTail());
      case TypeScriptParser.ClassExpressionContext ctx when ctx.classTail() != null ->
          classMembersOf(Trees.text(ctx.identifier()), ctx.classTail());
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case TypeScriptParser.MemberDotExpressionContext ctx -> memberOf(ctx);
      case TypeScriptParser.OptionalMemberExpressionContext ctx -> memberOf(ctx);
      case TypeScriptParser.OptionalChainExpressionContext ctx -> memberOf(ctx);
      case TypeScriptParser.VariableDeclarationContext ctx when ctx.objectLiteral() != null ->
          destructuredMembersOf(ctx);
      default -> List.of();
    };
  }

  @Override
  public boolean rebindsSelf(final ParserRuleContext context) {
    return context instanceof TypeScriptParser.FunctionDeclarationContext
        || context instanceof TypeScriptParser.GeneratorFunctionDeclarationContext
        || context instanceof TypeScriptParser.GeneratorMethodContext
        || context instanceof TypeScriptParser.GetAccessorContext
        || context instanceof TypeScriptParser.SetAccessorContext
        || context instanceof TypeScriptParser.AnonymousFunctionContext function
            && function.functionBody() != null;
  }

  private TypeMembers classMembersOf(
      final String typeName, final TypeScriptParser.ClassTailContext body) {
    final TypeMembers members = new TypeMembers(typeName);
    for (final TypeScriptParser.ClassElementContext element : body.classElement()) {
      if (element.constructorDeclaration() != null) {
        addParameterFields(members, element.constructorDeclaration());
      } else if (element.propertyMemberDeclaration() != null) {
        addMember(members, element.propertyMemberDeclaration());
      }
    }
    addUndeclaredFields(members, body);
    return members;
  }

  private static void addParameterFields(
      final TypeMembers members, final TypeScriptParser.ConstructorDeclarationContext ctx) {
    if (ctx.formalParameterList() == null) {
      return;
    }
    for (final TypeScriptParser.FormalParameterArgContext parameter :
        ctx.formalParameterList().formalParameterArg()) {
      if (parameter.accessibilityModifier() != null && parameter.assignable() != null) {
        members.addField(parameter.assignable().getText());
      }
    }
  }

  private static void addMember(
      final TypeMembers members, final TypeScriptParser.PropertyMemberDeclarationContext member) {
    switch (member) {
      case TypeScriptParser.PropertyDeclarationExpressionContext ctx -> addProperty(members, ctx);
      case TypeScriptParser.MethodDeclarationExpressionContext ctx ->
          addMethod(members, ctx.classElementName(), ctx.callSignature(), ctx.functionBody());
      case TypeScriptParser.GetterSetterDeclarationExpressionContext ctx ->
          addAccessor(members, ctx);
      default -> {
        // Abstract members have no code.
      }
    }
  }

  private static void addProperty(
      final TypeMembers members, final TypeScriptParser.PropertyDeclarationExpressionContext ctx) {
    if (ctx.classElementName() == null) {
      return;
    }
    final TypeScriptParser.AnonymousFunctionContext function = functionOf(ctx.initializer());
    if (function == null) {
      final TypeScriptParser.PropertyMemberBaseContext modifiers = ctx.propertyMemberBase();
      members.addField(
          nameOf(ctx.classElementName()),
          modifiers != null && modifiers.Static() != null && modifiers.ReadOnly() != null);
    } else if (function.arrowFunctionDeclaration() != null) {
      final TypeScriptParser.ArrowFunctionBodyContext body =
          function.arrowFunctionDeclaration().arrowFunctionBody();
      addMethod(
          members,
          ctx.classElementName(),
          function.arrowFunctionDeclaration().arrowFunctionParameters(),
          body == null || body.functionBody() == null ? body : body.functionBody());
    } else {
      addMethod(
          members, ctx.classElementName(), function.formalParameterList(), function.functionBody());
    }
  }

  private static TypeScriptParser.AnonymousFunctionContext functionOf(
      final TypeScriptParser.InitializerContext initializer) {
    return initializer != null
            && initializer.singleExpression()
                instanceof TypeScriptParser.FunctionExpressionContext expression
        ? expression.anonymousFunction()
        : null;
  }

  private static void addAccessor(
      final TypeMembers members,
      final TypeScriptParser.GetterSetterDeclarationExpressionContext ctx) {
    if (ctx.getAccessor() != null && ctx.getAccessor().getter() != null) {
      addMethod(
          members,
          ctx.getAccessor().getter().classElementName(),
          null,
          ctx.getAccessor().functionBody());
    } else if (ctx.setAccessor() != null && ctx.setAccessor().setter() != null) {
      addMethod(
          members,
          ctx.setAccessor().setter().classElementName(),
          ctx.setAccessor().formalParameterList(),
          ctx.setAccessor().functionBody());
    }
  }

  private static void addMethod(
      final TypeMembers members,
      final TypeScriptParser.ClassElementNameContext name,
      final ParserRuleContext parameters,
      final ParserRuleContext body) {
    if (name != null) {
      members.addMethod(
          Method.of(
              nameOf(name),
              Access.SELF,
              Method.Arguments.ANY,
              body != null && body.getChildCount() > 0,
              parameters,
              body));
    }
  }

  private void addUndeclaredFields(
      final TypeMembers members, final TypeScriptParser.ClassTailContext body) {
    final List<ParserRuleContext> code =
        Trees.find(
            body,
            ParserRuleContext.class,
            TypeScriptParser.ClassTailContext.class,
            TypeScriptParser.FunctionDeclarationContext.class);
    for (final ParserRuleContext context : code) {
      for (final Access access : accessesOf(context)) {
        if (Access.SELF.equals(access.qualifier())
            && access.arguments() == Access.NO_CALL
            && !members.declaresMethod(access.name())
            && !members.fieldNames().contains(access.name())) {
          members.addField(access.name());
        }
      }
    }
  }

  private static List<Access> memberOf(final ParserRuleContext context) {
    final List<String> tokens = new ArrayList<>(Trees.firstTokens(context, 5));
    if (tokens.size() > 1 && "!".equals(tokens.get(1))) {
      tokens.remove(1);
    }
    if (tokens.size() < 3 || !".".equals(tokens.get(1)) && !"?.".equals(tokens.get(1))) {
      return List.of();
    }
    final String name = "#".equals(tokens.get(2)) ? "#" + tokens.get(3) : tokens.get(2);
    return List.of(
        isCalled(context)
            ? Access.call(tokens.getFirst(), name, Access.UNKNOWN)
            : Access.of(tokens.getFirst(), name));
  }

  private static boolean isCalled(final ParserRuleContext context) {
    return context instanceof TypeScriptParser.OptionalMemberExpressionContext member
            && member.arguments() != null
        || context.getParent() instanceof TypeScriptParser.ArgumentsExpressionContext call
            && call.singleExpression() == context;
  }

  /** {@code const { x, y: z } = this} uses {@code x} and {@code y}. */
  private static List<Access> destructuredMembersOf(
      final TypeScriptParser.VariableDeclarationContext ctx) {
    final List<TypeScriptParser.SingleExpressionContext> values = ctx.singleExpression();
    if (values.isEmpty() || !(values.getLast() instanceof TypeScriptParser.ThisExpressionContext)) {
      return List.of();
    }
    return ctx.objectLiteral().propertyAssignment().stream()
        .map(property -> Trees.firstTokens(property, 1))
        .filter(tokens -> !tokens.isEmpty())
        .map(tokens -> Access.of(Access.SELF, tokens.getFirst()))
        .toList();
  }

  private static String nameOf(final TypeScriptParser.ClassElementNameContext name) {
    return name.getText();
  }
}
