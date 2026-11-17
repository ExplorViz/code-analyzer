package net.explorviz.code.analysis.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks which commit known to the receiver (e.g. landscape-service) represents the file state of
 * each walked commit.
 *
 * <p>The receiver reconstructs a commit's file state by copying the files of the first listed
 * parent and applying the reported added, modified and deleted files. Every commit report must
 * therefore be diffed against exactly the commit that is listed as its first parent. This class
 * enforces that invariant, also when commits are skipped (sampling, irrelevant changes, analysis
 * limit) and when the walk contains merged side branches, i.e. when not only first-parent commits
 * are analyzed.
 *
 * <p>A commit's <em>representative</em> is
 *
 * <ul>
 *   <li>the commit itself when it was persisted,
 *   <li>the diff base it would have been compared against when it was skipped, so that a child of a
 *       skipped commit is diffed against the nearest persisted ancestor and receives the cumulative
 *       changes,
 *   <li>{@code null} when no commit known to the receiver represents it.
 * </ul>
 *
 * <p>Commits outside of the walk range are ancestors of the start commit. They are assumed to be
 * known to the receiver only when the analysis resumes from an already persisted start commit.
 */
final class PersistedCommitTracker {

  private final Set<String> commitsInWalkRange;
  private final boolean commitsOutsideWalkRangeArePersisted;

  /** Representative per walked commit; a {@code null} value means "no representative". */
  private final Map<String, String> representatives = new HashMap<>();

  /**
   * Creates a tracker.
   *
   * @param commitsInWalkRange hashes of all commits of the walk range, including commits that are
   *     not processed because of the analysis limit
   * @param commitsOutsideWalkRangeArePersisted whether ancestors of the start commit are already
   *     known to the receiver
   */
  /* package */ PersistedCommitTracker(
      final List<CommitWalkEntry> commitsInWalkRange,
      final boolean commitsOutsideWalkRangeArePersisted) {
    this.commitsInWalkRange = new HashSet<>(commitsInWalkRange.size() * 2);
    for (final CommitWalkEntry entry : commitsInWalkRange) {
      this.commitsInWalkRange.add(entry.hash());
    }
    this.commitsOutsideWalkRangeArePersisted = commitsOutsideWalkRangeArePersisted;
  }

  /**
   * Returns the hash of the commit known to the receiver that represents the given commit, or
   * {@code null} if there is none.
   */
  /* package */ String representativeOf(final String commitHash) {
    if (representatives.containsKey(commitHash)) {
      return representatives.get(commitHash);
    }
    if (commitsInWalkRange.contains(commitHash)) {
      // Not processed (yet): skipped because of the analysis limit.
      return null;
    }
    return commitsOutsideWalkRangeArePersisted ? commitHash : null;
  }

  /**
   * Returns the hash of the commit to diff against, which is the representative of the first git
   * parent, or {@code null} if the commit has to be reported with all of its files.
   */
  /* package */ String resolveDiffBase(final List<String> gitParentHashes) {
    if (gitParentHashes.isEmpty()) {
      return null;
    }
    return representativeOf(gitParentHashes.get(0));
  }

  /**
   * Returns the parent ids to report for a commit that was diffed against {@code diffBaseHash}.
   *
   * <p>The diff base is always reported as the first parent. Further git parents are mapped to
   * their representatives so that the commit tree does not reference skipped commits. Without a
   * diff base, all files are reported as added, so no commit known to the receiver may be listed as
   * first parent, as the receiver would otherwise inherit files of it.
   */
  /* package */ List<String> resolveReportedParents(
      final List<String> gitParentHashes, final String diffBaseHash) {
    if (gitParentHashes.isEmpty()) {
      return List.of();
    }
    if (diffBaseHash == null) {
      if (representativeOf(gitParentHashes.get(0)) != null) {
        // The representative exists but could not be used as diff base (e.g. missing locally).
        return List.of();
      }
      return List.copyOf(gitParentHashes);
    }

    final Set<String> parents = new LinkedHashSet<>();
    parents.add(diffBaseHash);
    for (int index = 1; index < gitParentHashes.size(); index++) {
      final String parentHash = gitParentHashes.get(index);
      final String representative = representativeOf(parentHash);
      parents.add(representative != null ? representative : parentHash);
    }
    return new ArrayList<>(parents);
  }

  /** Records that the commit was persisted. */
  /* package */ void markPersisted(final String commitHash) {
    representatives.put(commitHash, commitHash);
  }

  /**
   * Records that the commit was skipped. Its descendants are diffed against {@code diffBaseHash}
   * instead, which may be {@code null}.
   */
  /* package */ void markSkipped(final String commitHash, final String diffBaseHash) {
    representatives.put(commitHash, diffBaseHash);
  }
}
