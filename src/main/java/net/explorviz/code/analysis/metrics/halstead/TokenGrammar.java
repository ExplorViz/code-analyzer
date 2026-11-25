package net.explorviz.code.analysis.metrics.halstead;

import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Tells which tokens of a grammar are operators and operands. */
public final class TokenGrammar {

  private final Set<Integer> operands = new HashSet<>();
  private final Set<Integer> ignored = new HashSet<>();
  private final Set<Class<?>> operandRules = new HashSet<>();
  private final Set<Class<?>> operatorRules = new HashSet<>();
  private final Set<Class<?>> nestedOperatorRules = new HashSet<>();
  private final Set<Class<?>> partRules = new HashSet<>();
  private final Set<Class<?>> comparisonRules = new HashSet<>();
  private boolean hasAngleBrackets;

  /** Adds tokens that are operands. All other tokens are operators. */
  public TokenGrammar operands(final int... tokenTypes) {
    addAll(operands, tokenTypes);
    return this;
  }

  /** Adds tokens that are not counted, e.g. optional semicolons and the contents of strings. */
  public TokenGrammar ignored(final int... tokenTypes) {
    addAll(ignored, tokenTypes);
    return this;
  }

  /**
   * Adds rules that are a single operand if they have tokens of their own, e.g. strings and
   * identifiers that may be keywords.
   */
  public TokenGrammar operandRules(final Class<?>... rules) {
    operandRules.addAll(Set.of(rules));
    return this;
  }

  /** Adds rules whose own tokens are a single operator, e.g. of the ternary operator. */
  public TokenGrammar operatorRules(final Class<?>... rules) {
    operatorRules.addAll(Set.of(rules));
    return this;
  }

  /** Adds rules that are a single operator, whose tokens are in the given rules below. */
  public TokenGrammar nestedOperatorRules(final Class<?> operator, final Class<?>... parts) {
    nestedOperatorRules.add(operator);
    partRules.addAll(Set.of(parts));
    return this;
  }

  /** States that {@code <} and {@code >} are brackets, except in the given rules. */
  public TokenGrammar angleBracketsExceptIn(final Class<?>... rules) {
    hasAngleBrackets = true;
    comparisonRules.addAll(Set.of(rules));
    return this;
  }

  /* package */ boolean isOperand(final ParserRuleContext context) {
    return operandRules.contains(context.getClass());
  }

  /* package */ boolean isOperator(final ParserRuleContext context) {
    return operatorRules.contains(context.getClass());
  }

  /* package */ boolean isNestedOperator(final ParserRuleContext context) {
    return nestedOperatorRules.contains(context.getClass());
  }

  /* package */ boolean isPart(final ParserRuleContext context) {
    return partRules.contains(context.getClass());
  }

  /* package */ Kind kindOf(final ParserRuleContext context, final Token token) {
    if (ignored.contains(token.getType())) {
      return Kind.IGNORED;
    }
    if (operands.contains(token.getType())) {
      return Kind.OPERAND;
    }
    final boolean isBracket = hasAngleBrackets && !comparisonRules.contains(context.getClass());
    return switch (token.getText()) {
      case ")", "]", "}" -> Kind.IGNORED;
      case "<" -> isBracket ? Kind.ANGLE_BRACKET : Kind.OPERATOR;
      case ">" -> isBracket ? Kind.IGNORED : Kind.OPERATOR;
      default -> Kind.OPERATOR;
    };
  }

  private static void addAll(final Set<Integer> types, final int... tokenTypes) {
    for (final int type : tokenTypes) {
      types.add(type);
    }
  }

  /** Kinds of tokens. */
  /* package */ enum Kind {
    OPERAND,
    OPERATOR,
    ANGLE_BRACKET,
    IGNORED
  }
}
