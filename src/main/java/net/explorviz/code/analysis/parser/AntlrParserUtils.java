package net.explorviz.code.analysis.parser;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.Interval;
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
 *
 * <p><b>Optional parse timeout</b>: callers may pass {@code parseTimeoutMs} so the entire SLL → LL
 * → lenient sequence cannot hang indefinitely. Large or ambiguous JS files (vendored libraries,
 * bundles) can stall in SLL alone; the timeout covers that stage as well. On timeout a
 * {@link ParseCancellationException} is thrown so callers can fall back to metrics-only analysis.
 *
 * <p>The timeout is enforced by a {@link DeadlineTokenStream} that checks a wall-clock deadline on
 * every lookahead/{@code consume}. ANTLR prediction does not respond to {@link Thread#interrupt()},
 * so a {@code Future.get(timeout)} + {@code cancel(true)} approach leaves a CPU-burning zombie
 * thread and can stall the whole analysis under parallel file processing.
 */
public final class AntlrParserUtils {

  /** Maximum number of syntax errors tolerated before aborting recovery. */
  static final int MAX_ERRORS = 50;

  /**
   * Default wall-clock budget for a full two-stage parse (SLL + LL + optional lenient). Large
   * vendored JS can hang in SLL for minutes without this bound.
   */
  public static final long DEFAULT_PARSE_TIMEOUT_MS = 5_000L;

  /**
   * Alias for {@link #DEFAULT_PARSE_TIMEOUT_MS}.
   *
   * @deprecated use {@link #DEFAULT_PARSE_TIMEOUT_MS}; kept for binary compatibility with callers
   *     that timed only the LL stage.
   */
  @Deprecated
  public static final long DEFAULT_LL_TIMEOUT_MS = DEFAULT_PARSE_TIMEOUT_MS;

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
    return parseTwoStage(parser, tokens, logger, fileName, null, 0L, parseCall);
  }

  /**
   * Same as {@link #parseTwoStage(Parser, CommonTokenStream, Logger, String, Supplier)} but aborts
   * the entire SLL → LL → lenient sequence if it exceeds {@code parseTimeoutMs} (milliseconds). A
   * value {@code <= 0} means no timeout. On timeout a {@link ParseCancellationException} is thrown
   * so callers can fall back to metrics-only analysis.
   */
  public static <T> T parseTwoStage(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final long parseTimeoutMs,
      final Supplier<T> parseCall) {
    return parseTwoStage(parser, tokens, logger, fileName, null, parseTimeoutMs, parseCall);
  }

  /**
   * Same as {@link #parseTwoStage(Parser, CommonTokenStream, Logger, String, Supplier)} but also
   * records syntax-error messages in {@code syntaxErrors} (for tests and diagnostics).
   */
  static <T> T parseTwoStage(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final List<String> syntaxErrors,
      final Supplier<T> parseCall) {
    return parseTwoStage(parser, tokens, logger, fileName, syntaxErrors, 0L, parseCall);
  }

  static <T> T parseTwoStage(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final List<String> syntaxErrors,
      final long parseTimeoutMs, final Supplier<T> parseCall) {
    if (parseTimeoutMs <= 0L) {
      return parseTwoStageUnbounded(parser, tokens, logger, fileName, syntaxErrors, parseCall);
    }

    final long deadlineNanos =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(parseTimeoutMs);
    final TokenStream previous = parser.getInputStream();
    final DeadlineTokenStream deadlineStream =
        new DeadlineTokenStream(tokens, deadlineNanos, parseTimeoutMs, fileName);
    parser.setTokenStream(deadlineStream);
    try {
      return parseTwoStageUnbounded(parser, tokens, logger, fileName, syntaxErrors, parseCall);
    } catch (ParseCancellationException e) {
      if (deadlineStream.timedOut()) {
        logger.warn("Parse timed out after {} ms for {}", parseTimeoutMs, fileName);
      }
      throw e;
    } finally {
      parser.setTokenStream(previous);
    }
  }

  private static <T> T parseTwoStageUnbounded(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final List<String> syntaxErrors,
      final Supplier<T> parseCall) {

    // Stage 1: SLL — fast, works for the vast majority of valid source files.
    parser.removeErrorListeners();
    parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
    parser.setErrorHandler(new org.antlr.v4.runtime.BailErrorStrategy());
    try {
      return parseCall.get();
    } catch (ParseCancellationException e) {
      if (isDeadlineTimeout(e)) {
        throw e;
      }
      // SLL found an ambiguity it cannot resolve; fall through to full LL.
    }

    // Stage 2: LL — handles all ambiguities; limit error recovery to avoid O(n²) worst case.
    tokens.seek(0);
    parser.reset();
    parser.getInterpreter().setPredictionMode(PredictionMode.LL);
    parser.setErrorHandler(new ThresholdBailErrorStrategy(MAX_ERRORS));
    parser.removeErrorListeners();
    parser.addErrorListener(new LoggingErrorListener(logger, fileName, syntaxErrors));
    try {
      return parseCall.get();
    } catch (ParseCancellationException e) {
      if (isDeadlineTimeout(e)) {
        throw e;
      }
      logger.warn("Strict parse failed for {}, retrying with lenient error recovery", fileName);
      return parseLenient(parser, tokens, logger, fileName, syntaxErrors, parseCall);
    }
  }

  private static boolean isDeadlineTimeout(final ParseCancellationException e) {
    final String message = e.getMessage();
    return message != null && message.startsWith("Parse timeout after ");
  }

  /**
   * Parses with full LL prediction and default error recovery, returning the best-effort parse
   * tree even when the source contains unexpanded macros or other non-standard constructs.
   */
  public static <T> T parseLenient(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final Supplier<T> parseCall) {
    return parseLenient(parser, tokens, logger, fileName, null, parseCall);
  }

  static <T> T parseLenient(final Parser parser, final CommonTokenStream tokens,
      final Logger logger, final String fileName, final List<String> syntaxErrors,
      final Supplier<T> parseCall) {
    tokens.seek(0);
    parser.reset();
    parser.getInterpreter().setPredictionMode(PredictionMode.LL);
    parser.setErrorHandler(new DefaultErrorStrategy());
    parser.removeErrorListeners();
    parser.addErrorListener(new LoggingErrorListener(logger, fileName, syntaxErrors));
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
    private final List<String> syntaxErrors;

    LoggingErrorListener(final Logger logger, final String fileName) {
      this(logger, fileName, null);
    }

    LoggingErrorListener(final Logger logger, final String fileName,
        final List<String> syntaxErrors) {
      this.logger = logger;
      this.fileName = fileName;
      this.syntaxErrors = syntaxErrors;
    }

    @Override
    public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
        final int line, final int charPositionInLine, final String msg,
        final RecognitionException e) {
      final String formatted =
          "Parse error in %s at %d:%d — %s".formatted(fileName, line, charPositionInLine, msg);
      if (syntaxErrors != null) {
        syntaxErrors.add(formatted);
      }
      logger.warn("Parse error in {} at {}:{} — {}", fileName, line, charPositionInLine, msg);
    }
  }

  /**
   * Token stream decorator that aborts the parse once a wall-clock deadline is exceeded.
   *
   * <p>Extends {@link CommonTokenStream} (rather than only implementing {@link TokenStream}) because
   * several language-specific parser bases cast {@code _input} to {@code CommonTokenStream} /
   * {@code BufferedTokenStream} for semantic predicates.
   *
   * <p>ANTLR adaptive prediction calls {@link #LT(int)} / {@link #LA(int)} millions of times during
   * catastrophic backtracking, so checking the deadline there stops the parse on the same thread
   * (no zombie {@code Future} workers).
   */
  static final class DeadlineTokenStream extends CommonTokenStream {

    private final CommonTokenStream input;
    private final long deadlineNanos;
    private final long timeoutMs;
    private final String fileName;
    private volatile boolean timedOut;

    DeadlineTokenStream(final CommonTokenStream input, final long deadlineNanos,
        final long timeoutMs, final String fileName) {
      super(input.getTokenSource());
      this.input = input;
      this.deadlineNanos = deadlineNanos;
      this.timeoutMs = timeoutMs;
      this.fileName = fileName;
    }

    boolean timedOut() {
      return timedOut;
    }

    private void checkDeadline() {
      if (timedOut || System.nanoTime() < deadlineNanos) {
        return;
      }
      timedOut = true;
      throw new ParseCancellationException(
          "Parse timeout after " + timeoutMs + " ms for " + fileName);
    }

    @Override
    public Token LT(final int k) {
      checkDeadline();
      return input.LT(k);
    }

    @Override
    public int LA(final int i) {
      checkDeadline();
      return input.LA(i);
    }

    @Override
    public void consume() {
      checkDeadline();
      input.consume();
    }

    @Override
    public Token get(final int index) {
      return input.get(index);
    }

    @Override
    public List<Token> get(final int start, final int stop) {
      return input.get(start, stop);
    }

    @Override
    public TokenSource getTokenSource() {
      return input.getTokenSource();
    }

    @Override
    public void setTokenSource(final TokenSource tokenSource) {
      input.setTokenSource(tokenSource);
    }

    @Override
    public List<Token> getTokens() {
      return input.getTokens();
    }

    @Override
    public List<Token> getTokens(final int start, final int stop) {
      return input.getTokens(start, stop);
    }

    @Override
    public List<Token> getTokens(final int start, final int stop, final Set<Integer> types) {
      return input.getTokens(start, stop, types);
    }

    @Override
    public List<Token> getTokens(final int start, final int stop, final int ttype) {
      return input.getTokens(start, stop, ttype);
    }

    @Override
    public List<Token> getHiddenTokensToRight(final int tokenIndex, final int channel) {
      return input.getHiddenTokensToRight(tokenIndex, channel);
    }

    @Override
    public List<Token> getHiddenTokensToRight(final int tokenIndex) {
      return input.getHiddenTokensToRight(tokenIndex);
    }

    @Override
    public List<Token> getHiddenTokensToLeft(final int tokenIndex, final int channel) {
      return input.getHiddenTokensToLeft(tokenIndex, channel);
    }

    @Override
    public List<Token> getHiddenTokensToLeft(final int tokenIndex) {
      return input.getHiddenTokensToLeft(tokenIndex);
    }

    @Override
    public void fill() {
      input.fill();
    }

    @Override
    public void reset() {
      input.reset();
    }

    @Override
    public int getNumberOfOnChannelTokens() {
      return input.getNumberOfOnChannelTokens();
    }

    @Override
    public String getText(final Interval interval) {
      return input.getText(interval);
    }

    @Override
    public String getText() {
      return input.getText();
    }

    @Override
    public String getText(final RuleContext ctx) {
      return input.getText(ctx);
    }

    @Override
    public String getText(final Token start, final Token stop) {
      return input.getText(start, stop);
    }

    @Override
    public int mark() {
      return input.mark();
    }

    @Override
    public void release(final int marker) {
      input.release(marker);
    }

    @Override
    public int index() {
      return input.index();
    }

    @Override
    public void seek(final int index) {
      input.seek(index);
    }

    @Override
    public int size() {
      return input.size();
    }

    @Override
    public String getSourceName() {
      return input.getSourceName();
    }
  }
}
