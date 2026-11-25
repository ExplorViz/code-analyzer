package net.explorviz.code.analysis.metrics.golang;

import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Go. Semicolons are optional and not counted. */
public final class GoTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              GoParser.IDENTIFIER,
              GoParser.NIL_LIT,
              GoParser.DECIMAL_LIT,
              GoParser.BINARY_LIT,
              GoParser.OCTAL_LIT,
              GoParser.HEX_LIT,
              GoParser.FLOAT_LIT,
              GoParser.IMAGINARY_LIT,
              GoParser.RUNE_LIT,
              GoParser.RAW_STRING_LIT,
              GoParser.INTERPRETED_STRING_LIT)
          .ignored(GoParser.SEMI, GoParser.EOS);

  private GoTokens() {}
}
