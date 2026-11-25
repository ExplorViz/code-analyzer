package net.explorviz.code.analysis.metrics.controlflow;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.explorviz.code.analysis.metrics.TreeWalker;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Measures all functions of a file in a single pass: cyclomatic complexity (McCabe, 1976), NPath
 * complexity (Nejmeh, 1988) and nested block depth. The definitions are the same for all languages
 * and correspond to those for Java.
 */
public final class ControlFlowAnalysis implements TreeWalker.Visitor {

  private final ControlFlowGrammar grammar;
  private final Map<ParserRuleContext, FunctionMetrics> functions = new IdentityHashMap<>();
  private final Deque<FunctionState> states = new ArrayDeque<>();
  private final Deque<Entered> entered = new ArrayDeque<>();

  /** Conditions and else branches of open constructs that have not been entered yet. */
  private final Set<ParserRuleContext> conditions = newIdentitySet();

  private final Set<ParserRuleContext> alternatives = newIdentitySet();

  private ControlFlowAnalysis(final ControlFlowGrammar grammar) {
    this.grammar = grammar;
    // Code outside of functions is ignored.
    states.push(new FunctionState(false));
  }

  /** Returns the metrics of all functions of the file by the rule that declares the function. */
  public static Map<ParserRuleContext, FunctionMetrics> ofFile(
      final ParserRuleContext file, final ControlFlowGrammar grammar) {
    final ControlFlowAnalysis analysis = new ControlFlowAnalysis(grammar);
    TreeWalker.walk(file, analysis);
    return Collections.unmodifiableMap(analysis.functions);
  }

  @Override
  public boolean enter(final ParserRuleContext context) {
    final FunctionState state = states.peek();
    final Construct construct = grammar.constructOf(context);
    final Construct.Kind kind = construct.kind();
    final boolean isAlternative = alternatives.remove(context);
    if (isAlternative) {
      state.enterElse(context.getStart());
    }
    final boolean isCondition = conditions.remove(context);
    if (isCondition) {
      state.enterMeasured();
    }
    final boolean isNested =
        kind.isNested() && !(kind == Construct.Kind.BRANCH && state.isElseIf(context.getStart()));
    if (isNested) {
      state.enterLevel();
    }
    final boolean isMeasuredElsewhere =
        state.isMeasured() && (kind == Construct.Kind.TERNARY || kind == Construct.Kind.RETURN);
    entered.push(new Entered(kind, isAlternative, isCondition, isNested, isMeasuredElsewhere));

    state.addDecisions(grammar.countShortCircuits(context) + construct.decisions());
    if (!isMeasuredElsewhere) {
      enterConstruct(context, construct, state);
    }
    return true;
  }

  @Override
  public void exit(final ParserRuleContext context) {
    final Entered exited = entered.pop();
    if (!exited.isMeasuredElsewhere()) {
      exitConstruct(context, exited.kind());
    }
    final FunctionState state = states.peek();
    if (exited.isNested()) {
      state.leaveLevel();
    }
    if (exited.isCondition()) {
      state.leaveMeasured();
    }
    if (exited.isAlternative()) {
      state.paths.leaveBranch();
    }
  }

  private void enterConstruct(
      final ParserRuleContext context, final Construct construct, final FunctionState state) {
    switch (construct.kind()) {
      case FUNCTION, TYPE -> states.push(new FunctionState(construct.hasBody()));
      case BRANCH, LOOP, SWITCH, GUARD -> {
        conditions.addAll(construct.conditions());
        if (construct.alternative() != null) {
          alternatives.add(construct.alternative());
        }
        state.open(construct.kind() == Construct.Kind.SWITCH);
        state.paths.enter(1 + countPaths(construct));
      }
      case ELSE_IF -> {
        conditions.addAll(construct.conditions());
        state.open(false);
        state.paths.enterElseIf(countPaths(construct));
      }
      case CASE -> {
        conditions.addAll(construct.conditions());
        state.enterCase(construct.labels() + countPaths(construct));
      }
      case DEFAULT -> state.enterCase(0);
      case TRY, CATCH -> {
        state.open(false);
        state.paths.enter(1);
      }
      case TERNARY, RETURN -> {
        state.enterMeasured();
        state.open(false);
        state.paths.enter(countPaths(context));
      }
      default -> {
        // No own paths
      }
    }
  }

  private void exitConstruct(final ParserRuleContext context, final Construct.Kind kind) {
    final FunctionState state = states.peek();
    switch (kind) {
      case FUNCTION -> functions.put(context, states.pop().toMetrics());
      case TYPE -> states.pop();
      case BRANCH, LOOP, SWITCH, GUARD -> {
        state.close();
        state.paths.leaveConditional();
      }
      case ELSE_IF -> {
        state.close();
        state.paths.leaveBranch();
      }
      case TRY -> {
        state.close();
        state.paths.leaveTry();
      }
      case CATCH -> {
        state.close();
        state.paths.leaveCatch();
      }
      case TERNARY, RETURN -> {
        state.leaveMeasured();
        state.close();
        state.paths.leaveExpression();
      }
      default -> {
        // No own paths
      }
    }
  }

  private int countPaths(final Construct construct) {
    return construct.conditions().stream().mapToInt(this::countPaths).sum();
  }

  /**
   * Counts one path for each short-circuit operator and two for each ternary operator of an
   * expression, wherever they are nested.
   */
  private int countPaths(final ParserRuleContext expression) {
    int paths = 0;
    final Deque<ParserRuleContext> pending = new ArrayDeque<>();
    pending.push(expression);
    while (!pending.isEmpty()) {
      final ParserRuleContext context = pending.pop();
      final Construct construct = grammar.constructOf(context);
      final Construct.Kind kind = construct.kind();
      if (kind == Construct.Kind.FUNCTION || kind == Construct.Kind.TYPE) {
        continue;
      }
      paths += grammar.countShortCircuits(context);
      if (kind == Construct.Kind.TERNARY) {
        paths += 2 * construct.decisions();
      }
      for (int i = 0; i < context.getChildCount(); i++) {
        final ParseTree child = context.getChild(i);
        if (child instanceof ParserRuleContext childContext) {
          pending.push(childContext);
        }
      }
    }
    return paths;
  }

  private static Set<ParserRuleContext> newIdentitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private record Entered(
      Construct.Kind kind,
      boolean isAlternative,
      boolean isCondition,
      boolean isNested,
      boolean isMeasuredElsewhere) {}
}
