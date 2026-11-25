package net.explorviz.code.analysis.metrics.cpp;

import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of C++. */
public final class CppTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              CPP14Parser.Identifier,
              CPP14Parser.IntegerLiteral,
              CPP14Parser.FloatingLiteral,
              CPP14Parser.CharacterLiteral,
              CPP14Parser.StringLiteral,
              CPP14Parser.BooleanLiteral,
              CPP14Parser.PointerLiteral,
              CPP14Parser.UserDefinedLiteral,
              CPP14Parser.DecimalLiteral,
              CPP14Parser.OctalLiteral,
              CPP14Parser.HexadecimalLiteral,
              CPP14Parser.BinaryLiteral)
          .operatorRules(
              CPP14Parser.ConditionalExpressionContext.class,
              CPP14Parser.ShiftOperatorContext.class,
              CPP14Parser.TheOperatorContext.class)
          .angleBracketsExceptIn(CPP14Parser.RelationalExpressionContext.class);

  private CppTokens() {}
}
