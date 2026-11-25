package net.explorviz.code.analysis.metrics.c;

import net.explorviz.code.analysis.antlr.generated.c.CParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of C. */
public final class CTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              CParser.Identifier,
              CParser.IntegerConstant,
              CParser.FloatingConstant,
              CParser.DigitSequence,
              CParser.CharacterConstant,
              CParser.StringLiteral,
              CParser.True_,
              CParser.False_,
              CParser.Nulptr,
              CParser.KW__func__,
              CParser.KW__FUNCTION__,
              CParser.KW__PRETTY_FUNCTION__)
          .operatorRules(CParser.ConditionalExpressionContext.class);

  private CTokens() {}
}
