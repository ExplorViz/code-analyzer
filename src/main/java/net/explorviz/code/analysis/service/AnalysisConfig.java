package net.explorviz.code.analysis.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration object for Git analysis operations.
 */
public record AnalysisConfig(Optional<String> repoPath, Optional<String> repoRemoteUrl, Optional<String> gitUsername,
    Optional<String> gitPassword, Optional<String> branch,
    Optional<String> includeInAnalysisExpressions,
    Optional<String> excludeFromAnalysisExpressions,
    List<ApplicationPath> applicationPaths,
    boolean calculateMetrics,
    Optional<String> startCommit, Optional<String> endCommit,
    Optional<Integer> commitAnalysisLimit,
    String landscapeToken) {

  /**
   * Path filter passed to Git diffs: union of all application roots, or global filters when appropriate.
   */
  public String pathRestrictionForDiff() {
    if (applicationPaths == null || applicationPaths.isEmpty()) {
      return includeInAnalysisExpressions().orElse("");
    }
    final boolean anyBlankRoot = applicationPaths.stream()
        .map(ApplicationPath::root)
        .anyMatch(r -> r == null || r.isBlank());
    if (anyBlankRoot) {
      return includeInAnalysisExpressions().orElse("");
    }
    return applicationPaths.stream()
        .map(ApplicationPath::root)
        .map(String::trim)
        .filter(r -> !r.isEmpty())
        .collect(Collectors.joining(","));
  }

  /**
   * Map for {@link net.explorviz.code.proto.StateDataRequest} {@code application_paths}.
   */
  public Map<String, String> applicationPathsMap() {
    final Map<String, String> map = new LinkedHashMap<>();
    if (applicationPaths == null) {
      return map;
    }
    for (final ApplicationPath path : applicationPaths) {
      final String name = path.name() == null ? "" : path.name().trim();
      final String root = path.root() == null ? "" : path.root().trim();
      if (!name.isEmpty()) {
        map.put(name, root);
      }
    }
    return map;
  }

  /** First non-blank application name; used for JSON export folder layout. */
  public String primaryApplicationNameForExport() {
    if (applicationPaths == null) {
      return "";
    }
    return applicationPaths.stream()
        .map(ApplicationPath::name)
        .filter(n -> n != null && !n.isBlank())
        .findFirst()
        .orElse("")
        .trim();
  }

  /**
   * Builder for AnalysisConfig.
   */
  public static class Builder {
    private Optional<String> repoPath = Optional.empty();
    private Optional<String> repoRemoteUrl = Optional.empty();
    private Optional<String> gitUsername = Optional.empty();
    private Optional<String> gitPassword = Optional.empty();
    private Optional<String> branch = Optional.empty();

    private Optional<String> includeInAnalysisExpressions = Optional.empty();
    private Optional<String> excludeFromAnalysisExpressions = Optional.empty();
    private Optional<String> applicationRoot = Optional.empty();
    private boolean calculateMetrics = true;
    private Optional<String> startCommit = Optional.empty();
    private Optional<String> endCommit = Optional.empty();
    private Optional<Integer> commitAnalysisLimit = Optional.empty();
    private String landscapeToken = "";
    private String applicationName = "";
    private List<ApplicationPath> explicitApplicationPaths;

    public Builder repoPath(final Optional<String> repoPath) {
      this.repoPath = repoPath;
      return this;
    }

    public Builder repoRemoteUrl(final Optional<String> repoRemoteUrl) {
      this.repoRemoteUrl = repoRemoteUrl;
      return this;
    }

    public Builder gitUsername(final Optional<String> gitUsername) {
      this.gitUsername = gitUsername;
      return this;
    }

    public Builder gitPassword(final Optional<String> gitPassword) {
      this.gitPassword = gitPassword;
      return this;
    }

    public Builder branch(final Optional<String> branch) {
      this.branch = branch;
      return this;
    }

    public Builder includeInAnalysisExpressions(final Optional<String> includeInAnalysisExpressions) {
      this.includeInAnalysisExpressions = includeInAnalysisExpressions;
      return this;
    }

    public Builder excludeFromAnalysisExpressions(final Optional<String> excludeFromAnalysisExpressions) {
      this.excludeFromAnalysisExpressions = excludeFromAnalysisExpressions;
      return this;
    }

    public Builder applicationRoot(final Optional<String> applicationRoot) {
      this.applicationRoot = applicationRoot;
      return this;
    }

    public Builder calculateMetrics(final boolean calculateMetrics) {
      this.calculateMetrics = calculateMetrics;
      return this;
    }

    public Builder startCommit(final Optional<String> startCommit) {
      this.startCommit = startCommit;
      return this;
    }

    public Builder endCommit(final Optional<String> endCommit) {
      this.endCommit = endCommit;
      return this;
    }

    public Builder commitAnalysisLimit(final Optional<Integer> commitAnalysisLimit) {
      this.commitAnalysisLimit = commitAnalysisLimit;
      return this;
    }

    public Builder landscapeToken(final String landscapeToken) {
      this.landscapeToken = landscapeToken;
      return this;
    }

    public Builder applicationName(final String applicationName) {
      this.applicationName = applicationName;
      return this;
    }

    public Builder applicationPaths(final List<ApplicationPath> paths) {
      this.explicitApplicationPaths = paths;
      return this;
    }

    public AnalysisConfig build() {
      final List<ApplicationPath> paths;
      if (explicitApplicationPaths != null && !explicitApplicationPaths.isEmpty()) {
        paths = List.copyOf(explicitApplicationPaths);
      } else {
        paths = List.of(new ApplicationPath(
            applicationName != null ? applicationName : "",
            applicationRoot.orElse("")));
      }
      return new AnalysisConfig(
          repoPath,
          repoRemoteUrl,
          gitUsername,
          gitPassword,
          branch,
          includeInAnalysisExpressions,
          excludeFromAnalysisExpressions,
          paths,
          calculateMetrics,
          startCommit,
          endCommit,
          commitAnalysisLimit,
          landscapeToken);
    }
  }

  /**
   * Returns the name of the repository extracted from the remote URL or the local path.
   *
   * @param repoPath optional local filesystem path to the repository
   * @param repoRemoteUrl optional clone URL (takes precedence over {@code repoPath})
   * @return The repository name, or an empty string if neither source yields one
   */
  public static String deriveRepositoryName(final Optional<String> repoPath,
      final Optional<String> repoRemoteUrl) {
    if (repoRemoteUrl != null && repoRemoteUrl.isPresent()) {
      String upstream = repoRemoteUrl.get();
      // remove trailing slash if present
      if (upstream.endsWith("/")) {
        upstream = upstream.substring(0, upstream.length() - 1);
      }
      // delete http(s):// or git@ in the front
      upstream = upstream.replaceFirst("^(https?://|.+@)", "");
      // replace potential .git ending
      upstream = upstream.replaceFirst("\\.git$", "");
      // find the last slash or colon
      final int lastSlash = upstream.lastIndexOf('/');
      final int lastColon = upstream.lastIndexOf(':');
      final int lastSeparator = Math.max(lastSlash, lastColon);
      if (lastSeparator != -1) {
        return upstream.substring(lastSeparator + 1);
      }
      return upstream;
    } else if (repoPath != null && repoPath.isPresent()) {
      String pathStr = repoPath.get();
      // remove trailing slash if present
      if (pathStr.endsWith("/") || pathStr.endsWith("\\")) {
        pathStr = pathStr.substring(0, pathStr.length() - 1);
      }
      // handle both / and \
      final int lastSlash = Math.max(pathStr.lastIndexOf('/'), pathStr.lastIndexOf('\\'));
      if (lastSlash != -1) {
        return pathStr.substring(lastSlash + 1);
      }
      return pathStr;
    }
    return "";
  }

  /**
   * Returns the name of the repository extracted from the remote URL or the local
   * path.
   *
   * @return The repository name, or an empty string if not found.
   */
  public String getRepositoryName() {
    return deriveRepositoryName(repoPath, repoRemoteUrl);
  }
}
