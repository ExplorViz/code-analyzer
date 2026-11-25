package net.explorviz.code.analysis.metrics.cpp;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import org.antlr.v4.runtime.ParserRuleContext;

/** Names and parameters of the declarators of C++. */
/* package */ final class CppDeclarators {

  private CppDeclarators() {}

  /** Returns the unqualified name or {@code null} if there is none. */
  /* package */ static String nameOf(final CPP14Parser.DeclaratorContext declarator) {
    return nameOf(idOf(declarator));
  }

  /** Returns the unqualified name or {@code null} if there is none. */
  /* package */ static String nameOf(final CPP14Parser.IdExpressionContext id) {
    if (id == null) {
      return null;
    }
    final CPP14Parser.UnqualifiedIdContext unqualified =
        id.qualifiedId() == null ? id.unqualifiedId() : id.qualifiedId().unqualifiedId();
    if (unqualified == null) {
      return null;
    }
    return unqualified.templateId() == null
        ? unqualified.getText()
        : Trees.firstTokens(unqualified.templateId(), 1).getFirst();
  }

  /** Returns the name, if any. */
  /* package */ static List<String> namesOf(final CPP14Parser.DeclaratorContext declarator) {
    final String name = nameOf(declarator);
    return name == null ? List.of() : List.of(name);
  }

  /** Returns the last part of the qualifier or {@code null} if there is none. */
  /* package */ static String qualifierOf(final CPP14Parser.DeclaratorContext declarator) {
    return qualifierOf(idOf(declarator));
  }

  /** Returns the last part of the qualifier or {@code null} if there is none. */
  /* package */ static String qualifierOf(final CPP14Parser.IdExpressionContext id) {
    if (id == null || id.qualifiedId() == null || id.qualifiedId().nestedNameSpecifier() == null) {
      return null;
    }
    final CPP14Parser.NestedNameSpecifierContext specifier = id.qualifiedId().nestedNameSpecifier();
    // The last child is the operator.
    final int last = Math.max(0, specifier.getChildCount() - 2);
    final String qualifier = specifier.getChild(last).getText();
    // "A::f();" is parsed as the declaration of "::f" with the type "A".
    return "::".equals(qualifier) ? typeBefore(id) : withoutTemplateArguments(qualifier);
  }

  /** Whether the declarator declares a function instead of, e.g., a pointer to a function. */
  /* package */ static boolean isFunction(final CPP14Parser.DeclaratorContext declarator) {
    final CPP14Parser.IdExpressionContext id = idOf(declarator);
    return id != null && parametersAfter(id) != null;
  }

  /** Returns the numbers of arguments that the declared function accepts. */
  /* package */ static Method.Arguments argumentsOf(
      final CPP14Parser.DeclaratorContext declarator) {
    final CPP14Parser.ParametersAndQualifiersContext parameters = parametersAfter(idOf(declarator));
    if (parameters == null || parameters.parameterDeclarationClause() == null) {
      return Method.Arguments.of(0, 0, false);
    }
    final CPP14Parser.ParameterDeclarationClauseContext clause =
        parameters.parameterDeclarationClause();
    final List<CPP14Parser.ParameterDeclarationContext> declared =
        clause.parameterDeclarationList().parameterDeclaration();
    final int optional = (int) declared.stream().filter(p -> p.Assign() != null).count();
    final boolean isVoid = declared.size() == 1 && "void".equals(declared.getFirst().getText());
    final boolean isVariadic = clause.Ellipsis() != null || Trees.tokens(clause).contains("...");
    return Method.Arguments.of(isVoid ? 0 : declared.size() - optional, optional, isVariadic);
  }

  /** Returns the number of arguments if the declarator is actually a call. */
  /* package */ static int countArguments(final CPP14Parser.InitDeclaratorContext init) {
    final CPP14Parser.ParametersAndQualifiersContext parameters =
        parametersAfter(idOf(init.declarator()));
    if (parameters != null) {
      return parameters.parameterDeclarationClause() == null
          ? 0
          : parameters
              .parameterDeclarationClause()
              .parameterDeclarationList()
              .parameterDeclaration()
              .size();
    }
    if (init.initializer() != null && init.initializer().LeftParen() != null) {
      return init.initializer().expressionList() == null
          ? 0
          : init.initializer().expressionList().initializerList().initializerClause().size();
    }
    return Access.NO_CALL;
  }

  private static CPP14Parser.IdExpressionContext idOf(
      final CPP14Parser.DeclaratorContext declarator) {
    final List<CPP14Parser.DeclaratorIdContext> ids =
        Trees.find(
            declarator,
            CPP14Parser.DeclaratorIdContext.class,
            CPP14Parser.ParametersAndQualifiersContext.class);
    return ids.isEmpty() ? null : ids.getFirst().idExpression();
  }

  /** Returns the parameters that directly follow the name. */
  private static CPP14Parser.ParametersAndQualifiersContext parametersAfter(
      final CPP14Parser.IdExpressionContext id) {
    if (id == null || id.getParent() == null) {
      return null;
    }
    final ParserRuleContext named = id.getParent().getParent();
    return switch (named == null ? null : named.getParent()) {
      case CPP14Parser.NoPointerDeclaratorContext ctx -> ctx.parametersAndQualifiers();
      case CPP14Parser.DeclaratorContext ctx -> ctx.parametersAndQualifiers();
      case null, default -> null;
    };
  }

  private static String typeBefore(final CPP14Parser.IdExpressionContext id) {
    final ParserRuleContext declaration =
        Trees.enclosing(
            id,
            CPP14Parser.SimpleDeclarationContext.class,
            CPP14Parser.FunctionDefinitionContext.class);
    final CPP14Parser.DeclSpecifierSeqContext specifiers =
        declaration == null
            ? null
            : declaration.getRuleContext(CPP14Parser.DeclSpecifierSeqContext.class, 0);
    return specifiers == null || specifiers.declSpecifier().isEmpty()
        ? "::"
        : withoutTemplateArguments(specifiers.declSpecifier().getLast().getText());
  }

  private static String withoutTemplateArguments(final String name) {
    final int arguments = name.indexOf('<');
    return arguments < 0 ? name : name.substring(0, arguments);
  }
}
