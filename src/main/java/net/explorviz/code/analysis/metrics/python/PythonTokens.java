package net.explorviz.code.analysis.metrics.python;

import net.explorviz.code.analysis.antlr.generated.python.PythonParser;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;

/** Operators and operands of Python. Line breaks, indentation and semicolons are not counted. */
public final class PythonTokens {

  public static final TokenGrammar GRAMMAR =
      new TokenGrammar()
          .operands(
              PythonParser.NAME,
              PythonParser.TRUE,
              PythonParser.FALSE,
              PythonParser.NONE,
              PythonParser.STRING,
              PythonParser.DECIMAL_INTEGER,
              PythonParser.OCT_INTEGER,
              PythonParser.HEX_INTEGER,
              PythonParser.BIN_INTEGER,
              PythonParser.FLOAT_NUMBER,
              PythonParser.IMAG_NUMBER,
              PythonParser.PRINT,
              PythonParser.EXEC)
          .ignored(
              PythonParser.LINE_BREAK,
              PythonParser.INDENT,
              PythonParser.DEDENT,
              PythonParser.SEMI_COLON)
          .operatorRules(PythonParser.TestContext.class, PythonParser.ComparisonContext.class);

  private PythonTokens() {}
}
