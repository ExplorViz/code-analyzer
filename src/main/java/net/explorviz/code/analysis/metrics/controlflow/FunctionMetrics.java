package net.explorviz.code.analysis.metrics.controlflow;

import java.math.BigInteger;

/**
 * Metrics of a function.
 *
 * @param cyclomaticComplexity the cyclomatic complexity
 * @param npathComplexity the NPath complexity
 * @param nestedBlockDepth the nested block depth, 0 without a body
 */
public record FunctionMetrics(
    int cyclomaticComplexity, BigInteger npathComplexity, int nestedBlockDepth) {

  /** Metrics of functions that are only declared. */
  public static final FunctionMetrics WITHOUT_BODY = new FunctionMetrics(1, BigInteger.ZERO, 0);
}
