package net.explorviz.code.analysis.metrics.rust;

import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Rust, including the arguments of macros. */
public final class RustTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              RustParser.NON_KEYWORD_IDENTIFIER,
              RustParser.RAW_IDENTIFIER,
              RustParser.KW_TRUE,
              RustParser.KW_FALSE,
              RustParser.CHAR_LITERAL,
              RustParser.STRING_LITERAL,
              RustParser.RAW_STRING_LITERAL,
              RustParser.BYTE_LITERAL,
              RustParser.BYTE_STRING_LITERAL,
              RustParser.RAW_BYTE_STRING_LITERAL,
              RustParser.INTEGER_LITERAL,
              RustParser.FLOAT_LITERAL,
              RustParser.LIFETIME_OR_LABEL,
              RustParser.KW_STATICLIFETIME,
              RustParser.KW_UNDERLINELIFETIME)
          .operandRules(RustParser.IdentifierContext.class)
          .operatorRules(RustParser.ShlContext.class, RustParser.ShrContext.class)
          .angleBracketsExceptIn(
              RustParser.ComparisonOperatorContext.class,
              RustParser.MacroPunctuationTokenContext.class);

  private RustTokens() {}
}
