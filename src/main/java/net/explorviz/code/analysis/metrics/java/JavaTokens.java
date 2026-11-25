package net.explorviz.code.analysis.metrics.java;

import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Java. */
public final class JavaTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(JavaParser.IDENTIFIER)
          .operandRules(
              JavaParser.IdentifierContext.class,
              JavaParser.TypeIdentifierContext.class,
              JavaParser.LiteralContext.class,
              JavaParser.IntegerLiteralContext.class,
              JavaParser.FloatLiteralContext.class)
          .operatorRules(
              JavaParser.BinaryOperatorExpressionContext.class,
              JavaParser.TernaryExpressionContext.class)
          .angleBracketsExceptIn();

  private JavaTokens() {}
}
