package net.explorviz.code.analysis.metrics.kotlin;

import java.util.Set;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.controlflow.Construct;
import net.explorviz.code.analysis.metrics.controlflow.ControlFlowGrammar;
import org.antlr.v4.runtime.ParserRuleContext;

/** Control flow of Kotlin. Lambdas and local functions belong to the function around them. */
public final class KotlinControlFlow implements ControlFlowGrammar {

  private static final Set<String> CONTROL_STRUCTURES =
      Set.of("if", "when", "for", "while", "do", "try");

  @Override
  public Construct constructOf(final ParserRuleContext context) {
    return switch (context) {
      case KotlinParser.FunctionDeclarationContext ctx when isLocal(ctx) -> Construct.BLOCK;
      case KotlinParser.FunctionDeclarationContext ctx -> Construct.function(ctx.functionBody());
      case KotlinParser.FunctionBodyContext ctx
          when ctx.block() == null
              && ctx.getParent() instanceof KotlinParser.FunctionDeclarationContext ->
          Construct.RETURN;
      case KotlinParser.SecondaryConstructorContext ctx -> Construct.function(ctx.block());
      case KotlinParser.ClassBodyContext ctx -> Construct.TYPE;
      case KotlinParser.EnumClassBodyContext ctx -> Construct.TYPE;
      case KotlinParser.FunctionLiteralContext ctx when isBlock(ctx) -> Construct.BLOCK;
      case KotlinParser.IfExpressionContext ctx -> Construct.branch(ctx.expression(), elseOf(ctx));
      case KotlinParser.ForExpressionContext ctx -> Construct.loop(ctx.expression());
      case KotlinParser.WhileExpressionContext ctx -> Construct.loop(ctx.expression());
      case KotlinParser.DoWhileExpressionContext ctx -> Construct.loop(ctx.expression());
      case KotlinParser.WhenExpressionContext ctx -> Construct.switchOn(ctx.expression());
      case KotlinParser.WhenEntryContext ctx when ctx.whenCondition().isEmpty() ->
          Construct.DEFAULT;
      case KotlinParser.WhenEntryContext ctx ->
          Construct.label(ctx.whenCondition().size(), ctx.whenCondition());
      case KotlinParser.TryExpressionContext ctx -> Construct.TRY;
      case KotlinParser.CatchBlockContext ctx -> Construct.CATCH;
      case KotlinParser.JumpExpressionContext ctx
          when ctx.RETURN() != null || ctx.RETURN_AT() != null ->
          Construct.RETURN;
      default -> Construct.NONE;
    };
  }

  @Override
  public int countShortCircuits(final ParserRuleContext context) {
    return switch (context) {
      case KotlinParser.DisjunctionContext ctx -> ctx.DISJ().size();
      case KotlinParser.ConjunctionContext ctx -> ctx.CONJ().size();
      case KotlinParser.ElvisExpressionContext ctx -> ctx.ELVIS().size();
      default -> 0;
    };
  }

  private static boolean isLocal(final KotlinParser.FunctionDeclarationContext ctx) {
    final ParserRuleContext owner =
        Trees.enclosing(
            ctx,
            KotlinParser.ClassBodyContext.class,
            KotlinParser.EnumClassBodyContext.class,
            KotlinParser.FunctionDeclarationContext.class,
            KotlinParser.SecondaryConstructorContext.class,
            KotlinParser.AnonymousInitializerContext.class,
            KotlinParser.GetterContext.class,
            KotlinParser.SetterContext.class,
            KotlinParser.FunctionLiteralContext.class);
    return owner != null
        && !(owner instanceof KotlinParser.ClassBodyContext)
        && !(owner instanceof KotlinParser.EnumClassBodyContext);
  }

  /** Whether the lambda is more than a single expression. */
  private static boolean isBlock(final KotlinParser.FunctionLiteralContext ctx) {
    return ctx.statements() != null
        && (ctx.statements().statement().size() > 1
            || ctx.statements().statement().stream()
                .anyMatch(statement -> CONTROL_STRUCTURES.contains(firstTokenOf(statement))));
  }

  private static String firstTokenOf(final ParserRuleContext context) {
    return Trees.firstTokens(context, 1).stream().findFirst().orElse("");
  }

  private static ParserRuleContext elseOf(final KotlinParser.IfExpressionContext ctx) {
    if (ctx.ELSE() == null || ctx.controlStructureBody().isEmpty()) {
      return null;
    }
    final ParserRuleContext last = ctx.controlStructureBody().getLast();
    final int elseIndex = ctx.ELSE().getSymbol().getTokenIndex();
    return last.getStart().getTokenIndex() > elseIndex ? last : null;
  }
}
