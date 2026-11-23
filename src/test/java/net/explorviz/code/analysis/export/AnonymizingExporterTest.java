package net.explorviz.code.analysis.export;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AnonymizingExporterTest {

  private static final String NAME = "Jane Doe";
  private static final String EMAIL = "jane.doe@example.com";

  private final List<Object> exported = new ArrayList<>();
  private final AnonymizingExporter exporter =
      new AnonymizingExporter(
          new CapturingExporter(),
          new Pseudonymizer("test-secret".getBytes(StandardCharsets.UTF_8)));

  @Test
  void removesPersonalDataAndKeepsAuthorsAndEditorsLinked() {
    exporter.persistCommit(
        CommitData.newBuilder()
            .setCommitId("abc123")
            .setAuthor(
                ContributorData.newBuilder()
                    .setGitUsername(NAME)
                    .setEmail(EMAIL)
                    .setGithubLogin("jdoe")
                    .setAvatarUrl("https://avatars.example.com/jdoe"))
            .build());
    exporter.persistFilesBatch(
        List.of(FileData.newBuilder().setLastEditor(" Jane.Doe@Example.com ").build()));

    final CommitData commit = (CommitData) exported.get(0);
    final FileData file = (FileData) exported.get(1);
    final String output = exported.toString();

    for (final String leak : List.of(NAME, "Jane", "jdoe", "example.com", "avatars")) {
      Assertions.assertFalse(output.contains(leak), "leaked: " + leak);
    }
    Assertions.assertEquals("abc123", commit.getCommitId());
    Assertions.assertTrue(commit.getAuthor().getGitUsername().startsWith("Contributor "));
    Assertions.assertEquals(commit.getAuthor().getEmail(), file.getLastEditor());
  }

  @Test
  void dropsTrackableResourceEvents() {
    exporter.persistTrackableResourceEvent(
        TrackableResourceEvent.newBuilder().setTitle("Fix bug reported by Jane").build());
    exporter.persistTrackableResourceEventBatch(
        List.of(TrackableResourceEvent.newBuilder().build()));

    Assertions.assertTrue(exported.isEmpty());
  }

  @Test
  void pseudonymsDependOnTheSecret() {
    final Pseudonymizer other = new Pseudonymizer("other".getBytes(StandardCharsets.UTF_8));
    final Pseudonymizer same = new Pseudonymizer("test-secret".getBytes(StandardCharsets.UTF_8));

    Assertions.assertNotEquals(other.pseudonym(EMAIL), same.pseudonym(EMAIL));
    Assertions.assertEquals(
        same.pseudonym(EMAIL),
        new Pseudonymizer("test-secret".getBytes(StandardCharsets.UTF_8)).pseudonym(EMAIL));
  }

  private final class CapturingExporter implements DataExporter {

    @Override
    public StateData getStateData(
        final String repositoryName,
        final String branchName,
        final String token,
        final Map<String, String> applicationPaths,
        final String repositoryUrl,
        final boolean skipLatestCommitLookup) {
      return StateData.getDefaultInstance();
    }

    @Override
    public void persistFile(final FileData fileData) {
      exported.add(fileData);
    }

    @Override
    public void persistFilesBatch(final List<FileData> files) {
      exported.addAll(files);
    }

    @Override
    public void persistCommit(final CommitData commitData) {
      exported.add(commitData);
    }

    @Override
    public void persistTrackableResourceEvent(final TrackableResourceEvent event) {
      exported.add(event);
    }

    @Override
    public boolean isRemote() {
      return false;
    }

    @Override
    public boolean isInvalidCommitHash(final String hash) {
      return false;
    }
  }
}
