package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusTest
class AnalysisServiceCommitRangeTest {

  @Inject AnalysisService analysisService;

  @Test
  void excludesOldestCommitWhenShallowCloneHasOneExtraCommit() {
    final AnalysisService.CommitRangeSelection selection =
        analysisService.resolveCommitRangeSelection(101, configWithLimit(100));

    Assertions.assertEquals(100, selection.commitsToAnalyze());
    Assertions.assertEquals(1, selection.commitsToSkipBeforeAnalyzing());
  }

  @Test
  void analyzesAllCommitsWhenRepositoryIsSmallerThanLimit() {
    final AnalysisService.CommitRangeSelection selection =
        analysisService.resolveCommitRangeSelection(50, configWithLimit(100));

    Assertions.assertEquals(50, selection.commitsToAnalyze());
    Assertions.assertEquals(0, selection.commitsToSkipBeforeAnalyzing());
  }

  @Test
  void analyzesNewestCommitsWhenFullCloneExceedsLimit() {
    final AnalysisService.CommitRangeSelection selection =
        analysisService.resolveCommitRangeSelection(910, configWithLimit(100));

    Assertions.assertEquals(100, selection.commitsToAnalyze());
    Assertions.assertEquals(810, selection.commitsToSkipBeforeAnalyzing());
  }

  @Test
  void excludesShallowBoundaryWhenParentIsMissingLocally(@TempDir final File tempDir)
      throws Exception {
    final File sourceDir = new File(tempDir, "source");
    final File shallowDir = new File(tempDir, "shallow");

    try (Git sourceGit = Git.init().setDirectory(sourceDir).call()) {
      writeAndCommit(sourceGit, "v1");
      writeAndCommit(sourceGit, "v2");
    }

    try (Git ignored =
        Git.cloneRepository()
            .setURI(sourceDir.toURI().toString())
            .setDirectory(shallowDir)
            .setDepth(1)
            .call()) {
      try (Git shallowGit = Git.open(shallowDir);
          Repository shallowRepository = shallowGit.getRepository()) {
        final RevCommit shallowTip = shallowGit.log().setMaxCount(1).call().iterator().next();

        Assertions.assertTrue(
            analysisService.isShallowBoundaryCommit(shallowRepository, shallowTip));
        Assertions.assertTrue(
            analysisService.shouldExcludeAsShallowCloneBoundary(shallowRepository, shallowTip));

        final List<CommitWalkEntry> commitsInRange =
            List.of(new CommitWalkEntry(shallowTip.getName(), shallowTip.getCommitTime()));

        final AnalysisService.CommitRangeSelection adjustedSelection =
            analysisService.adjustSkipForShallowCloneBoundary(
                shallowRepository,
                commitsInRange,
                new AnalysisService.CommitRangeSelection(1, 0),
                configWithLimit(1));

        Assertions.assertEquals(1, adjustedSelection.commitsToSkipBeforeAnalyzing());
        Assertions.assertEquals(0, adjustedSelection.commitsToAnalyze());
      }
    }
  }

  private static AnalysisConfig configWithLimit(final int limit) {
    return new AnalysisConfig.Builder().commitAnalysisLimit(Optional.of(limit)).build();
  }

  private static void writeAndCommit(final Git git, final String content) throws Exception {
    final File file = new File(git.getRepository().getWorkTree(), "README");
    Files.writeString(file.toPath(), content);
    git.add().addFilepattern("README").call();
    git.commit().setMessage("commit " + content).call();
  }
}
