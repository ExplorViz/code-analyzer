package net.explorviz.code.analysis.metrics.java;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Number of paths through the code that was visited so far.
 *
 * <p>Reproduces the arithmetic of Checkstyle's {@code NPathComplexity}, where code without branches
 * has zero instead of one path. Constructs are entered with the paths that they add themselves.
 */
/* package */ final class NpathPaths {

  private final Deque<Outer> outers = new ArrayDeque<>();
  private BigInteger current = BigInteger.ZERO;
  private boolean isInBranch;

  /** Starts a construct, whose code is counted on its own until the construct is left. */
  /* package */ void enter(final int ownPaths) {
    outers.push(new Outer(current, BigInteger.valueOf(ownPaths)));
    current = BigInteger.ZERO;
  }

  /** Starts a case. */
  /* package */ void enterCase(final int labelCount) {
    isInBranch = true;
    enter(labelCount);
  }

  /** Starts an else branch. */
  /* package */ void enterElse() {
    isInBranch = true;
    current = atLeastOne(current);
    enter(0);
  }

  /** Leaves an if, loop, switch or guard. */
  /* package */ void leaveConditional() {
    final Outer outer = outers.pop();
    current = atLeastOne(current).add(outer.ownPaths).multiply(atLeastOne(outer.paths));
  }

  /** Leaves a return or ternary operator. */
  /* package */ void leaveExpression() {
    final Outer outer = outers.pop();
    current = current.add(atLeastOne(outer.ownPaths)).multiply(atLeastOne(outer.paths));
  }

  /** Leaves an else branch or case. */
  /* package */ void leaveBranch() {
    leaveDefault();
    isInBranch = false;
  }

  /** Leaves a default label. */
  /* package */ void leaveDefault() {
    final Outer outer = outers.pop();
    if (isInBranch) {
      current = atLeastOne(current);
    }
    current = current.subtract(BigInteger.ONE).add(outer.paths).add(outer.ownPaths);
  }

  /* package */ void leaveTry() {
    current = current.add(BigInteger.ONE).multiply(outers.pop().paths.add(BigInteger.ONE));
  }

  /* package */ void leaveCatch() {
    current = current.add(outers.pop().paths).add(BigInteger.ONE);
  }

  /** Leaves a function and returns its paths. The code around it starts from zero again. */
  /* package */ BigInteger leaveFunction() {
    final BigInteger result = current;
    outers.pop();
    current = BigInteger.ZERO;
    return result;
  }

  private static BigInteger atLeastOne(final BigInteger paths) {
    return paths.signum() == 0 ? BigInteger.ONE : paths;
  }

  private record Outer(BigInteger paths, BigInteger ownPaths) {}
}
