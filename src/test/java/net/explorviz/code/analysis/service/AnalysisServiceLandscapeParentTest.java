package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class AnalysisServiceLandscapeParentTest {

  @Inject AnalysisService analysisService;

  @Test
  void usesLastAnalyzedCommitAsPrimaryParentForLandscapeCopy() {
    final RevCommit lastAnalyzed = commitWithName("analyzed-parent");
    final RevCommit gitParent = commitWithName("git-parent-0");
    final RevCommit gitParent2 = commitWithName("git-parent-1");
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(2);
    Mockito.doReturn(gitParent).when(commit).getParent(0);
    Mockito.doReturn(gitParent2).when(commit).getParent(1);

    final List<String> parentIds =
        analysisService.resolveLandscapeParentCommitIds(commit, lastAnalyzed);

    Assertions.assertEquals(
        List.of("analyzed-parent", "git-parent-0", "git-parent-1"), parentIds);
  }

  @Test
  void fallsBackToGitParentsForBootstrapCommit() {
    final RevCommit gitParent = commitWithName("git-parent-0");
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(1);
    Mockito.doReturn(gitParent).when(commit).getParent(0);

    final List<String> parentIds =
        analysisService.resolveLandscapeParentCommitIds(commit, null);

    Assertions.assertEquals(List.of("git-parent-0"), parentIds);
  }

  private static RevCommit commitWithName(final String name) {
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getName()).thenReturn(name);
    return commit;
  }
}
