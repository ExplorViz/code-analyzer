package net.explorviz.code.analysis.metrics.halstead;

import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Volume (Halstead, 1977) and lines of code. Operands are identifiers and literals, everything else
 * is an operator. Brackets that belong together are a single operator.
 */
public final class HalsteadVolume {

  private final TokenGrammar grammar;
  private final Set<String> distinctOperators = new HashSet<>();
  private final Set<String> distinctOperands = new HashSet<>();
  private final Set<Integer> codeLines = new HashSet<>();
  private int length;

  /** Creates an empty volume for code in the given language. */
  public HalsteadVolume(final TokenGrammar grammar) {
    this.grammar = grammar;
  }

  /** Returns the program length times the binary logarithm of the vocabulary. */
  public double value() {
    final int vocabulary = distinctOperators.size() + distinctOperands.size();
    return vocabulary == 0 ? 0 : length * StrictMath.log(vocabulary) / StrictMath.log(2);
  }

  /** Returns the number of lines with code. */
  public int lines() {
    return codeLines.size();
  }

  /** Adds the tokens that are direct children of the rule. */
  public void add(final ParserRuleContext context) {
    final StringBuilder operator = new StringBuilder();
    boolean hasTokens = false;
    for (int i = 0; i < context.getChildCount(); i++) {
      final ParseTree child = context.getChild(i);
      if (child instanceof TerminalNode terminal && isCode(terminal.getSymbol())) {
        hasTokens = true;
        codeLines.add(terminal.getSymbol().getLine());
        operator.append(terminal.getText());
        addToken(context, terminal.getSymbol());
      }
    }
    if (grammar.isNestedOperator(context) && !context.getText().isEmpty()) {
      addOperator(context.getText());
    } else if (hasTokens && grammar.isOperand(context)) {
      addOperand(context.getText());
    } else if (hasTokens && grammar.isOperator(context)) {
      addOperator(operator.toString());
    }
  }

  /** Tokens that the parser has made up have no index. */
  private static boolean isCode(final Token token) {
    return token.getTokenIndex() >= 0
        && token.getType() != Token.EOF
        && token.getText() != null
        && !token.getText().isBlank();
  }

  /** Adds a token unless it belongs to an operand or operator that consists of several tokens. */
  private void addToken(final ParserRuleContext context, final Token token) {
    if (grammar.isOperand(context) || grammar.isOperator(context) || grammar.isPart(context)) {
      return;
    }
    switch (grammar.kindOf(context, token)) {
      case OPERAND -> addOperand(token.getText());
      case OPERATOR -> addOperator(token.getText());
      case ANGLE_BRACKET -> addOperator("<>");
      default -> {
        // Counted with another token or no code
      }
    }
  }

  private void addOperand(final String operand) {
    distinctOperands.add(operand);
    length++;
  }

  private void addOperator(final String operator) {
    distinctOperators.add(operator);
    length++;
  }
}
