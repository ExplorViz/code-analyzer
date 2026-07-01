package net.explorviz.code.analysis.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reports analysis progress on an interactive terminal via an in-place progress bar, or as log
 * milestones when no interactive terminal is available (e.g. Docker, CI).
 */
@ApplicationScoped
public class TerminalProgressReporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(TerminalProgressReporter.class);

  private static final String STATUS_RUNNING = "running";
  private static final String STATUS_FINISHED = "finished";
  private static final String STATUS_FAILED = "failed";
  private static final int BAR_WIDTH = 30;
  private static final long MIN_RENDER_INTERVAL_NANOS = 100_000_000L;
  private static final int[] FILE_MILESTONE_PERCENTS = {25, 50, 75};

  private final boolean interactive;
  private final Object renderLock = new Object();

  private boolean active;
  private long lastRenderNanos;
  private int lastRenderedCommit = -1;
  private int lastRenderedTotalFiles = -1;

  private boolean loggedAnalysisStart;
  private int lastSeenTotalFiles = -1;
  private int lastLoggedFilePercent = -1;
  private int lastLoggedCompletedCommit = -1;

  TerminalProgressReporter() {
    this.interactive = isInteractiveTerminal();
  }

  public void update(final AnalysisProgressState state) {
    if (state == null) {
      return;
    }

    synchronized (renderLock) {
      if (interactive) {
        updateInteractive(state);
      } else {
        updateMilestones(state);
      }
    }
  }

  static String formatCommitLine(final AnalysisProgressState state) {
    final int totalCommits = Math.max(0, state.totalCommits());
    final int displayedCommit = resolveDisplayedCommit(state);
    return "Commit " + displayedCommit + "/" + totalCommits;
  }

  static String formatFileLine(final AnalysisProgressState state) {
    final int totalFiles = Math.max(0, state.totalFiles());
    final int analyzedFiles = Math.min(Math.max(0, state.analyzedFiles()), totalFiles);
    final int percent = totalFiles == 0 ? 100 : (analyzedFiles * 100) / totalFiles;
    return "Files " + renderBar(analyzedFiles, totalFiles) + " "
        + analyzedFiles + "/" + totalFiles + " (" + percent + "%)";
  }

  static String formatAnalysisStartedMessage(final AnalysisProgressState state) {
    return "Starting analysis of " + state.totalCommits() + " commits";
  }

  static String formatCommitStartedMessage(final AnalysisProgressState state) {
    final int totalCommits = Math.max(0, state.totalCommits());
    final int currentCommit = resolveDisplayedCommit(state);
    if (state.totalFiles() == 0) {
      return "Commit " + currentCommit + "/" + totalCommits + ": no files to analyze";
    }
    return "Commit " + currentCommit + "/" + totalCommits + ": analyzing " + state.totalFiles() + " files";
  }

  static String formatFileMilestoneMessage(final AnalysisProgressState state, final int milestonePercent) {
    final int totalCommits = Math.max(0, state.totalCommits());
    final int totalFiles = Math.max(0, state.totalFiles());
    final int analyzedFiles = Math.min(Math.max(0, state.analyzedFiles()), totalFiles);
    return "Commit " + resolveDisplayedCommit(state) + "/" + totalCommits + ": files "
        + analyzedFiles + "/" + totalFiles + " (" + milestonePercent + "%)";
  }

  static String formatCommitCompletedMessage(final AnalysisProgressState state) {
    return "Commit " + state.analyzedCommits() + "/" + state.totalCommits() + " completed";
  }

  static String formatAnalysisFinishedMessage(final AnalysisProgressState state) {
    return "Analysis finished: " + state.totalCommits() + "/" + state.totalCommits() + " commits";
  }

  static String formatAnalysisFailedMessage(final AnalysisProgressState state) {
    final int totalCommits = Math.max(0, state.totalCommits());
    if (totalCommits == 0) {
      return "Analysis failed";
    }
    final int failedAtCommit = Math.min(Math.max(0, state.analyzedCommits()) + 1, totalCommits);
    return "Analysis failed at commit " + failedAtCommit + "/" + totalCommits;
  }

  static int resolveFilePercent(final AnalysisProgressState state) {
    final int totalFiles = Math.max(0, state.totalFiles());
    if (totalFiles == 0) {
      return 100;
    }
    final int analyzedFiles = Math.min(Math.max(0, state.analyzedFiles()), totalFiles);
    return (analyzedFiles * 100) / totalFiles;
  }

  static int nextFileMilestonePercent(final int filePercent, final int lastLoggedPercent) {
    for (final int milestone : FILE_MILESTONE_PERCENTS) {
      if (filePercent >= milestone && lastLoggedPercent < milestone) {
        return milestone;
      }
    }
    return -1;
  }

  private static int resolveDisplayedCommit(final AnalysisProgressState state) {
    final int totalCommits = Math.max(0, state.totalCommits());
    if (totalCommits == 0) {
      return 0;
    }
    if (STATUS_FINISHED.equals(state.status())) {
      return totalCommits;
    }
    if (STATUS_RUNNING.equals(state.status())) {
      return Math.min(Math.max(0, state.analyzedCommits()) + 1, totalCommits);
    }
    return Math.min(Math.max(0, state.analyzedCommits()), totalCommits);
  }

  private static String renderBar(final int analyzedFiles, final int totalFiles) {
    final int filled = totalFiles == 0
        ? BAR_WIDTH
        : Math.min(BAR_WIDTH, (int) Math.round((double) analyzedFiles * BAR_WIDTH / totalFiles));
    return "[" + "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled) + "]";
  }

  static boolean isInteractiveTerminal() {
    if ("true".equalsIgnoreCase(System.getenv("EXPLORVIZ_FORCE_PROGRESS"))) {
      return true;
    }
    if ("false".equalsIgnoreCase(System.getenv("EXPLORVIZ_FORCE_PROGRESS"))) {
      return false;
    }
    if (System.console() != null) {
      return true;
    }
    if (isCiEnvironment()) {
      return false;
    }
    final String term = System.getenv("TERM");
    return term != null && !term.isBlank() && !"dumb".equalsIgnoreCase(term);
  }

  private static boolean isCiEnvironment() {
    return isTruthyEnv("CI")
        || isTruthyEnv("GITLAB_CI")
        || isTruthyEnv("GITHUB_ACTIONS")
        || isTruthyEnv("JENKINS_URL")
        || isTruthyEnv("BUILDKITE");
  }

  private static boolean isTruthyEnv(final String name) {
    final String value = System.getenv(name);
    return value != null && !value.isBlank() && !"false".equalsIgnoreCase(value) && !"0".equals(value);
  }

  private void updateInteractive(final AnalysisProgressState state) {
    if (STATUS_FINISHED.equals(state.status()) || STATUS_FAILED.equals(state.status())) {
      renderFinalState(state);
      finish();
      return;
    }

    if (!STATUS_RUNNING.equals(state.status())) {
      return;
    }

    if (!shouldRender(state)) {
      return;
    }

    renderRunningState(state);
  }

  private void updateMilestones(final AnalysisProgressState state) {
    if (STATUS_FINISHED.equals(state.status())) {
      LOGGER.info(formatAnalysisFinishedMessage(state));
      resetMilestoneTracking();
      return;
    }

    if (STATUS_FAILED.equals(state.status())) {
      LOGGER.warn(formatAnalysisFailedMessage(state));
      resetMilestoneTracking();
      return;
    }

    if (!STATUS_RUNNING.equals(state.status())) {
      return;
    }

    if (!loggedAnalysisStart && state.totalCommits() > 0) {
      LOGGER.info(formatAnalysisStartedMessage(state));
      loggedAnalysisStart = true;
    }

    if (state.totalFiles() != lastSeenTotalFiles && state.analyzedFiles() == 0) {
      LOGGER.info(formatCommitStartedMessage(state));
      lastLoggedFilePercent = -1;
    }

    if (state.totalFiles() > 0) {
      final int milestone = nextFileMilestonePercent(resolveFilePercent(state), lastLoggedFilePercent);
      if (milestone > 0) {
        LOGGER.info(formatFileMilestoneMessage(state, milestone));
        lastLoggedFilePercent = milestone;
      }
    }

    if (state.analyzedCommits() > lastLoggedCompletedCommit) {
      LOGGER.info(formatCommitCompletedMessage(state));
      lastLoggedCompletedCommit = state.analyzedCommits();
      lastLoggedFilePercent = -1;
    }

    lastSeenTotalFiles = state.totalFiles();
  }

  private void resetMilestoneTracking() {
    loggedAnalysisStart = false;
    lastSeenTotalFiles = -1;
    lastLoggedFilePercent = -1;
    lastLoggedCompletedCommit = -1;
  }

  private boolean shouldRender(final AnalysisProgressState state) {
    final long now = System.nanoTime();
    final boolean commitBoundaryChanged = state.analyzedCommits() != lastRenderedCommit
        || state.totalFiles() != lastRenderedTotalFiles;
    if (!active || commitBoundaryChanged) {
      return true;
    }
    return now - lastRenderNanos >= MIN_RENDER_INTERVAL_NANOS;
  }

  private void renderRunningState(final AnalysisProgressState state) {
    writeProgressLines(formatCommitLine(state), formatFileLine(state));
    rememberRender(state);
  }

  private void renderFinalState(final AnalysisProgressState state) {
    if (STATUS_FINISHED.equals(state.status())) {
      final AnalysisProgressState completed = new AnalysisProgressState(
          STATUS_FINISHED,
          state.totalCommits(),
          state.totalCommits(),
          state.totalFiles(),
          state.totalFiles(),
          null);
      writeProgressLines(formatCommitLine(completed), formatFileLine(completed));
      rememberRender(completed);
      return;
    }
    if (active) {
      writeProgressLines(formatCommitLine(state), formatFileLine(state) + " - failed");
      rememberRender(state);
    }
  }

  private void writeProgressLines(final String commitLine, final String fileLine) {
    if (active) {
      System.err.print("\033[2A");
    } else {
      System.err.print("\033[?25l");
    }
    System.err.print("\r\033[K" + commitLine + "\n\r\033[K" + fileLine);
    System.err.flush();
    active = true;
  }

  private void rememberRender(final AnalysisProgressState state) {
    lastRenderNanos = System.nanoTime();
    lastRenderedCommit = state.analyzedCommits();
    lastRenderedTotalFiles = state.totalFiles();
  }

  private void finish() {
    if (!active) {
      return;
    }
    System.err.print("\n\033[?25h");
    System.err.flush();
    active = false;
    lastRenderedCommit = -1;
    lastRenderedTotalFiles = -1;
  }
}
