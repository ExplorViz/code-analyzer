package net.explorviz.code.analysis.metrics.java.lcom4;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.metrics.java.JavaTreeWalker;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Collects the fields and methods of the own class that a method uses, including uses inside of
 * lambdas, local and anonymous classes.
 *
 * <p>Types are not resolved. Overloads are therefore told apart by the number of arguments only,
 * and members that are accessed through another instance of the class are not recognized.
 */
/* package */ final class MemberAccessListener extends ScopeListener {

  private final ClassMembers members;
  private final Set<String> usedFields = new HashSet<>();
  private final Set<Method> usedMethods = new HashSet<>();

  private MemberAccessListener(final ClassMembers members) {
    super();
    this.members = members;
  }

  /** Collects the members that the given method uses. */
  /* package */ static MemberAccessListener analyze(
      final Method method, final ClassMembers members) {
    final MemberAccessListener listener = new MemberAccessListener(members);
    JavaTreeWalker.walk(listener, method.parameters(), isIrrelevant());
    JavaTreeWalker.walk(listener, method.body(), isIrrelevant());
    return listener;
  }

  /* package */ Set<String> usedFields() {
    return usedFields;
  }

  /* package */ Set<Method> usedMethods() {
    return usedMethods;
  }

  @Override
  public void enterPrimaryExpression(final JavaParser.PrimaryExpressionContext ctx) {
    if (ctx.primary() != null) {
      useVariable(ctx.primary().identifier());
    }
  }

  @Override
  public void enterResource(final JavaParser.ResourceContext ctx) {
    if (ctx.qualifiedName() != null) {
      useVariable(ctx.qualifiedName().identifier(0));
    }
    super.enterResource(ctx);
  }

  @Override
  public void enterMemberReferenceExpression(
      final JavaParser.MemberReferenceExpressionContext ctx) {
    if (!isOwnClass(ctx.expression())) {
      return;
    }
    if (ctx.identifier() != null) {
      useField(ctx.identifier().getText());
    } else if (ctx.methodCall() != null) {
      useMethod(ctx.methodCall().identifier(), ctx.methodCall().arguments());
    } else if (ctx.explicitGenericInvocation() != null
        && ctx.explicitGenericInvocation().explicitGenericInvocationSuffix() != null) {
      final JavaParser.ExplicitGenericInvocationSuffixContext suffix =
          ctx.explicitGenericInvocation().explicitGenericInvocationSuffix();
      useMethod(suffix.identifier(), suffix.arguments());
    }
  }

  @Override
  public void enterMethodCallExpression(final JavaParser.MethodCallExpressionContext ctx) {
    if (ctx.methodCall() == null || ctx.methodCall().identifier() == null) {
      return;
    }
    if (!scopes.hidesMethod(ctx.methodCall().identifier().getText())) {
      useMethod(ctx.methodCall().identifier(), ctx.methodCall().arguments());
    }
  }

  @Override
  public void enterMethodReferenceExpression(
      final JavaParser.MethodReferenceExpressionContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }
    final boolean ownType =
        ctx.typeType() != null && ctx.typeType().getText().equals(members.typeName());
    if (ownType || isOwnClass(ctx.expression())) {
      final String name = ctx.identifier().getText();
      useMethods(name, method -> true);
    }
  }

  /** Whether the expression is {@code this}, {@code Own.this} or {@code Own}. */
  private boolean isOwnClass(final JavaParser.ExpressionContext ctx) {
    if (ctx instanceof JavaParser.PrimaryExpressionContext expression) {
      final JavaParser.PrimaryContext primary = expression.primary();
      if (primary == null) {
        return false;
      }
      if (primary.THIS() != null) {
        return !scopes.isInNestedType();
      }
      return primary.identifier() != null && isOwnName(primary.identifier().getText());
    }
    return ctx instanceof JavaParser.MemberReferenceExpressionContext reference
        && reference.THIS() != null
        && reference.expression() != null
        && isOwnName(reference.expression().getText());
  }

  private boolean isOwnName(final String name) {
    return name.equals(members.typeName()) && !scopes.hidesVariable(name);
  }

  private void useVariable(final JavaParser.IdentifierContext identifier) {
    if (identifier != null && !scopes.hidesVariable(identifier.getText())) {
      useField(identifier.getText());
    }
  }

  private void useField(final String name) {
    if (members.isStateField(name)) {
      usedFields.add(name);
    }
  }

  private void useMethod(
      final JavaParser.IdentifierContext identifier, final JavaParser.ArgumentsContext arguments) {
    if (identifier != null) {
      final int argumentCount = countArguments(arguments);
      useMethods(identifier.getText(), method -> method.accepts(argumentCount));
    }
  }

  private void useMethods(final String name, final Predicate<Method> overload) {
    if (members.isRecordComponent(name) && !members.declaresMethod(name)) {
      useField(name);
    }
    for (final Method method : members.methods()) {
      if (method.name().equals(name) && overload.test(method)) {
        usedMethods.add(method);
      }
    }
  }

  private static int countArguments(final JavaParser.ArgumentsContext ctx) {
    return ctx == null || ctx.expressionList() == null
        ? 0
        : ctx.expressionList().expression().size();
  }

  /** Annotations and constants in case labels never access the state of the class. */
  private static Predicate<ParseTree> isIrrelevant() {
    return tree ->
        tree instanceof JavaParser.AnnotationContext
            || tree instanceof JavaParser.ExpressionContext
                && tree.getParent() instanceof JavaParser.SwitchLabelContext
            || tree instanceof JavaParser.ExpressionListContext
                && tree.getParent() instanceof JavaParser.SwitchLabeledRuleContext;
  }
}
