package net.explorviz.code.analysis.parser;

import java.util.function.Supplier;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.slf4j.Logger;

/**
 * Shared ANTLR parser utilities applied uniformly to every language parser service.
 *
 * <p><b>Two-stage SLL → LL prediction</b>: the parser first tries the fast SLL prediction mode.
 * Only if SLL raises a {@link ParseCancellationException} (ambiguity that SLL cannot resolve) does
 * it retry with full LL. For typical, error-free source files this avoids the quadratic work that
 * the default LL mode performs on large inputs.
 *
 * <p><b>Error threshold</b>: if the LL stage accumulates more than {@link #MAX_ERRORS} syntax
 * errors, the {@link ThresholdBailErrorStrategy} aborts rather than performing unbounded O(n²)
 * error recovery on heavily malformed files.
 */
public final class AntlrParserUtils {

  /** Maximum number of syntax errors tolerated before aborting recovery. */
  static final int MAX_ERRORS = 50;

  private AntlrParserUtils() {}

  /**
   * Removes the default {@code ConsoleErrorListener} from the lexer so that lexer errors
   * are not printed to {@code stderr}.
   */
  public static void configureLexer(final Lexer lexer) {
    lexer.removeErrorListeners();
  }

  /**
   * Parses using SLL prediction first; retries with full LL only if SLL cannot resolve an
   * ambiguity. The LL stage uses a {@link ThresholdBailErrorStrategy} and routes syntax errors
   * to SLF4J instead of the console.
   *
   * @param <T>       the parse-tree context type returned by the entry-point rule
   * @param parser    the configured parser (tokens already consumed)
   * @param tokens    the token stream, rewound when falling back to LL
   * @param logger    the caller's SLF4J logger
   * @param fileName  source file name, included in warning messages
   * @param parseCall supplier that invokes the grammar's entry-point rule (e.g.
   *                  {@code parser::compilationUnit})
   * @return the root parse-tree node
   * @throws ParseCancellationException if the error threshold is exceeded during LL recovery
   */
  public static <T> T parseTwoStage(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final Supplier<T> parseCall) {

    // Stage 1: SLL — fast, works for the vast majority of valid source files.
    parser.removeErrorListeners();
    parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());
    try {
      return parseCall.get();
    } catch (ParseCancellationException e) {
      // SLL found an ambiguity it cannot resolve; fall through to full LL.
    }

    // Stage 2: LL — handles all ambiguities; limit error recovery to avoid O(n²) worst case.
    tokens.seek(0);
    parser.reset();
    parser.getInterpreter().setPredictionMode(PredictionMode.LL);
    parser.setErrorHandler(new ThresholdBailErrorStrategy(MAX_ERRORS));
    parser.removeErrorListeners();
    parser.addErrorListener(new LoggingErrorListener(logger, fileName));
    return parseCall.get();
  }

  // ── Inner helpers ──────────────────────────────────────────────────────────

  /**
   * Behaves like {@link DefaultErrorStrategy} (attempts single-token insertion/deletion recovery)
   * but throws {@link ParseCancellationException} after {@code maxErrors} recovery attempts.
   * This prevents the parser from spending minutes on a file the grammar cannot handle.
   */
  static final class ThresholdBailErrorStrategy extends DefaultErrorStrategy {

    private final int maxErrors;
    private int errorCount;

    ThresholdBailErrorStrategy(final int maxErrors) {
      this.maxErrors = maxErrors;
    }

    @Override
    public void recover(final Parser recognizer, final RecognitionException e) {
      if (++errorCount >= maxErrors) {
        throw new ParseCancellationException(e);
      }
      super.recover(recognizer, e);
    }

    @Override
    public Token recoverInline(final Parser recognizer) throws RecognitionException {
      if (++errorCount >= maxErrors) {
        throw new ParseCancellationException(new InputMismatchException(recognizer));
      }
      return super.recoverInline(recognizer);
    }
  }

  /**
   * Routes ANTLR syntax-error messages to SLF4J at {@code WARN} level instead of the default
   * console output, and includes the file name for traceability.
   */
  static final class LoggingErrorListener extends BaseErrorListener {

    private final Logger logger;
    private final String fileName;

    LoggingErrorListener(final Logger logger, final String fileName) {
      this.logger = logger;
      this.fileName = fileName;
    }

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
        final int line, final int charPositionInLine, final String msg,
        final RecognitionException e) {
      logger.warn("Parse error in {} at {}:{} — {}", fileName, line, charPositionInLine, msg);
    }
  }
}
