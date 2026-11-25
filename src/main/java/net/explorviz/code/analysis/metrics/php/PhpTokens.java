package net.explorviz.code.analysis.metrics.php;

import net.explorviz.code.analysis.antlr.generated.php.PhpParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of PHP. */
public final class PhpTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              PhpParser.VarName,
              PhpParser.Label,
              PhpParser.Null,
              PhpParser.BooleanConstant,
              PhpParser.Octal,
              PhpParser.Decimal,
              PhpParser.Hex,
              PhpParser.Binary,
              PhpParser.Real,
              PhpParser.SingleQuoteString,
              PhpParser.BackQuoteString,
              PhpParser.HtmlText)
          .ignored(PhpParser.StringPart, PhpParser.UnicodeEscape)
          .operandRules(
              PhpParser.IdentifierContext.class,
              PhpParser.StringContext.class,
              PhpParser.MagicConstantContext.class)
          .operatorRules(PhpParser.ConditionalExpressionContext.class);

  private PhpTokens() {}
}
