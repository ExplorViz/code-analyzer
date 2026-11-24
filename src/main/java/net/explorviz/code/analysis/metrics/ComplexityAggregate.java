package net.explorviz.code.analysis.metrics;

/** Sum and average of the cyclomatic complexities of several functions. */
public final class ComplexityAggregate {

  private long sum;
  private int functionCount;

  public void add(final int complexity) {
    sum += complexity;
    functionCount++;
  }

  public long sum() {
    return sum;
  }

  /** Returns the average complexity per function, or 0 if there are no functions. */
  public double average() {
    return functionCount == 0 ? 0.0 : (double) sum / functionCount;
  }
}
