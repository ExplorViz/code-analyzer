package net.explorviz.code.analysis.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies that tags are attached to the commits they point to. */
class AnalysisServiceTagTest {

  @TempDir Path repoDir;

  @Test
  void mapsLightweightAndAnnotatedTagsToTheirCommits() throws Exception {
    try (Git git = Git.init().setDirectory(repoDir.toFile()).call()) {
      final RevCommit first = commitFile(git, "first");
      git.tag().setName("lightweight").setAnnotated(false).call();
      final RevCommit second = commitFile(git, "second");
      git.tag().setName("annotated").setAnnotated(true).setMessage("release").call();

      final Map<ObjectId, List<String>> tagsByCommitId =
          AnalysisService.buildTagsByCommitId(git.getRepository());

      Assertions.assertEquals(List.of("refs/tags/lightweight"), tagsByCommitId.get(first.getId()));
      Assertions.assertEquals(List.of("refs/tags/annotated"), tagsByCommitId.get(second.getId()));
      Assertions.assertEquals(2, tagsByCommitId.size());
    }
  }

  private RevCommit commitFile(final Git git, final String content) throws Exception {
    Files.writeString(repoDir.resolve("file.txt"), content);
    git.add().addFilepattern("file.txt").call();
    return git.commit()
        .setMessage(content)
        .setAuthor("Test", "test@example.com")
        .setCommitter("Test", "test@example.com")
        .setSign(false)
        .call();
  }
}
