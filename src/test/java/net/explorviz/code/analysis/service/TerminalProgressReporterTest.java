package net.explorviz.code.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerminalProgressReporterTest {

  @Test
  void formatCommitLineShowsCurrentCommitWhileRunning() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 1, 100, 5, "src/Foo.java");

    assertEquals("Commit 2/220", TerminalProgressReporter.formatCommitLine(state));
  }

  @Test
  void formatCommitLineShowsTotalWhenFinished() {
    final AnalysisProgressState state = new AnalysisProgressState("finished", 220, 220, 100, 100, null);

    assertEquals("Commit 220/220", TerminalProgressReporter.formatCommitLine(state));
  }

  @Test
  void formatFileLineShowsProgressBarAndPercentage() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 1, 100, 5, "src/Foo.java");

    assertEquals(
        "Files [##----------------------------] 5/100 (5%)",
        TerminalProgressReporter.formatFileLine(state));
  }

  @Test
  void formatFileLineHandlesEmptyCommit() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 1, 0, 0, null);

    assertEquals(
        "Files [##############################] 0/0 (100%)",
        TerminalProgressReporter.formatFileLine(state));
  }

  @Test
  void formatCommitStartedMessageForCommitWithFiles() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 1, 100, 0, null);

    assertEquals(
        "Commit 2/220: analyzing 100 files",
        TerminalProgressReporter.formatCommitStartedMessage(state));
  }

  @Test
  void formatCommitStartedMessageForEmptyCommit() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 5, 0, 0, null);

    assertEquals(
        "Commit 6/220: no files to analyze",
        TerminalProgressReporter.formatCommitStartedMessage(state));
  }

  @Test
  void formatFileMilestoneMessage() {
    final AnalysisProgressState state = new AnalysisProgressState("running", 220, 1, 100, 50, null);

    assertEquals(
        "Commit 2/220: files 50/100 (50%)",
        TerminalProgressReporter.formatFileMilestoneMessage(state, 50));
  }

  @Test
  void nextFileMilestonePercentReturnsFirstUnloggedMilestone() {
    assertEquals(25, TerminalProgressReporter.nextFileMilestonePercent(30, -1));
    assertEquals(50, TerminalProgressReporter.nextFileMilestonePercent(55, 25));
    assertEquals(-1, TerminalProgressReporter.nextFileMilestonePercent(10, -1));
    assertEquals(-1, TerminalProgressReporter.nextFileMilestonePercent(80, 75));
  }

  @Test
  void formatAnalysisFinishedMessage() {
    final AnalysisProgressState state = new AnalysisProgressState("finished", 220, 220, 0, 0, null);

    assertEquals("Analysis finished: 220/220 commits", TerminalProgressReporter.formatAnalysisFinishedMessage(state));
  }
}
