package net.explorviz.code.analysis.metrics.controlflow;

import java.math.BigInteger;

/**
 * Metrics of a function.
 *
 * @param cyclomaticComplexity the cyclomatic complexity
 * @param npathComplexity the NPath complexity
 * @param nestedBlockDepth the nested block depth, 0 without a body
 * @param halsteadVolume the Halstead volume, 0 without a body
 * @param linesOfCode the number of lines with code, 0 without a body
 */
public record FunctionMetrics(
    int cyclomaticComplexity,
    BigInteger npathComplexity,
    int nestedBlockDepth,
    double halsteadVolume,
    int linesOfCode) {

  /** Metrics of functions that are only declared. */
  public static final FunctionMetrics WITHOUT_BODY =
      new FunctionMetrics(1, BigInteger.ZERO, 0, 0, 0);

  /** Whether the function has a body. */
  public boolean hasBody() {
    return nestedBlockDepth > 0;
  }
}
