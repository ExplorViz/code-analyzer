package net.explorviz.code.analysis.metrics.controlflow;

import java.util.ArrayDeque;
import java.util.Deque;
import net.explorviz.code.analysis.metrics.halstead.HalsteadVolume;
import net.explorviz.code.analysis.metrics.halstead.TokenGrammar;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/** Measurement of a function that is being walked. */
/* package */ final class FunctionState {

  /* package */ final NpathPaths paths = new NpathPaths();

  /** Volume of the code or {@code null} if there is no body. */
  private final HalsteadVolume volume;

  private int complexity = 1;
  private int depth = 1;
  private int maxDepth = 1;

  /** Number of open conditions and returned expressions, whose operators are counted up front. */
  private int measuredExpressions;

  /** First token of the else branch that was entered last. */
  private Token elseStart;

  /** Open constructs, innermost first. For a switch, whether one of its cases is open. */
  private final Deque<Switch> openConstructs = new ArrayDeque<>();

  /* package */ FunctionState(final boolean hasBody, final TokenGrammar tokens) {
    this.volume = hasBody ? new HalsteadVolume(tokens) : null;
  }

  /* package */ FunctionMetrics toMetrics() {
    return volume == null
        ? new FunctionMetrics(complexity, paths.current(), 0, 0, 0)
        : new FunctionMetrics(
            complexity, paths.current(), maxDepth, volume.value(), volume.lines());
  }

  /** Adds the tokens that are direct children of a rule of the function. */
  /* package */ void addTokens(final ParserRuleContext context) {
    if (volume != null) {
      volume.add(context);
    }
  }

  /* package */ void addDecisions(final int count) {
    complexity += count;
  }

  /* package */ void enterLevel() {
    depth++;
    maxDepth = Math.max(maxDepth, depth);
  }

  /* package */ void leaveLevel() {
    depth--;
  }

  /* package */ void enterMeasured() {
    measuredExpressions++;
  }

  /* package */ void leaveMeasured() {
    measuredExpressions--;
  }

  /* package */ boolean isMeasured() {
    return measuredExpressions > 0;
  }

  /* package */ void enterElse(final Token start) {
    elseStart = start;
    paths.enterElse();
  }

  /** Whether an if that starts with the given token directly follows an else. */
  /* package */ boolean isElseIf(final Token start) {
    return start != null && start == elseStart;
  }

  /* package */ void open(final boolean isSwitch) {
    openConstructs.push(new Switch(isSwitch));
  }

  /** Closes the innermost construct including its last case. */
  /* package */ void close() {
    if (openConstructs.pop().hasOpenCase) {
      paths.leaveBranch();
    }
  }

  /** Starts a case unless it is not directly inside of a switch, e.g. in a nested loop. */
  /* package */ void enterCase(final int ownPaths) {
    final Switch innermost = openConstructs.peek();
    if (innermost == null || !innermost.isSwitch) {
      return;
    }
    if (innermost.hasOpenCase) {
      paths.leaveBranch();
    }
    innermost.hasOpenCase = true;
    paths.enterCase(ownPaths);
  }

  private static final class Switch {
    private final boolean isSwitch;
    private boolean hasOpenCase;

    private Switch(final boolean isSwitch) {
      this.isSwitch = isSwitch;
    }
  }
}
