package net.explorviz.code.analysis.handler;

import com.google.protobuf.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileIdentifier;

/**
 * The CommitReportHandler is used to create commit reports.
 */
@ApplicationScoped
public class CommitReportHandler { // NOPMD

  private final List<FileIdentifier> addedFiles = new ArrayList<>();
  private final List<FileIdentifier> deletedFiles = new ArrayList<>();
  private final List<FileIdentifier> modifiedFiles = new ArrayList<>();
  private CommitData.Builder builder;
  private ContributorData.Builder contributorBuilder;

  /**
   * Creates a blank handler, use
   * {@link CommitReportHandler#init(String, java.util.List, String)} to initialize it.
   */
  public CommitReportHandler() {
    this.builder = CommitData.newBuilder();
    this.contributorBuilder = ContributorData.newBuilder();
  }

  /**
   * Clears the commitReportData from old data entries. Gets called in
   * {@link CommitReportHandler#init(String, java.util.List, String)} automatically.
   */
  public void clear() {
    this.builder = CommitData.newBuilder();
    this.contributorBuilder = ContributorData.newBuilder();
    this.addedFiles.clear();
    this.deletedFiles.clear();
    this.modifiedFiles.clear();
  }

  /**
   * Initialize the current report handler.
   *
   * @param commitId        the id of the commit
   * @param parentCommitIds git parent commit ids (may be empty for root commits)
   * @param branchName      the name of the branch
   */
  public void init(
      final String commitId, final List<String> parentCommitIds, final String branchName) {
    clear();
    builder.setCommitId(commitId);
    if (parentCommitIds == null || parentCommitIds.isEmpty()) {
      builder.setParentCommitId("NONE");
    } else {
      builder.setParentCommitId(parentCommitIds.get(0));
      builder.addAllParentCommitIds(parentCommitIds);
    }
    builder.setBranchName(branchName);
  }

  private String getFileHash(final FileDescriptor fileDescriptor) {
    return fileDescriptor.objectId.name();
  }

  private FileIdentifier toFileId(final FileDescriptor fileDescriptor) {
    return FileIdentifier.newBuilder()
        .setFilePath(fileDescriptor.reportedPath)
        .setFileHash(getFileHash(fileDescriptor))
        .build();
  }

  public void addAdded(final FileDescriptor fileDescriptor) {
    addedFiles.add(toFileId(fileDescriptor));
  }

  public void addDeleted(final FileDescriptor fileDescriptor) {
    deletedFiles.add(toFileId(fileDescriptor));
  }

  public void addModified(final FileDescriptor fileDescriptor) {
    modifiedFiles.add(toFileId(fileDescriptor));
  }

  public void addTags(final List<String> tags) {
    builder.addAllTags(tags);
  }

  public void addToken(final String token) {
    builder.setLandscapeToken(token);
  }

  public void setRepositoryName(final String repositoryName) {
    builder.setRepositoryName(repositoryName);
  }

  public void setAuthorDate(final Timestamp authorDate) {
    builder.setAuthorDate(authorDate);
  }

  public void setCommitDate(final Timestamp commitDate) {
    builder.setCommitDate(commitDate);
  }

  public void setAuthor(final ContributorData contributorData) {
    builder.setAuthor(contributorData);
  }

  public void setAnalysisFileCount(final int analysisFileCount) {
    builder.setAnalysisFileCount(analysisFileCount);
  }

  /**
   * Returns the commit data. * * @return commit data object
   */
  public CommitData getCommitData() {
    builder.addAllAddedFiles(addedFiles);
    builder.addAllModifiedFiles(modifiedFiles);
    builder.addAllDeletedFiles(deletedFiles);
    return builder.build();
  }
}
