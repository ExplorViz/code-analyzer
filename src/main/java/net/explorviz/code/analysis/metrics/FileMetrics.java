package net.explorviz.code.analysis.metrics;

import net.explorviz.code.analysis.handler.AbstractFileDataHandler;

/**
 * Metrics of a file that are derived from its functions and classes: sum and average of the
 * cyclomatic complexities, the maximum of the other metrics.
 */
public final class FileMetrics {

  /**
   * Limit for the NPath complexity of files. The values of all files are summed up for commits,
   * where a single huge value would exceed the precision of a double and hide all other files.
   */
  public static final double MAX_NPATH_COMPLEXITY = 1_000_000_000;

  private final ComplexityAggregate complexity = new ComplexityAggregate();
  private double npathComplexity;
  private int nestedBlockDepth;
  private int lcom4;

  /**
   * Adds a function.
   *
   * @param cyclomaticComplexity the cyclomatic complexity
   * @param npath the NPath complexity
   * @param blockDepth the nested block depth, 0 without a body
   */
  public void addFunction(
      final int cyclomaticComplexity, final double npath, final int blockDepth) {
    complexity.add(cyclomaticComplexity);
    npathComplexity = Math.max(npathComplexity, Math.min(npath, MAX_NPATH_COMPLEXITY));
    nestedBlockDepth = Math.max(nestedBlockDepth, blockDepth);
  }

  /** Adds a class. */
  public void addClass(final int classLcom4) {
    lcom4 = Math.max(lcom4, classLcom4);
  }

  /**
   * Attaches the metrics.
   *
   * @param file the file to attach the metrics to
   * @param hasCohesion whether the types of the language have LCOM4
   */
  public void attachTo(final AbstractFileDataHandler file, final boolean hasCohesion) {
    file.addMetric(CodeMetrics.CYCLOMATIC_COMPLEXITY, String.valueOf(complexity.sum()));
    file.addMetric(CodeMetrics.AVERAGE_CYCLOMATIC_COMPLEXITY, String.valueOf(complexity.average()));
    file.addMetric(CodeMetrics.NESTED_BLOCK_DEPTH, String.valueOf(nestedBlockDepth));
    file.addMetric(CodeMetrics.NPATH_COMPLEXITY, String.valueOf(npathComplexity));
    if (hasCohesion) {
      file.addMetric(CodeMetrics.LCOM4, String.valueOf(lcom4));
    }
  }
}
