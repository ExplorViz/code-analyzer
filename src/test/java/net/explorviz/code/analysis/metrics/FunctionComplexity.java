package net.explorviz.code.analysis.metrics;

import net.explorviz.code.proto.FunctionData;

/** Metrics of a function, whose nested block depth is 0 if there is none. */
record FunctionComplexity(double cyclomatic, double npath, double nestedBlockDepth) {

  static FunctionComplexity of(final FunctionData function) {
    return new FunctionComplexity(
        function.getMetricsOrThrow(CodeMetrics.CYCLOMATIC_COMPLEXITY),
        function.getMetricsOrThrow(CodeMetrics.NPATH_COMPLEXITY),
        function.getMetricsOrDefault(CodeMetrics.NESTED_BLOCK_DEPTH, 0));
  }

  /** Returns the metrics of a Java method with the given statements. */
  static FunctionComplexity ofJava(final String statements) {
    return of(
        TestLanguage.JAVA
            .parse("class A { Object m(Object o) { " + statements + " } }")
            .getClasses(0)
            .getFunctions(0));
  }
}
