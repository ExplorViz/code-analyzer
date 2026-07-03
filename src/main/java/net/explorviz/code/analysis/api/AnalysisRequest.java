package net.explorviz.code.analysis.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.service.AnalysisConfig;
import net.explorviz.code.analysis.service.ApplicationPath;

/**
 * Request object for triggering a Git analysis.
 */
public class AnalysisRequest {

  /**
   * Optional multi-application configuration (name and repo-relative root per app).
   */
  public static final class ApplicationSpec {
    private String name;
    private String root;

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getRoot() {
      return root;
    }

    public void setRoot(final String root) {
      this.root = root;
    }
  }

  private String repoPath;
  private String repoRemoteUrl;
  private String remoteStoragePath;
  private String username;
  private String password;
  private String branch;
  private String includeInAnalysisExpressions;
  private String excludeFromAnalysisExpressions;

  private boolean sendToRemote = true;
  private boolean includeDataStructures = true;
  private String startCommit;
  private String endCommit;
  private Integer commitAnalysisLimit;
  private Integer maxLocForFullAnalysis;
  private boolean firstParentCommitsOnly = true;
  private String landscapeToken = "mytokenvalue";
  private String applicationName = "";
  private String applicationRoot;
  private List<ApplicationSpec> applications;

  private boolean fetchSocialData = false;
  private String fetchEndDate;
  private Integer socialDataTimeFrameDays;

  public AnalysisRequest() {
  }

  public String getRepoPath() {
    return repoPath;
  }

  public void setRepoPath(final String repoPath) {
    this.repoPath = repoPath;
  }

  public String getRepoRemoteUrl() {
    return repoRemoteUrl;
  }

  public void setRepoRemoteUrl(final String repoRemoteUrl) {
    this.repoRemoteUrl = repoRemoteUrl;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(final String branch) {
    this.branch = branch;
  }

  public String getIncludeInAnalysisExpressions() {
    return includeInAnalysisExpressions;
  }

  public void setIncludeInAnalysisExpressions(final String includeInAnalysisExpressions) {
    this.includeInAnalysisExpressions = includeInAnalysisExpressions;
  }

  public String getExcludeFromAnalysisExpressions() {
    return excludeFromAnalysisExpressions;
  }

  public void setExcludeFromAnalysisExpressions(final String excludeFromAnalysisExpressions) {
    this.excludeFromAnalysisExpressions = excludeFromAnalysisExpressions;
  }

  public String getApplicationRoot() {
    return applicationRoot;
  }

  public void setApplicationRoot(final String applicationRoot) {
    this.applicationRoot = applicationRoot;
  }

  public boolean isSendToRemote() {
    return sendToRemote;
  }

  public void setSendToRemote(final boolean sendToRemote) {
    this.sendToRemote = sendToRemote;
  }

  public boolean isIncludeDataStructures() {
    return includeDataStructures;
  }

  public void setIncludeDataStructures(final boolean includeDataStructures) {
    this.includeDataStructures = includeDataStructures;
  }

  public String getStartCommit() {
    return startCommit;
  }

  public void setStartCommit(final String startCommit) {
    this.startCommit = startCommit;
  }

  public String getEndCommit() {
    return endCommit;
  }

  public void setEndCommit(final String endCommit) {
    this.endCommit = endCommit;
  }

  public String getLandscapeToken() {
    return landscapeToken;
  }

  public void setLandscapeToken(final String landscapeToken) {
    this.landscapeToken = landscapeToken;
  }

  public String getApplicationName() {
    return applicationName;
  }

  public void setApplicationName(final String applicationName) {
    this.applicationName = applicationName;
  }

  public List<ApplicationSpec> getApplications() {
    return applications;
  }

  public void setApplications(final List<ApplicationSpec> applications) {
    this.applications = applications;
  }

  public Integer getCommitAnalysisLimit() {
    return commitAnalysisLimit;
  }

  public void setCommitAnalysisLimit(final Integer commitAnalysisLimit) {
    this.commitAnalysisLimit = commitAnalysisLimit;
  }

  public Integer getMaxLocForFullAnalysis() {
    return maxLocForFullAnalysis;
  }

  public void setMaxLocForFullAnalysis(final Integer maxLocForFullAnalysis) {
    this.maxLocForFullAnalysis = maxLocForFullAnalysis;
  }

  public boolean isFirstParentCommitsOnly() {
    return firstParentCommitsOnly;
  }

  public void setFirstParentCommitsOnly(final boolean firstParentCommitsOnly) {
    this.firstParentCommitsOnly = firstParentCommitsOnly;
  }

  public boolean isFetchSocialData() {
    return fetchSocialData;
  }

  public void setFetchSocialData(final boolean fetchSocialData) {
    this.fetchSocialData = fetchSocialData;
  }

  public String getFetchEndDate() {
    return fetchEndDate;
  }

  public void setFetchEndDate(final String fetchEndDate) {
    this.fetchEndDate = fetchEndDate;
  }

  public Integer getSocialDataTimeFrameDays() {
    return socialDataTimeFrameDays;
  }

  public void setSocialDataTimeFrameDays(final Integer socialDataTimeFrameDays) {
    this.socialDataTimeFrameDays = socialDataTimeFrameDays;
  }

  /**
   * Converts this request to an AnalysisConfig.
   *
   * @return The analysis configuration
   */
  public AnalysisConfig toConfig() {
    final List<ApplicationPath> paths = new ArrayList<>();
    if (applications != null) {
      for (final ApplicationSpec spec : applications) {
        if (spec == null || spec.getName() == null || spec.getName().isBlank()) {
          continue;
        }
        final String root = spec.getRoot() != null ? spec.getRoot().trim() : "";
        paths.add(new ApplicationPath(spec.getName().trim(), root));
      }
    }

    final AnalysisConfig.Builder builder = new AnalysisConfig.Builder()
        .repoPath(Optional.ofNullable(repoPath))
        .repoRemoteUrl(Optional.ofNullable(repoRemoteUrl))
        .gitUsername(Optional.ofNullable(username))
        .gitPassword(Optional.ofNullable(password))
        .branch(Optional.ofNullable(branch))
        .includeInAnalysisExpressions(Optional.ofNullable(includeInAnalysisExpressions))
        .excludeFromAnalysisExpressions(Optional.ofNullable(excludeFromAnalysisExpressions))
        .includeDataStructures(includeDataStructures)
        .startCommit(Optional.ofNullable(startCommit))
        .endCommit(Optional.ofNullable(endCommit))
        .commitAnalysisLimit(Optional.ofNullable(commitAnalysisLimit))
        .maxLocForFullAnalysis(Optional.ofNullable(maxLocForFullAnalysis))
        .firstParentCommitsOnly(firstParentCommitsOnly)
        .landscapeToken((landscapeToken != null && !landscapeToken.isBlank()) ? landscapeToken : "mytokenvalue")
        .applicationRoot(Optional.ofNullable(applicationRoot))
        .fetchSocialData(fetchSocialData)
        .socialDataTimeFrameDays(Optional.ofNullable(socialDataTimeFrameDays))
        .fetchEndDate(Optional.ofNullable(fetchEndDate).filter(s -> !s.isEmpty()));

    if (!paths.isEmpty()) {
      builder.applicationPaths(paths);
    } else {
      String resolvedName = applicationName != null ? applicationName.trim() : "";
      if (resolvedName.isEmpty()) {
        resolvedName = AnalysisConfig.deriveRepositoryName(
            Optional.ofNullable(repoPath),
            Optional.ofNullable(repoRemoteUrl));
      }
      builder.applicationName(resolvedName)
          .applicationRoot(Optional.ofNullable(applicationRoot));
    }
    return builder.build();
  }
}
