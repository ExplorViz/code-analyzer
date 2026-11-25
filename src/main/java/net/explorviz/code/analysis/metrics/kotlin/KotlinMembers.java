package net.explorviz.code.analysis.metrics.kotlin;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;

/** Members of the classes, interfaces and objects of Kotlin. */
public final class KotlinMembers implements MemberGrammar {

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case KotlinParser.ClassDeclarationContext ctx -> {
        final TypeMembers members = new TypeMembers(Trees.text(ctx.simpleIdentifier()));
        addConstructor(members, ctx.primaryConstructor());
        addBody(members, ctx.classBody());
        if (ctx.enumClassBody() != null) {
          addMembers(members, ctx.enumClassBody().classMemberDeclaration());
        }
        yield members;
      }
      case KotlinParser.ObjectDeclarationContext ctx -> {
        final TypeMembers members = new TypeMembers(Trees.text(ctx.simpleIdentifier()));
        addBody(members, ctx.classBody());
        yield members;
      }
      case KotlinParser.CompanionObjectContext ctx -> {
        final TypeMembers members = new TypeMembers(Trees.text(ctx.simpleIdentifier()));
        addBody(members, ctx.classBody());
        yield members;
      }
      case KotlinParser.ObjectLiteralContext ctx -> {
        final TypeMembers members = new TypeMembers(null);
        addBody(members, ctx.classBody());
        yield members;
      }
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case KotlinParser.PostfixUnaryExpressionContext ctx
          when !(ctx.getParent() instanceof KotlinParser.PostfixUnaryOperationContext) ->
          nameAccessesOf(ctx);
      case KotlinParser.CallableReferenceContext ctx when ctx.identifier() != null ->
          List.of(Access.of(Trees.text(ctx.userType()), ctx.identifier().getText()));
      case KotlinParser.LineStringContentContext ctx when ctx.LineStrRef() != null ->
          List.of(Access.of(null, ctx.getText().substring(1)));
      case KotlinParser.MultiLineStringContentContext ctx when ctx.MultiLineStrRef() != null ->
          List.of(Access.of(null, ctx.getText().substring(1)));
      default -> List.of();
    };
  }

  @Override
  public boolean opensScope(final ParserRuleContext context) {
    return context instanceof KotlinParser.BlockContext
        || context instanceof KotlinParser.FunctionDeclarationContext
        || context instanceof KotlinParser.FunctionLiteralContext
        || context instanceof KotlinParser.ControlStructureBodyContext
        || context instanceof KotlinParser.ForExpressionContext
        || context instanceof KotlinParser.CatchBlockContext
        || context instanceof KotlinParser.SetterContext;
  }

  @Override
  public List<String> scopeVariables(final ParserRuleContext context) {
    return switch (context) {
      case KotlinParser.CatchBlockContext ctx -> namesOf(ctx.simpleIdentifier());
      case KotlinParser.SetterContext ctx -> namesOf(ctx.simpleIdentifier());
      default -> List.of();
    };
  }

  @Override
  public List<String> declaredVariables(final ParserRuleContext context) {
    return switch (context) {
      // Not known in the initializer, hence declared with the property.
      case KotlinParser.VariableDeclarationContext ctx when isProperty(ctx) -> List.of();
      case KotlinParser.VariableDeclarationContext ctx -> namesOf(ctx.simpleIdentifier());
      case KotlinParser.PropertyDeclarationContext ctx ->
          variablesOf(ctx).stream().flatMap(v -> namesOf(v.simpleIdentifier()).stream()).toList();
      case KotlinParser.ParameterContext ctx -> namesOf(ctx.simpleIdentifier());
      default -> List.of();
    };
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof KotlinParser.AnnotationsContext;
  }

  private static List<Access> nameAccessesOf(final KotlinParser.PostfixUnaryExpressionContext ctx) {
    if (ctx.atomicExpression() == null) {
      return List.of();
    }
    final KotlinParser.AtomicExpressionContext atom = ctx.atomicExpression();
    final List<KotlinParser.PostfixUnaryOperationContext> operations = ctx.postfixUnaryOperation();
    if (atom.thisExpression() != null) {
      return qualifiedAccessOf(Access.SELF, operations);
    }
    if (atom.simpleIdentifier() == null) {
      return List.of();
    }
    final String name = atom.simpleIdentifier().getText();
    final List<Access> qualified = qualifiedAccessOf(name, operations);
    return qualified.isEmpty()
        ? List.of(accessOf(null, name, operations))
        : List.of(accessOf(null, name, operations), qualified.getFirst());
  }

  private static List<Access> qualifiedAccessOf(
      final String qualifier, final List<KotlinParser.PostfixUnaryOperationContext> operations) {
    if (operations.isEmpty()) {
      return List.of();
    }
    final KotlinParser.PostfixUnaryExpressionContext member =
        operations.getFirst().postfixUnaryExpression();
    if (member == null
        || member.atomicExpression() == null
        || member.atomicExpression().simpleIdentifier() == null) {
      return List.of();
    }
    return List.of(
        accessOf(
            qualifier,
            member.atomicExpression().simpleIdentifier().getText(),
            member.postfixUnaryOperation()));
  }

  private static Access accessOf(
      final String qualifier,
      final String name,
      final List<KotlinParser.PostfixUnaryOperationContext> operations) {
    final KotlinParser.CallSuffixContext call =
        operations.isEmpty() ? null : operations.getFirst().callSuffix();
    if (call == null) {
      return Access.of(qualifier, name);
    }
    final int arguments =
        call.valueArguments() == null ? 0 : call.valueArguments().valueArgument().size();
    return Access.call(qualifier, name, arguments + call.annotatedLambda().size());
  }

  private static void addConstructor(
      final TypeMembers members, final KotlinParser.PrimaryConstructorContext constructor) {
    if (constructor == null || constructor.classParameters() == null) {
      return;
    }
    for (final KotlinParser.ClassParameterContext parameter :
        constructor.classParameters().classParameter()) {
      if (parameter.VAL() != null || parameter.VAR() != null) {
        namesOf(parameter.simpleIdentifier()).forEach(members::addField);
      }
    }
  }

  private static void addBody(final TypeMembers members, final KotlinParser.ClassBodyContext body) {
    if (body != null) {
      addMembers(members, body.classMemberDeclaration());
    }
  }

  private static void addMembers(
      final TypeMembers members,
      final List<KotlinParser.ClassMemberDeclarationContext> declarations) {
    for (final KotlinParser.ClassMemberDeclarationContext declaration : declarations) {
      if (declaration.propertyDeclaration() != null) {
        addProperty(members, declaration.propertyDeclaration());
      } else if (declaration.functionDeclaration() != null) {
        addMethod(members, declaration.functionDeclaration());
      }
    }
  }

  private static void addProperty(
      final TypeMembers members, final KotlinParser.PropertyDeclarationContext property) {
    final boolean isConstant =
        property.modifierList() != null && Trees.tokens(property.modifierList()).contains("const");
    final boolean hasValue = property.ASSIGNMENT() != null || property.BY() != null;
    final KotlinParser.GetterContext getter = property.getter();
    final KotlinParser.SetterContext setter = property.setter();
    final boolean hasCode =
        getter != null && (getter.block() != null || getter.expression() != null)
            || setter != null && setter.functionBody() != null;
    for (final KotlinParser.VariableDeclarationContext variable : variablesOf(property)) {
      for (final String name : namesOf(variable.simpleIdentifier())) {
        if (hasValue || !hasCode) {
          members.addField(name, isConstant);
        }
        if (hasCode) {
          members.addMethod(Method.property(name, true, getter, setter));
        }
      }
    }
  }

  private static void addMethod(
      final TypeMembers members, final KotlinParser.FunctionDeclarationContext function) {
    if (function.identifier() == null) {
      return;
    }
    final KotlinParser.FunctionBodyContext body = function.functionBody();
    final boolean hasCode =
        body != null
            && (body.block() == null
                || body.block().statements() != null
                    && body.block().statements().statement().stream()
                        .anyMatch(statement -> statement.getChildCount() > 0));
    members.addMethod(
        Method.of(
            function.identifier().getText(),
            Access.SELF,
            argumentsOf(function.functionValueParameters()),
            hasCode,
            function.functionValueParameters(),
            body));
  }

  private static Method.Arguments argumentsOf(
      final KotlinParser.FunctionValueParametersContext parameters) {
    if (parameters == null) {
      return Method.Arguments.ANY;
    }
    int required = 0;
    int optional = 0;
    boolean isVariadic = false;
    for (final KotlinParser.FunctionValueParameterContext parameter :
        parameters.functionValueParameter()) {
      final boolean isVararg =
          parameter.modifierList() != null
              && Trees.tokens(parameter.modifierList()).contains("vararg");
      isVariadic |= isVararg;
      if (isVararg || parameter.ASSIGNMENT() != null) {
        optional++;
      } else {
        required++;
      }
    }
    return Method.Arguments.of(required, optional, isVariadic);
  }

  private static boolean isProperty(final KotlinParser.VariableDeclarationContext variable) {
    return variable.getParent() instanceof KotlinParser.PropertyDeclarationContext
        || variable.getParent() instanceof KotlinParser.MultiVariableDeclarationContext
            && variable.getParent().getParent() instanceof KotlinParser.PropertyDeclarationContext;
  }

  private static List<KotlinParser.VariableDeclarationContext> variablesOf(
      final KotlinParser.PropertyDeclarationContext property) {
    if (property.variableDeclaration() != null) {
      return List.of(property.variableDeclaration());
    }
    return property.multiVariableDeclaration() == null
        ? List.of()
        : property.multiVariableDeclaration().variableDeclaration();
  }

  private static List<String> namesOf(final KotlinParser.SimpleIdentifierContext identifier) {
    return identifier == null ? List.of() : List.of(identifier.getText());
  }
}
