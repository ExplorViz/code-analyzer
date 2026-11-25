package net.explorviz.code.analysis.metrics.csharp;

import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of C#. */
public final class CSharpTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              CSharpParser.Simple_Identifier,
              CSharpParser.Integer_Literal,
              CSharpParser.Real_Literal,
              CSharpParser.Character_Literal,
              CSharpParser.String_Literal,
              CSharpParser.TRUE,
              CSharpParser.FALSE,
              CSharpParser.NULL)
          .ignored(
              CSharpParser.Regular_Interpolation_Format, CSharpParser.Verbatim_Interpolation_Format)
          .operandRules(
              CSharpParser.IdentifierContext.class,
              CSharpParser.Contextual_keywordContext.class,
              CSharpParser.Discard_tokenContext.class,
              CSharpParser.Interpolated_regular_string_expressionContext.class,
              CSharpParser.Interpolated_verbatim_string_expressionContext.class)
          .operatorRules(
              CSharpParser.Conditional_expressionContext.class,
              CSharpParser.Right_shiftContext.class,
              CSharpParser.Right_shift_assignmentContext.class)
          .angleBracketsExceptIn(
              CSharpParser.Relational_expressionContext.class,
              CSharpParser.Overloadable_binary_operatorContext.class);

  private CSharpTokens() {}
}
