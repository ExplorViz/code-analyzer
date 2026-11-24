package net.explorviz.code.analysis.metrics;

/** Names of the code metrics that are attached to functions, classes and files. */
public final class CodeMetrics {

  /** Function: McCabe complexity. Class and file: sum over the contained functions. */
  public static final String CYCLOMATIC_COMPLEXITY = "cyclomatic_complexity";

  /** Class and file: average cyclomatic complexity of the contained functions. */
  public static final String CYCLOMATIC_COMPLEXITY_WEIGHTED = "cyclomatic_complexity_weighted";

  /** Function: maximum nesting of control structures. */
  public static final String NESTED_BLOCK_DEPTH = "nestedBlockDepth";

  /** Function: number of acyclic execution paths. */
  public static final String NPATH_COMPLEXITY = "npath_complexity";

  /** Class: lack of cohesion of methods, i.e. the number of unrelated groups of methods. */
  public static final String LCOM4 = "LCOM4";

  private CodeMetrics() {}
}
