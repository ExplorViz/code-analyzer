package net.explorviz.code.analysis.metrics.cpp;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Members of the classes, structs and unions of C++, including the methods that are defined outside
 * of the class in the same file.
 *
 * <p>The grammar parses some statements as declarations, e.g. {@code x = 1;} and {@code f(x);}.
 * Those declare no variables, but use the names.
 */
public final class CppMembers implements MemberGrammar {

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    if (!(context instanceof CPP14Parser.ClassSpecifierContext type)) {
      return null;
    }
    final String name = nameOf(type);
    final TypeMembers members = new TypeMembers(name);
    if (type.memberSpecification() != null) {
      final List<Declared> declared = new ArrayList<>();
      for (final CPP14Parser.MemberDeclarationContext member :
          type.memberSpecification().memberDeclaration()) {
        addMember(members, name, member, declared);
      }
      if (name != null) {
        addOutOfClassDefinitions(members, name, type, declared);
      }
      declared.forEach(method -> members.addMethod(method.toMethod()));
    }
    return members;
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case CPP14Parser.IdExpressionContext ctx -> nameAccessesOf(ctx);
      case CPP14Parser.ClassNameContext ctx when ctx.Identifier() != null -> typeOrNameOf(ctx);
      default -> List.of();
    };
  }

  @Override
  public boolean opensScope(final ParserRuleContext context) {
    return context instanceof CPP14Parser.CompoundStatementContext
        || context instanceof CPP14Parser.IterationStatementContext
        || context instanceof CPP14Parser.SelectionStatementContext
        || context instanceof CPP14Parser.HandlerContext
        || context instanceof CPP14Parser.LambdaExpressionContext;
  }

  @Override
  public List<String> declaredVariables(final ParserRuleContext context) {
    return switch (context) {
      case CPP14Parser.InitDeclaratorContext ctx when isDeclaration(ctx) ->
          CppDeclarators.namesOf(ctx.declarator());
      case CPP14Parser.ParameterDeclarationContext ctx -> CppDeclarators.namesOf(ctx.declarator());
      case CPP14Parser.ForRangeDeclarationContext ctx -> CppDeclarators.namesOf(ctx.declarator());
      case CPP14Parser.ConditionContext ctx -> CppDeclarators.namesOf(ctx.declarator());
      case CPP14Parser.ExceptionDeclarationContext ctx -> CppDeclarators.namesOf(ctx.declarator());
      case CPP14Parser.InitCaptureContext ctx when ctx.Identifier() != null ->
          List.of(ctx.Identifier().getText());
      default -> List.of();
    };
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof CPP14Parser.AttributeSpecifierSeqContext
        || context instanceof CPP14Parser.NestedNameSpecifierContext;
  }

  private static String nameOf(final CPP14Parser.ClassSpecifierContext type) {
    return type.classHead() == null || type.classHead().classHeadName() == null
        ? null
        : Trees.firstTokens(type.classHead().classHeadName().className(), 1).getFirst();
  }

  private static void addMember(
      final TypeMembers members,
      final String typeName,
      final CPP14Parser.MemberDeclarationContext member,
      final List<Declared> declared) {
    final CPP14Parser.FunctionDefinitionContext function = definitionOf(member);
    if (function != null) {
      final String name = CppDeclarators.nameOf(function.declarator());
      if (isMethod(name, typeName) && !isFriend(function.declSpecifierSeq())) {
        declared.add(new Declared(name, function.declarator(), function));
      }
    } else if (member.memberDeclaratorList() != null && !isFriend(member.declSpecifierSeq())) {
      final boolean isConstant = isConstant(member.declSpecifierSeq());
      for (final CPP14Parser.MemberDeclaratorContext declarator :
          member.memberDeclaratorList().memberDeclarator()) {
        final String name = CppDeclarators.nameOf(declarator.declarator());
        if (CppDeclarators.isFunction(declarator.declarator())) {
          if (isMethod(name, typeName)) {
            declared.add(new Declared(name, declarator.declarator(), null));
          }
        } else if (name != null) {
          members.addField(name, isConstant);
        } else if (declarator.Identifier() != null) {
          members.addField(declarator.Identifier().getText(), isConstant);
        }
      }
    }
  }

  private static CPP14Parser.FunctionDefinitionContext definitionOf(
      final CPP14Parser.MemberDeclarationContext member) {
    if (member.functionDefinition() != null) {
      return member.functionDefinition();
    }
    return member.templateDeclaration() == null
            || member.templateDeclaration().declaration() == null
        ? null
        : member.templateDeclaration().declaration().functionDefinition();
  }

  private static void addOutOfClassDefinitions(
      final TypeMembers members,
      final String typeName,
      final CPP14Parser.ClassSpecifierContext type,
      final List<Declared> declared) {
    for (final CPP14Parser.FunctionDefinitionContext function :
        Trees.find(
            Trees.root(type),
            CPP14Parser.FunctionDefinitionContext.class,
            CPP14Parser.ClassSpecifierContext.class,
            CPP14Parser.CompoundStatementContext.class)) {
      final String name = CppDeclarators.nameOf(function.declarator());
      if (!isMethod(name, typeName)
          || !typeName.equals(CppDeclarators.qualifierOf(function.declarator()))) {
        continue;
      }
      final Declared definition = new Declared(name, function.declarator(), function);
      // The definition replaces its declaration, which may have default arguments.
      final Declared declaration =
          declared.stream().filter(definition::isDefinitionOf).findFirst().orElse(null);
      declared.remove(declaration);
      members.addMethod(
          Method.of(
              name,
              Access.SELF,
              declaration == null ? definition.arguments() : declaration.arguments(),
              definition.hasCode(),
              function.declarator(),
              function.functionBody()));
    }
  }

  /** Whether the function is neither a constructor nor a destructor. */
  private static boolean isMethod(final String name, final String typeName) {
    return name != null && !name.equals(typeName) && !name.startsWith("~");
  }

  private static boolean isFriend(final CPP14Parser.DeclSpecifierSeqContext specifiers) {
    return specifiers != null
        && specifiers.declSpecifier().stream().anyMatch(s -> s.Friend() != null);
  }

  private static boolean isConstant(final CPP14Parser.DeclSpecifierSeqContext specifiers) {
    final List<String> texts =
        specifiers == null
            ? List.of()
            : specifiers.declSpecifier().stream().map(ParserRuleContext::getText).toList();
    return texts.contains("constexpr") || texts.contains("static") && texts.contains("const");
  }

  /** Whether the declarator declares a variable instead of being a misinterpreted statement. */
  private static boolean isDeclaration(final CPP14Parser.InitDeclaratorContext ctx) {
    return ctx.getParent().getParent() instanceof CPP14Parser.SimpleDeclarationContext declaration
        && declaration.declSpecifierSeq() != null
        && ctx.getStart() != null
        && ctx.getStart().getType() != CPP14Parser.LeftParen
        && CppDeclarators.qualifierOf(ctx.declarator()) == null;
  }

  private static List<Access> nameAccessesOf(final CPP14Parser.IdExpressionContext ctx) {
    final String name = CppDeclarators.nameOf(ctx);
    if (name == null) {
      return List.of();
    }
    final String qualifier = CppDeclarators.qualifierOf(ctx);
    return switch (ctx.getParent()) {
      case CPP14Parser.DeclaratorIdContext declarator ->
          declaratorAccessesOf(declarator, qualifier, name);
      case CPP14Parser.PostfixExpressionContext member when member.postfixExpression() != null ->
          isSelf(member.postfixExpression())
              ? List.of(accessOf(Access.SELF, name, member))
              : List.of();
      case CPP14Parser.PrimaryExpressionContext primary ->
          List.of(accessOf(qualifier, name, primary.getParent()));
      case null, default -> List.of(Access.of(qualifier, name));
    };
  }

  /** Returns the name that a misinterpreted statement uses. */
  private static List<Access> declaratorAccessesOf(
      final CPP14Parser.DeclaratorIdContext declarator, final String qualifier, final String name) {
    final ParserRuleContext owner =
        Trees.enclosing(
            declarator,
            CPP14Parser.InitDeclaratorContext.class,
            CPP14Parser.ParameterDeclarationContext.class,
            CPP14Parser.ForRangeDeclarationContext.class,
            CPP14Parser.ConditionContext.class,
            CPP14Parser.ExceptionDeclarationContext.class,
            CPP14Parser.FunctionDefinitionContext.class);
    if (!(owner instanceof CPP14Parser.InitDeclaratorContext init) || isDeclaration(init)) {
      return List.of();
    }
    final int arguments = CppDeclarators.countArguments(init);
    return List.of(new Access(qualifier, name, arguments));
  }

  /**
   * Returns the use of a name that the grammar takes for a type, as in {@code f(x);} and {@code T
   * y(x);}. Types do no harm since they are no members.
   */
  private static List<Access> typeOrNameOf(final CPP14Parser.ClassNameContext ctx) {
    final String name = ctx.Identifier().getText();
    final ParserRuleContext declaration =
        Trees.enclosing(
            ctx,
            CPP14Parser.SimpleDeclarationContext.class,
            CPP14Parser.ParameterDeclarationContext.class,
            CPP14Parser.TemplateArgumentContext.class);
    final boolean isCall =
        declaration instanceof CPP14Parser.SimpleDeclarationContext simple
            && simple.initDeclaratorList() != null
            && simple.initDeclaratorList().getStart().getType() == CPP14Parser.LeftParen;
    return List.of(isCall ? Access.call(null, name, 1) : Access.of(null, name));
  }

  private static Access accessOf(
      final String qualifier, final String name, final ParserRuleContext expression) {
    if (expression instanceof CPP14Parser.PostfixExpressionContext
        && expression.getParent() instanceof CPP14Parser.PostfixExpressionContext call
        && call.LeftParen() != null
        && call.postfixExpression() == expression) {
      final int arguments =
          call.expressionList() == null || call.expressionList().initializerList() == null
              ? 0
              : call.expressionList().initializerList().initializerClause().size();
      return Access.call(qualifier, name, arguments);
    }
    return Access.of(qualifier, name);
  }

  private static boolean isSelf(final CPP14Parser.PostfixExpressionContext object) {
    final List<String> tokens = Trees.firstTokens(object, 5);
    return tokens.equals(List.of("this")) || tokens.equals(List.of("(", "*", "this", ")"));
  }

  /** Method that is declared inside of the class, {@code definition} may be {@code null}. */
  private record Declared(
      String name,
      CPP14Parser.DeclaratorContext declarator,
      CPP14Parser.FunctionDefinitionContext definition) {

    private Method.Arguments arguments() {
      return CppDeclarators.argumentsOf(declarator);
    }

    private boolean isDefinitionOf(final Declared declaration) {
      return declaration.definition == null
          && declaration.name.equals(name)
          && declaration.arguments().max() == arguments().max();
    }

    private boolean hasCode() {
      return definition != null
          && definition.functionBody() != null
          && !Trees.find(definition.functionBody(), CPP14Parser.StatementContext.class).isEmpty();
    }

    private Method toMethod() {
      return Method.of(
          name,
          Access.SELF,
          arguments(),
          hasCode(),
          definition == null ? null : definition.declarator(),
          definition == null ? null : definition.functionBody());
    }
  }
}
