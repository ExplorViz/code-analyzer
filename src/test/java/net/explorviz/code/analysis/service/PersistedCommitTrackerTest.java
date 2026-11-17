package net.explorviz.code.analysis.service;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PersistedCommitTrackerTest {

  private static final String ROOT = "a".repeat(40);
  private static final String MAIN = "b".repeat(40);
  private static final String SIDE = "c".repeat(40);
  private static final String MERGE = "d".repeat(40);
  private static final String OUTSIDE = "e".repeat(40);

  @Test
  void diffsAgainstPersistedFirstParent() {
    final PersistedCommitTracker tracker = tracker(false, ROOT, MAIN);
    tracker.markPersisted(ROOT);

    Assertions.assertEquals(ROOT, tracker.resolveDiffBase(List.of(ROOT)));
    Assertions.assertEquals(List.of(ROOT), tracker.resolveReportedParents(List.of(ROOT), ROOT));
  }

  @Test
  void reportsRootCommitWithoutParents() {
    final PersistedCommitTracker tracker = tracker(false, ROOT);

    Assertions.assertNull(tracker.resolveDiffBase(List.of()));
    Assertions.assertEquals(List.of(), tracker.resolveReportedParents(List.of(), null));
  }

  @Test
  void diffsSkippedCommitsChildAgainstNearestPersistedAncestor() {
    final PersistedCommitTracker tracker = tracker(false, ROOT, MAIN, MERGE);
    tracker.markPersisted(ROOT);
    tracker.markSkipped(MAIN, tracker.resolveDiffBase(List.of(ROOT)));

    Assertions.assertEquals(ROOT, tracker.resolveDiffBase(List.of(MAIN)));
    Assertions.assertEquals(List.of(ROOT), tracker.resolveReportedParents(List.of(MAIN), ROOT));
  }

  @Test
  void mapsSkippedMergeParentsToTheirRepresentatives() {
    final PersistedCommitTracker tracker = tracker(false, ROOT, MAIN, SIDE, MERGE);
    tracker.markPersisted(ROOT);
    tracker.markPersisted(MAIN);
    tracker.markSkipped(SIDE, ROOT);

    Assertions.assertEquals(MAIN, tracker.resolveDiffBase(List.of(MAIN, SIDE)));
    Assertions.assertEquals(
        List.of(MAIN, ROOT), tracker.resolveReportedParents(List.of(MAIN, SIDE), MAIN));
  }

  @Test
  void deduplicatesParentsWithTheSameRepresentative() {
    final PersistedCommitTracker tracker = tracker(false, ROOT, MAIN, SIDE, MERGE);
    tracker.markPersisted(ROOT);
    tracker.markSkipped(MAIN, ROOT);
    tracker.markSkipped(SIDE, ROOT);

    Assertions.assertEquals(
        List.of(ROOT), tracker.resolveReportedParents(List.of(MAIN, SIDE), ROOT));
  }

  @Test
  void treatsCommitsOutsideWalkRangeAsPersistedWhenResuming() {
    final PersistedCommitTracker tracker = tracker(true, MAIN);

    Assertions.assertEquals(OUTSIDE, tracker.resolveDiffBase(List.of(OUTSIDE)));
  }

  @Test
  void treatsCommitsOutsideWalkRangeAsUnknownWithoutResuming() {
    final PersistedCommitTracker tracker = tracker(false, MAIN);

    Assertions.assertNull(tracker.resolveDiffBase(List.of(OUTSIDE)));
    Assertions.assertEquals(
        List.of(OUTSIDE), tracker.resolveReportedParents(List.of(OUTSIDE), null));
  }

  @Test
  void treatsUnprocessedCommitsInWalkRangeAsUnknownWhenResuming() {
    // e.g. commits excluded by the analysis limit
    final PersistedCommitTracker tracker = tracker(true, ROOT, MAIN);

    Assertions.assertNull(tracker.resolveDiffBase(List.of(ROOT)));
  }

  @Test
  void omitsPersistedParentsWhenTheDiffBaseIsNotUsable() {
    final PersistedCommitTracker tracker = tracker(true, MAIN);

    Assertions.assertEquals(List.of(), tracker.resolveReportedParents(List.of(OUTSIDE), null));
  }

  private static PersistedCommitTracker tracker(
      final boolean outsideWalkRangeIsPersisted, final String... commitsInRange) {
    final List<CommitWalkEntry> entries =
        java.util.Arrays.stream(commitsInRange)
            .map(hash -> new CommitWalkEntry(hash, 0, List.of()))
            .toList();
    return new PersistedCommitTracker(entries, outsideWalkRangeIsPersisted);
  }
}
