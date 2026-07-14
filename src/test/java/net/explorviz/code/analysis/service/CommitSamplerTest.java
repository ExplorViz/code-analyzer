package net.explorviz.code.analysis.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommitSamplerTest {

  @Test
  void selectsEveryNthCommitStartingAtFirst() {
    final AnalysisConfig config = configWithInterval(3);

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), config);

    Assertions.assertEquals(Set.of(0, 3, 6, 9), indices);
  }

  @Test
  void intervalSamplingDisabledWhenIntervalIsOne() {
    final AnalysisConfig config = configWithInterval(1);

    Assertions.assertFalse(CommitSampler.isEnabled(config));
    Assertions.assertEquals(
        Set.of(0, 1, 2),
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(List.of(1, 2, 3), config));
  }

  @Test
  void selectsFirstCommitPerDay() {
    final AnalysisConfig config = configWithPeriod(CommitSamplingPeriod.DAY);
    final List<Integer> commitTimes =
        List.of(
            epochSeconds("2024-01-01T10:00:00Z"),
            epochSeconds("2024-01-01T18:00:00Z"),
            epochSeconds("2024-01-02T09:00:00Z"),
            epochSeconds("2024-01-03T09:00:00Z"));

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(commitTimes, config);

    Assertions.assertEquals(Set.of(0, 2, 3), indices);
  }

  @Test
  void selectsFirstCommitPerWeek() {
    final AnalysisConfig config = configWithPeriod(CommitSamplingPeriod.WEEK);
    final List<Integer> commitTimes =
        List.of(
            epochSeconds("2024-01-01T10:00:00Z"),
            epochSeconds("2024-01-03T10:00:00Z"),
            epochSeconds("2024-01-08T10:00:00Z"));

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(commitTimes, config);

    Assertions.assertEquals(Set.of(0, 2), indices);
  }

  @Test
  void selectsFirstCommitPerMonth() {
    final AnalysisConfig config = configWithPeriod(CommitSamplingPeriod.MONTH);
    final List<Integer> commitTimes =
        List.of(
            epochSeconds("2024-01-15T10:00:00Z"),
            epochSeconds("2024-01-20T10:00:00Z"),
            epochSeconds("2024-02-01T10:00:00Z"));

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(commitTimes, config);

    Assertions.assertEquals(Set.of(0, 2), indices);
  }

  @Test
  void selectsFirstCommitPerYear() {
    final AnalysisConfig config = configWithPeriod(CommitSamplingPeriod.YEAR);
    final List<Integer> commitTimes =
        List.of(
            epochSeconds("2023-12-31T10:00:00Z"),
            epochSeconds("2024-01-01T10:00:00Z"),
            epochSeconds("2025-01-01T10:00:00Z"));

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(commitTimes, config);

    Assertions.assertEquals(Set.of(0, 1, 2), indices);
  }

  @Test
  void intervalSamplingTakesPrecedenceOverPeriod() {
    final AnalysisConfig config = new AnalysisConfig.Builder()
        .commitSamplingInterval(Optional.of(4))
        .commitSamplingPeriod(Optional.of(CommitSamplingPeriod.DAY))
        .build();

    final Set<Integer> indices =
        CommitSampler.selectFullyAnalyzedIndicesFromCommitTimes(
            List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), config);

    Assertions.assertEquals(Set.of(0, 4, 8), indices);
  }

  private static AnalysisConfig configWithInterval(final int interval) {
    return new AnalysisConfig.Builder()
        .commitSamplingInterval(Optional.of(interval))
        .build();
  }

  private static AnalysisConfig configWithPeriod(final CommitSamplingPeriod period) {
    return new AnalysisConfig.Builder()
        .commitSamplingPeriod(Optional.of(period))
        .build();
  }

  private static int epochSeconds(final String isoInstant) {
    return (int) Instant.parse(isoInstant).getEpochSecond();
  }
}
