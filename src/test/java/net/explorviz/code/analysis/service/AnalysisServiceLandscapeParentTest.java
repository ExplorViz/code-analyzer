package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Constructor;
import java.util.List;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class AnalysisServiceLandscapeParentTest {

  @Inject AnalysisService analysisService;

  @Test
  void usesGitParentsForLandscapeTopology() {
    final RevCommit gitParent = commitWithId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final RevCommit gitParent2 = commitWithId("cccccccccccccccccccccccccccccccccccccccc");
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(2);
    Mockito.doReturn(gitParent).when(commit).getParent(0);
    Mockito.doReturn(gitParent2).when(commit).getParent(1);

    final List<String> parentIds = analysisService.resolveLandscapeParentCommitIds(commit);

    Assertions.assertEquals(
        List.of(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            "cccccccccccccccccccccccccccccccccccccccc"),
        parentIds);
  }

  @Test
  void detectsGapWhenSkippedCommitsSitBetweenLastAnalyzedAndCurrentCommit() {
    final RevCommit gitParent = commitWithId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(1);
    Mockito.doReturn(gitParent).when(commit).getParent(0);

    Assertions.assertTrue(
        analysisService.hasGapSinceLastFullAnalysis(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", commit));
  }

  @Test
  void hasNoGapForConsecutiveAnalyzedCommits() {
    final RevCommit gitParent = commitWithId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    final RevCommit commit = Mockito.mock(RevCommit.class);
    Mockito.when(commit.getParentCount()).thenReturn(1);
    Mockito.doReturn(gitParent).when(commit).getParent(0);

    Assertions.assertFalse(
        analysisService.hasGapSinceLastFullAnalysis(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", commit));
  }

  private static RevCommit commitWithId(final String objectId) {
    try {
      final Constructor<RevCommit> constructor =
          RevCommit.class.getDeclaredConstructor(AnyObjectId.class);
      constructor.setAccessible(true);
      return constructor.newInstance(ObjectId.fromString(objectId));
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Failed to create RevCommit test stub", exception);
    }
  }
}
