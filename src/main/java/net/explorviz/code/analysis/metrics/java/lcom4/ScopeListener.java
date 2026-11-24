package net.explorviz.code.analysis.metrics.java.lcom4;

import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserBaseListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Keeps track of the {@link Scopes} while walking the code of a method. */
/* package */ class ScopeListener extends JavaParserBaseListener {

  protected final Scopes scopes = new Scopes();

  @Override
  public void enterEveryRule(final ParserRuleContext ctx) {
    if (opensType(ctx)) {
      scopes.enterType(ClassMembers.of(typeOf(ctx)));
    } else if (opensBlock(ctx)) {
      scopes.enterBlock();
    }
  }

  @Override
  public void exitEveryRule(final ParserRuleContext ctx) {
    if (opensType(ctx) || opensBlock(ctx)) {
      scopes.leave();
    }
  }

  @Override
  public void enterVariableDeclaratorId(final JavaParser.VariableDeclaratorIdContext ctx) {
    if (!(ctx.getParent() instanceof JavaParser.EnhancedForControlContext)) {
      declare(ctx.identifier());
    }
  }

  @Override
  public void exitEnhancedForControl(final JavaParser.EnhancedForControlContext ctx) {
    // Not visible in the iterated expression, hence declared afterwards.
    if (ctx.variableDeclaratorId() != null) {
      declare(ctx.variableDeclaratorId().identifier());
    }
  }

  @Override
  public void enterLocalVariableDeclaration(final JavaParser.LocalVariableDeclarationContext ctx) {
    declare(ctx.identifier());
  }

  @Override
  public void enterResource(final JavaParser.ResourceContext ctx) {
    declare(ctx.identifier());
  }

  @Override
  public void enterCatchClause(final JavaParser.CatchClauseContext ctx) {
    declare(ctx.identifier());
  }

  @Override
  public void enterSwitchLabel(final JavaParser.SwitchLabelContext ctx) {
    declare(ctx.varName);
  }

  @Override
  public void enterLambdaParameters(final JavaParser.LambdaParametersContext ctx) {
    ctx.identifier().forEach(this::declare);
  }

  @Override
  public void enterLambdaLVTIParameter(final JavaParser.LambdaLVTIParameterContext ctx) {
    declare(ctx.identifier());
  }

  /**
   * Declares the binding of a type pattern without modifiers. The grammar parses the binding {@code
   * s} in {@code instanceof String s} as part of the type.
   */
  @Override
  public void enterInstanceOfOperatorExpression(
      final JavaParser.InstanceOfOperatorExpressionContext ctx) {
    if (ctx.typeType() == null || ctx.typeType().classOrInterfaceType() == null) {
      return;
    }
    final JavaParser.ClassTypeContext type = ctx.typeType().classOrInterfaceType().classType();
    final int last = type == null ? 0 : type.getChildCount() - 1;
    if (last < 1) {
      return;
    }
    final ParseTree beforeLast = type.getChild(last - 1);
    if (type.getChild(last) instanceof JavaParser.TypeIdentifierContext binding
        && (beforeLast instanceof JavaParser.TypeIdentifierContext
            || beforeLast instanceof JavaParser.TypeArgumentsContext)) {
      scopes.declareVariable(binding.getText());
    }
  }

  private void declare(final JavaParser.IdentifierContext identifier) {
    if (identifier != null) {
      scopes.declareVariable(identifier.getText());
    }
  }

  private static boolean opensType(final ParserRuleContext ctx) {
    return ctx instanceof JavaParser.ClassBodyContext
        || ctx instanceof JavaParser.InterfaceBodyContext
        || ctx instanceof JavaParser.RecordBodyContext
        || ctx instanceof JavaParser.EnumDeclarationContext;
  }

  private static ParserRuleContext typeOf(final ParserRuleContext ctx) {
    return ctx instanceof JavaParser.EnumDeclarationContext ? ctx : ctx.getParent();
  }

  private static boolean opensBlock(final ParserRuleContext ctx) {
    if (ctx instanceof JavaParser.StatementContext statement) {
      return statement.FOR() != null || statement.TRY() != null || statement.SWITCH() != null;
    }
    if (ctx instanceof JavaParser.SwitchLabeledRuleContext rule) {
      // Cases with a colon share the scope of the switch.
      return rule.ARROW() != null;
    }
    return ctx instanceof JavaParser.BlockContext
        || ctx instanceof JavaParser.CatchClauseContext
        || ctx instanceof JavaParser.SwitchExpressionContext
        || ctx instanceof JavaParser.LambdaExpressionContext
        || ctx instanceof JavaParser.MethodDeclarationContext
        || ctx instanceof JavaParser.ConstructorDeclarationContext
        || ctx instanceof JavaParser.InterfaceCommonBodyDeclarationContext;
  }
}
