package net.explorviz.code.analysis.metrics;

import java.util.function.BiConsumer;

/** Sum and average of the cyclomatic complexities of several functions. */
public final class ComplexityAggregate {

  private long sum;
  private int functionCount;

  public void add(final int complexity) {
    sum += complexity;
    functionCount++;
  }

  /**
   * Attaches sum and average, which do not exist without functions.
   *
   * @param target receives the names and values of the metrics
   */
  public void attachTo(final BiConsumer<String, String> target) {
    if (functionCount > 0) {
      target.accept(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(sum));
      target.accept(
          CodeMetrics.AVERAGE_CYCLOMATIC_COMPLEXITY, String.valueOf((double) sum / functionCount));
    }
  }
}
