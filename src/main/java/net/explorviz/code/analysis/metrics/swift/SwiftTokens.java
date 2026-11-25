package net.explorviz.code.analysis.metrics.swift;

import net.explorviz.code.analysis.antlr.generated.swift.Swift5Parser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Swift. Semicolons are optional and not counted. */
public final class SwiftTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              Swift5Parser.Identifier,
              Swift5Parser.TRUE,
              Swift5Parser.FALSE,
              Swift5Parser.NIL,
              Swift5Parser.Binary_literal,
              Swift5Parser.Octal_literal,
              Swift5Parser.Decimal_digits,
              Swift5Parser.Decimal_literal,
              Swift5Parser.Hexadecimal_literal,
              Swift5Parser.Floating_point_literal)
          .ignored(Swift5Parser.SEMI)
          .operandRules(
              Swift5Parser.IdentifierContext.class,
              Swift5Parser.Static_string_literalContext.class,
              Swift5Parser.Interpolated_string_literalContext.class,
              Swift5Parser.Extended_string_literalContext.class)
          .operatorRules(
              Swift5Parser.Conditional_operatorContext.class,
              Swift5Parser.Arrow_operatorContext.class,
              Swift5Parser.Range_operatorContext.class,
              Swift5Parser.Same_type_equalsContext.class,
              Swift5Parser.Try_operatorContext.class)
          .nestedOperatorRules(
              Swift5Parser.OperatorContext.class,
              Swift5Parser.Operator_headContext.class,
              Swift5Parser.Operator_characterContext.class,
              Swift5Parser.Operator_charactersContext.class,
              Swift5Parser.Dot_operator_headContext.class,
              Swift5Parser.Dot_operator_characterContext.class,
              Swift5Parser.Dot_operator_charactersContext.class)
          .angleBracketsExceptIn();

  private SwiftTokens() {}
}
