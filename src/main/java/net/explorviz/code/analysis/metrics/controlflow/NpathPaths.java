package net.explorviz.code.analysis.metrics.controlflow;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Number of paths through the code that was visited so far.
 *
 * <p>Reproduces the arithmetic of Checkstyle's {@code NPathComplexity}, where code without branches
 * has zero instead of one path. Constructs are entered with the paths that they add themselves.
 */
public final class NpathPaths {

  private final Deque<Outer> outers = new ArrayDeque<>();
  private BigInteger current = BigInteger.ZERO;
  private boolean isInBranch;

  /** Starts a construct, whose code is counted on its own until the construct is left. */
  public void enter(final int ownPaths) {
    outers.push(new Outer(current, BigInteger.valueOf(ownPaths)));
    current = BigInteger.ZERO;
  }

  /** Starts a case. */
  public void enterCase(final int labelCount) {
    isInBranch = true;
    enter(labelCount);
  }

  /** Starts an else branch. */
  public void enterElse() {
    isInBranch = true;
    current = atLeastOne(current);
    enter(0);
  }

  /** Starts an else if branch that shares the construct of its if, instead of a nested if. */
  public void enterElseIf(final int conditionPaths) {
    isInBranch = true;
    current = atLeastOne(current);
    enter(1 + conditionPaths);
  }

  /** Leaves an if, loop, switch or guard. */
  public void leaveConditional() {
    final Outer outer = outers.pop();
    current = atLeastOne(current).add(outer.ownPaths).multiply(atLeastOne(outer.paths));
  }

  /** Leaves a return or ternary operator. */
  public void leaveExpression() {
    final Outer outer = outers.pop();
    current = current.add(atLeastOne(outer.ownPaths)).multiply(atLeastOne(outer.paths));
  }

  /** Leaves an else branch or case. */
  public void leaveBranch() {
    leaveDefault();
    isInBranch = false;
  }

  /** Leaves a default label. */
  public void leaveDefault() {
    final Outer outer = outers.pop();
    if (isInBranch) {
      current = atLeastOne(current);
    }
    current = current.subtract(BigInteger.ONE).add(outer.paths).add(outer.ownPaths);
  }

  public void leaveTry() {
    current = current.add(BigInteger.ONE).multiply(outers.pop().paths.add(BigInteger.ONE));
  }

  public void leaveCatch() {
    current = current.add(outers.pop().paths).add(BigInteger.ONE);
  }

  /** Leaves a function and returns its paths. The code around it starts from zero again. */
  public BigInteger leaveFunction() {
    final BigInteger result = current;
    outers.pop();
    current = BigInteger.ZERO;
    return result;
  }

  /** Returns the paths of the code since the innermost open construct was entered. */
  public BigInteger current() {
    return current;
  }

  private static BigInteger atLeastOne(final BigInteger paths) {
    return paths.signum() == 0 ? BigInteger.ONE : paths;
  }

  private record Outer(BigInteger paths, BigInteger ownPaths) {}
}
