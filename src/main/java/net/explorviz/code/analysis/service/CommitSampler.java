package net.explorviz.code.analysis.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Selects which commits in a branch walk receive full file analysis while the remaining commits
 * are persisted as metadata-only entries.
 */
public final class CommitSampler {

  private CommitSampler() {}

  /**
   * Returns {@code true} when commit sampling is configured.
   */
  public static boolean isEnabled(final AnalysisConfig config) {
    return config.commitSamplingInterval().filter(interval -> interval > 1).isPresent()
        || config.commitSamplingPeriod().isPresent();
  }

  /**
   * Returns indices into {@code commits} that should receive full file analysis.
   *
   * @param commits commits in oldest-to-newest order
   * @param config analysis configuration
   * @return indices selected for full analysis
   */
  public static Set<Integer> selectFullyAnalyzedIndices(
      final List<RevCommit> commits, final AnalysisConfig config) {
    if (commits.isEmpty()) {
      return Set.of();
    }

    final List<Integer> commitTimes =
        commits.stream().map(RevCommit::getCommitTime).toList();
    return selectFullyAnalyzedIndicesFromCommitTimes(commitTimes, config);
  }

  static Set<Integer> selectFullyAnalyzedIndicesFromCommitTimes(
      final List<Integer> commitTimes, final AnalysisConfig config) {
    if (commitTimes.isEmpty()) {
      return Set.of();
    }

    final Optional<Integer> interval = config.commitSamplingInterval().filter(value -> value > 1);
    if (interval.isPresent()) {
      return selectEveryNthCommit(commitTimes.size(), interval.get());
    }

    final Optional<CommitSamplingPeriod> period = config.commitSamplingPeriod();
    if (period.isPresent()) {
      return selectFirstCommitPerPeriod(commitTimes, period.get());
    }

    final Set<Integer> allIndices = new HashSet<>(commitTimes.size());
    for (int index = 0; index < commitTimes.size(); index++) {
      allIndices.add(index);
    }
    return allIndices;
  }

  private static Set<Integer> selectEveryNthCommit(final int commitCount, final int interval) {
    final Set<Integer> indices = new HashSet<>();
    for (int index = 0; index < commitCount; index += interval) {
      indices.add(index);
    }
    return indices;
  }

  private static Set<Integer> selectFirstCommitPerPeriod(
      final List<Integer> commitTimes, final CommitSamplingPeriod period) {
    final Set<Integer> indices = new HashSet<>();
    Object lastPeriodKey = null;
    for (int index = 0; index < commitTimes.size(); index++) {
      final Object periodKey = periodKey(commitTimes.get(index), period);
      if (!periodKey.equals(lastPeriodKey)) {
        indices.add(index);
        lastPeriodKey = periodKey;
      }
    }
    return indices;
  }

  private static Object periodKey(final int commitTimeSeconds, final CommitSamplingPeriod period) {
    final Instant commitInstant = Instant.ofEpochSecond(commitTimeSeconds);
    return switch (period) {
      case DAY -> commitInstant.atZone(ZoneOffset.UTC).toLocalDate();
      case WEEK -> {
        final var zonedDateTime = commitInstant.atZone(ZoneOffset.UTC);
        final WeekFields weekFields = WeekFields.ISO;
        yield new WeekPeriod(
            zonedDateTime.get(weekFields.weekBasedYear()),
            zonedDateTime.get(weekFields.weekOfWeekBasedYear()));
      }
      case MONTH -> YearMonth.from(commitInstant.atZone(ZoneOffset.UTC));
      case YEAR -> commitInstant.atZone(ZoneOffset.UTC).getYear();
    };
  }

  private record WeekPeriod(int weekBasedYear, int weekOfYear) {}
}
