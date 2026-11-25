package net.explorviz.code.analysis.metrics;

/** Names of the code metrics that are attached to functions, classes and files. */
public final class CodeMetrics {

  /** Function: McCabe complexity. Class and file: sum over the contained functions. */
  public static final String CYCLOMATIC_COMPLEXITY = "cyclomatic_complexity";

  /** Class and file: average cyclomatic complexity of the contained functions. */
  public static final String CYCLOMATIC_COMPLEXITY_WEIGHTED = "cyclomatic_complexity_weighted";

  /** Function: maximum nesting of control structures. File: maximum of the contained functions. */
  public static final String NESTED_BLOCK_DEPTH = "nestedBlockDepth";

  /** Function: number of acyclic execution paths. File: maximum of the contained functions. */
  public static final String NPATH_COMPLEXITY = "npath_complexity";

  /**
   * Class: lack of cohesion of methods, i.e. the number of unrelated groups of methods. File:
   * maximum of the contained classes.
   */
  public static final String LCOM4 = "LCOM4";

  /**
   * Function, class and file: inverse of the maintainability index, from 0 (maintainable) to 100.
   */
  public static final String UNMAINTAINABILITY_INDEX = "unmaintainability_index";

  private CodeMetrics() {}
}
