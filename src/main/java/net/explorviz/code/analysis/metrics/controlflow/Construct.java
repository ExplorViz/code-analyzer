package net.explorviz.code.analysis.metrics.controlflow;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Control flow construct that a rule of a grammar stands for.
 *
 * @param kind the kind of the construct
 * @param conditions the expressions that decide about the path, e.g. the condition of an if
 * @param alternative the else branch of an if or loop or {@code null}. If it starts with another
 *     if, that one is an else if.
 * @param labels the number of values of a case
 * @param hasBody whether a function has a body
 * @param decisions the number of decision points of the cyclomatic complexity
 */
public record Construct(
    Kind kind,
    List<ParserRuleContext> conditions,
    ParserRuleContext alternative,
    int labels,
    boolean hasBody,
    int decisions) {

  /** Rules without own meaning for the control flow. */
  public static final Construct NONE = of(Kind.NONE);

  /** Body of a type, whose code does not belong to the function around it. */
  public static final Construct TYPE = of(Kind.TYPE);

  /** Label of a switch for all other values. */
  public static final Construct DEFAULT = of(Kind.DEFAULT);

  /** Try statement including its catch clauses. */
  public static final Construct TRY = of(Kind.TRY);

  /** Catch clause of a try statement. */
  public static final Construct CATCH = of(Kind.CATCH);

  /** Ternary operator with its operands. */
  public static final Construct TERNARY = of(Kind.TERNARY);

  /** Return statement or expression that is returned implicitly. */
  public static final Construct RETURN = of(Kind.RETURN);

  /** Nested block without own paths, e.g. of a lambda. */
  public static final Construct BLOCK = of(Kind.BLOCK);

  /** Several ternary operators that the grammar does not nest. */
  public static Construct ternaries(final int count) {
    return new Construct(Kind.TERNARY, List.of(), null, 0, false, count);
  }

  /** Function that is measured on its own, {@code body} may be {@code null}. */
  public static Construct function(final ParserRuleContext body) {
    return new Construct(Kind.FUNCTION, List.of(), null, 0, body != null, 0);
  }

  /** If statement or expression with an optional else branch. */
  public static Construct branch(
      final ParserRuleContext condition, final ParserRuleContext alternative) {
    return new Construct(Kind.BRANCH, present(condition), alternative, 0, false, 1);
  }

  /** Else if clause that is a direct part of its if. */
  public static Construct elseIf(final ParserRuleContext condition) {
    return new Construct(Kind.ELSE_IF, present(condition), null, 0, false, 1);
  }

  /** Loop. */
  public static Construct loop(final ParserRuleContext... conditions) {
    return new Construct(Kind.LOOP, present(conditions), null, 0, false, 1);
  }

  /** Loop with an else branch, which may be {@code null}. */
  public static Construct loopElse(
      final ParserRuleContext condition, final ParserRuleContext alternative) {
    return new Construct(Kind.LOOP, present(condition), alternative, 0, false, 1);
  }

  /** Switch statement or expression, {@code selector} may be {@code null}. */
  public static Construct switchOn(final ParserRuleContext selector) {
    return new Construct(Kind.SWITCH, present(selector), null, 0, false, 0);
  }

  /**
   * Label of a switch. Its code ends at the next label or at the end of the switch.
   *
   * @param labels the number of values or patterns
   * @param conditions the values
   */
  public static Construct label(
      final int labels, final List<? extends ParserRuleContext> conditions) {
    return labels(1, labels, conditions);
  }

  /**
   * Several labels of a switch that share their code.
   *
   * @param decisions the number of labels
   * @param labels the number of values or patterns
   * @param conditions the values
   */
  public static Construct labels(
      final int decisions, final int labels, final List<? extends ParserRuleContext> conditions) {
    return new Construct(Kind.CASE, List.copyOf(conditions), null, labels, false, decisions);
  }

  /** Additional condition, e.g. of a case. */
  public static Construct guard(final ParserRuleContext... conditions) {
    return new Construct(Kind.GUARD, present(conditions), null, 0, false, 1);
  }

  private static Construct of(final Kind kind) {
    final boolean isDecision = kind == Kind.CATCH || kind == Kind.TERNARY;
    return new Construct(kind, List.of(), null, 0, false, isDecision ? 1 : 0);
  }

  private static List<ParserRuleContext> present(final ParserRuleContext... contexts) {
    return Arrays.stream(contexts).filter(Objects::nonNull).toList();
  }

  /** Kinds of constructs. */
  public enum Kind {
    NONE,
    FUNCTION,
    TYPE,
    BRANCH,
    ELSE_IF,
    LOOP,
    SWITCH,
    CASE,
    DEFAULT,
    GUARD,
    TRY,
    CATCH,
    TERNARY,
    RETURN,
    BLOCK;

    /** Whether the construct adds a level to the nested block depth. */
    /* package */ boolean isNested() {
      return switch (this) {
        case BRANCH, LOOP, SWITCH, TRY, BLOCK -> true;
        default -> false;
      };
    }
  }
}
