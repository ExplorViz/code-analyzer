package net.explorviz.code.analysis.service;

import java.util.Locale;
import java.util.Optional;

/**
 * Calendar period for time-based commit sampling. The first commit in each period is fully
 * analyzed.
 */
public enum CommitSamplingPeriod {
  DAY,
  WEEK,
  MONTH,
  YEAR;

  /**
   * Parses a period name case-insensitively.
   *
   * @param value configured period name
   * @return the matching period, or empty when unset or unknown
   */
  public static Optional<CommitSamplingPeriod> fromConfigValue(final String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(CommitSamplingPeriod.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
