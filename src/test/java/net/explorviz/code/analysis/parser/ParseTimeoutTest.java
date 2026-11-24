package net.explorviz.code.analysis.parser;

import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptLexer;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Verifies that a {@code parse-timeout-ms} of zero disables the parse deadline. */
class ParseTimeoutTest {

  @Test
  void zeroTimeoutDisablesDeadline() {
    final TypeScriptLexer lexer = new TypeScriptLexer(CharStreams.fromString("const x = 1;\n"));
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TypeScriptParser parser = new TypeScriptParser(tokens);
    parser.setJavaScriptMode(true);

    Assertions.assertDoesNotThrow(
        () ->
            AntlrParserUtils.parseTwoStage(
                parser,
                tokens,
                LoggerFactory.getLogger(ParseTimeoutTest.class),
                "ok.js",
                0L,
                parser::program));
  }
}
