package net.explorviz.code.analysis.metrics;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.explorviz.code.analysis.handler.AbstractFileDataHandler;

/**
 * Metrics of a file that are derived from its functions and classes: sum and average of the
 * cyclomatic complexities, the index of the average function, the maximum of the other metrics.
 * Metrics do not exist without functions or classes that have them.
 */
public final class FileMetrics {

  /**
   * Limit for the NPath complexity of files. The values of all files are summed up for commits,
   * where a single huge value would exceed the precision of a double and hide all other files.
   */
  public static final double MAX_NPATH_COMPLEXITY = 1_000_000_000;

  private final ComplexityAggregate complexity = new ComplexityAggregate();
  private final UnmaintainabilityIndex unmaintainability = new UnmaintainabilityIndex();
  private OptionalDouble npathComplexity = OptionalDouble.empty();
  private OptionalInt nestedBlockDepth = OptionalInt.empty();
  private OptionalInt lcom4 = OptionalInt.empty();

  /**
   * Adds a function.
   *
   * @param cyclomaticComplexity the cyclomatic complexity
   * @param npath the NPath complexity
   */
  public void addFunction(final int cyclomaticComplexity, final double npath) {
    complexity.add(cyclomaticComplexity);
    npathComplexity =
        OptionalDouble.of(
            Math.max(npathComplexity.orElse(0), Math.min(npath, MAX_NPATH_COMPLEXITY)));
  }

  /**
   * Adds the code of a function that has a body.
   *
   * @param blockDepth the nested block depth
   * @param halsteadVolume the Halstead volume
   * @param cyclomaticComplexity the cyclomatic complexity
   * @param linesOfCode the number of lines with code
   */
  public void addCode(
      final int blockDepth,
      final double halsteadVolume,
      final int cyclomaticComplexity,
      final int linesOfCode) {
    nestedBlockDepth = OptionalInt.of(Math.max(nestedBlockDepth.orElse(0), blockDepth));
    unmaintainability.add(halsteadVolume, cyclomaticComplexity, linesOfCode);
  }

  /** Adds a class that has methods with code. */
  public void addClass(final int classLcom4) {
    lcom4 = OptionalInt.of(Math.max(lcom4.orElse(0), classLcom4));
  }

  /** Attaches the metrics that exist. */
  public void attachTo(final AbstractFileDataHandler file) {
    complexity.attachTo(file::addMetric);
    nestedBlockDepth.ifPresent(
        depth -> file.addMetric(CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(depth)));
    npathComplexity.ifPresent(
        paths -> file.addMetric(CodeMetrics.NPATH_COMPLEXITY, String.valueOf(paths)));
    unmaintainability.attachTo(file::addMetric);
    lcom4.ifPresent(value -> file.addMetric(CodeMetrics.LCOM4, String.valueOf(value)));
  }
}
