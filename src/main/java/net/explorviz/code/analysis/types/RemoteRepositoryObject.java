package net.explorviz.code.analysis.types;

import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.transport.CredentialsProvider;

/** Storage Object to abstract the remote repository's url and storage path. */
public class RemoteRepositoryObject {

  private String url;
  private String storagePath;
  private String branchName;
  private Integer cloneDepth;
  private CredentialsProvider credentialsProvider;

  /**
   * Create new RemoteRepositoryObject holding data for cloning remote repository.
   *
   * @param url the repository's url
   * @param storagePath where to clone the repository to
   * @param credentialsProvider the credential provider for private repositories
   * @param branchName the name of the branch to analyze
   * @param cloneDepth optional clone depth for shallow cloning (null for full clone)
   */
  public RemoteRepositoryObject(
      final String url,
      final String storagePath,
      final CredentialsProvider credentialsProvider,
      final String branchName,
      final Integer cloneDepth) {
    this.url = url;
    this.storagePath = storagePath;
    this.credentialsProvider = credentialsProvider;
    this.branchName = branchName;
    this.cloneDepth = cloneDepth;
  }

  public RemoteRepositoryObject(
      final String url,
      final String storagePath,
      final CredentialsProvider credentialsProvider,
      final String branchName) {
    this(url, storagePath, credentialsProvider, branchName, null);
  }

  public RemoteRepositoryObject(
      final String url, final String storagePath, final String branchName) {
    this(url, storagePath, CredentialsProvider.getDefault(), branchName);
  }

  public RemoteRepositoryObject() {
    this("", "", "");
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(final String storagePath) {
    this.storagePath = storagePath;
  }

  public String getBranchName() {
    return branchName;
  }

  public void setBranchName(final String branchName) {
    this.branchName = branchName;
  }

  public String getBranchNameOrNull() {
    return branchName.isBlank() ? null : branchName;
  }

  public List<String> getBranchNameAsListOrNull() {
    return branchName.isBlank() ? null : Collections.singletonList(branchName);
  }

  public CredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public void setCredentialsProvider(final CredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
  }

  public Integer getCloneDepth() {
    return cloneDepth;
  }

  public void setCloneDepth(final Integer cloneDepth) {
    this.cloneDepth = cloneDepth;
  }
}
