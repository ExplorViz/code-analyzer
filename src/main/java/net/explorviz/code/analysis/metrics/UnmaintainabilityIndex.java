package net.explorviz.code.analysis.metrics;

import java.util.OptionalDouble;
import java.util.function.BiConsumer;

/**
 * Unmaintainability index of one or several functions: 100 minus the maintainability index on the
 * scale from 0 to 100. Hence, 0 is maintainable and 100 is unmaintainable.
 *
 * <p>The maintainability index (Oman and Hagemeister, 1992; Coleman et al., 1994) without its
 * optional term for comments uses the averages of the Halstead volume, the cyclomatic complexity
 * and the lines of code per function. It is scaled to 100 and limited as by Visual Studio.
 */
public final class UnmaintainabilityIndex {

  private static final double SCALE = 100;
  private static final double MAXIMUM = 171;
  private static final double VOLUME_WEIGHT = 5.2;
  private static final double COMPLEXITY_WEIGHT = 0.23;
  private static final double LINES_WEIGHT = 16.2;

  private double volume;
  private long complexity;
  private long lines;
  private int functionCount;

  /** Adds a function. */
  public void add(
      final double halsteadVolume, final int cyclomaticComplexity, final int linesOfCode) {
    volume += halsteadVolume;
    complexity += cyclomaticComplexity;
    lines += linesOfCode;
    functionCount++;
  }

  /** Returns the index of the added functions, which does not exist without functions. */
  public OptionalDouble value() {
    if (functionCount == 0) {
      return OptionalDouble.empty();
    }
    final double maintainability =
        MAXIMUM
            - VOLUME_WEIGHT * logarithm(volume / functionCount)
            - COMPLEXITY_WEIGHT * complexity / functionCount
            - LINES_WEIGHT * logarithm((double) lines / functionCount);
    return OptionalDouble.of(SCALE - Math.clamp(maintainability * SCALE / MAXIMUM, 0, SCALE));
  }

  /**
   * Attaches the index if it exists.
   *
   * @param target receives the name and value of the metric
   */
  public void attachTo(final BiConsumer<String, String> target) {
    value()
        .ifPresent(
            index -> target.accept(CodeMetrics.UNMAINTAINABILITY_INDEX, String.valueOf(index)));
  }

  private static double logarithm(final double value) {
    // StrictMath gives identical results on every CPU; Math.log may differ in the last digits.
    return StrictMath.log(Math.max(1, value));
  }
}
