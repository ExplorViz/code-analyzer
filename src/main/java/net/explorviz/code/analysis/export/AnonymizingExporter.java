package net.explorviz.code.analysis.export;

import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;

/**
 * Decorates a {@link DataExporter} so that no personal data leaves the analyzer: contributor names
 * and emails are replaced by pseudonyms, GitHub logins and avatars are removed, and issue/PR events
 * (free text written by people) are not exported at all.
 */
public final class AnonymizingExporter implements DataExporter {

  private static final String EMAIL_DOMAIN = "@anonymized.invalid";

  private final DataExporter delegate;
  private final Pseudonymizer pseudonymizer;

  public AnonymizingExporter(final DataExporter delegate, final Pseudonymizer pseudonymizer) {
    this.delegate = delegate;
    this.pseudonymizer = pseudonymizer;
  }

  @Override
  public StateData getStateData(
      final String repositoryName,
      final String branchName,
      final String token,
      final Map<String, String> applicationPaths,
      final String repositoryUrl,
      final boolean skipLatestCommitLookup) {
    return delegate.getStateData(
        repositoryName, branchName, token, applicationPaths, repositoryUrl, skipLatestCommitLookup);
  }

  @Override
  public void persistFile(final FileData fileData) {
    delegate.persistFile(anonymize(fileData));
  }

  @Override
  public void persistFilesBatch(final List<FileData> files) {
    delegate.persistFilesBatch(files.stream().map(this::anonymize).toList());
  }

  @Override
  public void persistCommit(final CommitData commitData) {
    final CommitData.Builder builder = commitData.toBuilder();
    if (commitData.hasAuthor()) {
      builder.setAuthor(anonymize(commitData.getAuthor()));
    }
    delegate.persistCommit(builder.build());
  }

  @Override
  public void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent) {
    // Dropped: titles, descriptions and URLs of issues/PRs identify people.
  }

  @Override
  public void persistTrackableResourceEventBatch(final List<TrackableResourceEvent> events) {
    // Dropped, see persistTrackableResourceEvent.
  }

  @Override
  public boolean isRemote() {
    return delegate.isRemote();
  }

  @Override
  public boolean isInvalidCommitHash(final String hash) {
    return delegate.isInvalidCommitHash(hash);
  }

  private FileData anonymize(final FileData fileData) {
    if (fileData.getLastEditor().isEmpty()) {
      return fileData;
    }
    return fileData.toBuilder().setLastEditor(email(fileData.getLastEditor())).build();
  }

  private ContributorData anonymize(final ContributorData contributor) {
    final String identity =
        contributor.getEmail().isBlank() ? contributor.getGitUsername() : contributor.getEmail();
    return contributor.toBuilder()
        .setGitUsername("Contributor " + pseudonymizer.pseudonym(identity))
        .setEmail(email(identity))
        .clearContributorId()
        .clearGithubLogin()
        .clearAvatarUrl()
        .build();
  }

  /** Same input as {@link #anonymize(ContributorData)} so file editors match commit authors. */
  private String email(final String email) {
    return pseudonymizer.pseudonym(email) + EMAIL_DOMAIN;
  }
}
