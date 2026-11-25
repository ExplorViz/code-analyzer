package net.explorviz.code.analysis.handler;

import java.util.OptionalDouble;

/** Parses the values of metrics. */
final class MetricParser {

  private MetricParser() {}

  /** Returns the value, or nothing if it is not a number. Hence, the metric has no value then. */
  static OptionalDouble parse(final String value) {
    try {
      final double number = Double.parseDouble(value);
      return Double.isFinite(number) ? OptionalDouble.of(number) : OptionalDouble.empty();
    } catch (NumberFormatException e) {
      return OptionalDouble.empty();
    }
  }
}
