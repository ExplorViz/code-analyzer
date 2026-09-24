package net.explorviz.code.analysis.parser;

import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptLexer;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Verifies that {@code parse-timeout-ms} aborts ANTLR prediction on the parsing thread itself
 * (via {@link AntlrParserUtils.DeadlineTokenStream}), rather than leaving a zombie worker.
 */
class ParseTimeoutTest {

  @Test
  void deadlineAbortsPathologicalObjectLiteralWithinBudget() {
    // Deeply nested object literals historically trigger catastrophic LL prediction on the
    // TypeScript grammar when TypeScript-only alternatives are enabled.
    final StringBuilder source = new StringBuilder("module.exports = {\n");
    for (int i = 0; i < 80; i++) {
      source.append("  a").append(i).append(": { x: 1, y: 2, z: [1, 2, 3],\n");
    }
    for (int i = 0; i < 80; i++) {
      source.append("  },\n");
    }
    source.append("};\n");

    final TypeScriptLexer lexer = new TypeScriptLexer(CharStreams.fromString(source.toString()));
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TypeScriptParser parser = new TypeScriptParser(tokens);
    // Keep TypeScript mode so ambiguous alts remain — this is what hangs without a deadline.
    parser.setJavaScriptMode(false);

    final long timeoutMs = 500L;
    final long budgetMs = timeoutMs + 2_000L;
    final long start = System.nanoTime();
    final ParseCancellationException thrown = Assertions.assertThrows(
        ParseCancellationException.class,
        () -> AntlrParserUtils.parseTwoStage(
            parser, tokens, LoggerFactory.getLogger(ParseTimeoutTest.class),
            "pathological.js", timeoutMs, parser::program));
    final long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

    Assertions.assertTrue(
        thrown.getMessage() != null && thrown.getMessage().startsWith("Parse timeout after "),
        "Expected timeout cancellation, got: " + thrown.getMessage());
    Assertions.assertTrue(
        elapsedMs < budgetMs,
        "Expected abort under " + budgetMs + " ms, was " + elapsedMs + " ms");
  }

  @Test
  void zeroTimeoutDisablesDeadline() {
    final TypeScriptLexer lexer = new TypeScriptLexer(CharStreams.fromString("const x = 1;\n"));
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TypeScriptParser parser = new TypeScriptParser(tokens);
    parser.setJavaScriptMode(true);

    Assertions.assertDoesNotThrow(() -> AntlrParserUtils.parseTwoStage(
        parser, tokens, LoggerFactory.getLogger(ParseTimeoutTest.class),
        "ok.js", 0L, parser::program));
  }
}
