package net.explorviz.code.analysis.metrics.kotlin;

import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Kotlin. Semicolons are optional and not counted. */
public final class KotlinTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              KotlinParser.Identifier,
              KotlinParser.BooleanLiteral,
              KotlinParser.NullLiteral,
              KotlinParser.IntegerLiteral,
              KotlinParser.HexLiteral,
              KotlinParser.BinLiteral,
              KotlinParser.LongLiteral,
              KotlinParser.RealLiteral,
              KotlinParser.CharacterLiteral,
              KotlinParser.LabelReference,
              KotlinParser.LabelDefinition,
              KotlinParser.FieldIdentifier,
              KotlinParser.LineStrRef,
              KotlinParser.MultiLineStrRef)
          .ignored(
              KotlinParser.NL,
              KotlinParser.SEMICOLON,
              KotlinParser.DOUBLE_SEMICOLON,
              KotlinParser.LineStrText,
              KotlinParser.LineStrEscapedChar,
              KotlinParser.LineStrExprStart,
              KotlinParser.MultiLineStrText,
              KotlinParser.MultiLineStrEscapedChar,
              KotlinParser.MultiLineStrExprStart)
          .operandRules(
              KotlinParser.SimpleIdentifierContext.class,
              KotlinParser.LineStringLiteralContext.class,
              KotlinParser.MultiLineStringLiteralContext.class)
          .operatorRules(
              KotlinParser.MemberAccessOperatorContext.class,
              KotlinParser.PostfixUnaryOperationContext.class)
          .angleBracketsExceptIn(KotlinParser.ComparisonOperatorContext.class);

  private KotlinTokens() {}
}
