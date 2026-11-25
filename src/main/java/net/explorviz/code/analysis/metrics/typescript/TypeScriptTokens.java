package net.explorviz.code.analysis.metrics.typescript;

import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of TypeScript and JavaScript. Semicolons are optional and not counted. */
public final class TypeScriptTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              TypeScriptParser.Identifier,
              TypeScriptParser.NullLiteral,
              TypeScriptParser.BooleanLiteral,
              TypeScriptParser.DecimalLiteral,
              TypeScriptParser.HexIntegerLiteral,
              TypeScriptParser.OctalIntegerLiteral,
              TypeScriptParser.OctalIntegerLiteral2,
              TypeScriptParser.BinaryIntegerLiteral,
              TypeScriptParser.BigHexIntegerLiteral,
              TypeScriptParser.BigOctalIntegerLiteral,
              TypeScriptParser.BigBinaryIntegerLiteral,
              TypeScriptParser.BigDecimalIntegerLiteral,
              TypeScriptParser.StringLiteral,
              TypeScriptParser.RegularExpressionLiteral)
          .ignored(
              TypeScriptParser.SemiColon,
              TypeScriptParser.TemplateStringAtom,
              TypeScriptParser.TemplateStringEscapeAtom,
              TypeScriptParser.TemplateStringStartExpression,
              TypeScriptParser.TemplateCloseBrace)
          .operandRules(
              TypeScriptParser.IdentifierContext.class,
              TypeScriptParser.IdentifierOrKeyWordContext.class,
              TypeScriptParser.KeywordContext.class,
              TypeScriptParser.TemplateStringLiteralContext.class)
          .operatorRules(
              TypeScriptParser.TernaryExpressionContext.class,
              TypeScriptParser.BitShiftExpressionContext.class)
          .angleBracketsExceptIn(TypeScriptParser.RelationalExpressionContext.class);

  private TypeScriptTokens() {}
}
