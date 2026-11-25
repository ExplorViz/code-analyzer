package net.explorviz.code.analysis.metrics.java;

import java.util.HashSet;
import java.util.Set;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.metrics.TreeWalker;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Volume (Halstead, 1977) and lines of the code that belongs to a function itself, i.e. without the
 * bodies of local and anonymous classes.
 *
 * <p>Operands are identifiers and literals, everything else is an operator. Brackets that belong
 * together are a single operator, as are {@code ?:} and the shift operators.
 */
public final class HalsteadVolume {

  private final Set<String> distinctOperators = new HashSet<>();
  private final Set<String> distinctOperands = new HashSet<>();
  private final Set<Integer> codeLines = new HashSet<>();
  private int length;

  private HalsteadVolume() {}

  /** Measures the function that is declared by the given rule. */
  public static HalsteadVolume of(final ParserRuleContext declaration) {
    final HalsteadVolume volume = new HalsteadVolume();
    TreeWalker.walk(
        declaration,
        context -> {
          if (JavaTreeWalker.isTypeBody(context)) {
            return false;
          }
          volume.addTokens(context);
          return true;
        });
    return volume;
  }

  /** Returns the program length times the binary logarithm of the vocabulary. */
  public double value() {
    final int vocabulary = distinctOperators.size() + distinctOperands.size();
    return vocabulary == 0 ? 0 : length * Math.log(vocabulary) / Math.log(2);
  }

  /** Returns the number of lines with code. */
  public int lines() {
    return codeLines.size();
  }

  /** Adds the tokens that are direct children of the rule. */
  private void addTokens(final ParserRuleContext context) {
    final boolean isSingleOperator =
        context instanceof JavaParser.BinaryOperatorExpressionContext
            || context instanceof JavaParser.TernaryExpressionContext;
    final StringBuilder singleOperator = new StringBuilder();
    for (int i = 0; i < context.getChildCount(); i++) {
      final ParseTree child = context.getChild(i);
      // Tokens that the parser has made up have no index.
      if (child instanceof TerminalNode terminal && terminal.getSymbol().getTokenIndex() >= 0) {
        codeLines.add(terminal.getSymbol().getLine());
        if (isSingleOperator) {
          singleOperator.append(terminal.getText());
        } else {
          addToken(context, terminal.getSymbol());
        }
      }
    }
    if (!singleOperator.isEmpty()) {
      addOperator(singleOperator.toString());
    }
  }

  private void addToken(final ParserRuleContext context, final Token token) {
    if (isOperand(context, token)) {
      distinctOperands.add(token.getText());
      length++;
      return;
    }
    switch (token.getType()) {
      case JavaParser.RPAREN, JavaParser.RBRACK, JavaParser.RBRACE, JavaParser.GT -> {
        // Counted with the opening bracket
      }
      // Not the operator, which is part of a binary expression
      case JavaParser.LT -> addOperator("<>");
      default -> addOperator(token.getText());
    }
  }

  private void addOperator(final String operator) {
    distinctOperators.add(operator);
    length++;
  }

  /** Contextual keywords such as {@code record} are identifiers in some places. */
  private static boolean isOperand(final ParserRuleContext context, final Token token) {
    return token.getType() == JavaParser.IDENTIFIER
        || context instanceof JavaParser.IdentifierContext
        || context instanceof JavaParser.TypeIdentifierContext
        || context instanceof JavaParser.LiteralContext
        || context instanceof JavaParser.IntegerLiteralContext
        || context instanceof JavaParser.FloatLiteralContext;
  }
}
