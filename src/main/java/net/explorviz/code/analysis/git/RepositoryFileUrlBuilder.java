package net.explorviz.code.analysis.git;

import java.util.Optional;

/**
 * Normalizes repository clone URLs for persistence in the landscape service.
 */
public final class RepositoryFileUrlBuilder {

  private RepositoryFileUrlBuilder() {
    // utility class
  }

  public static Optional<String> normalizeRepositoryUrl(final String repositoryUrl) {
    if (repositoryUrl == null || repositoryUrl.isBlank()) {
      return Optional.empty();
    }

    final var converted = GitRepositoryHandler.convertSshToHttps(repositoryUrl.trim());
    if (!converted.getKey()) {
      return Optional.empty();
    }

    return Optional.of(stripGitSuffix(converted.getValue()));
  }

  public static Optional<String> resolveRepositoryUrl(
      final Optional<String> configuredRemoteUrl, final String originUrl) {
    return configuredRemoteUrl
        .flatMap(RepositoryFileUrlBuilder::normalizeRepositoryUrl)
        .or(() -> normalizeRepositoryUrl(originUrl));
  }

  private static String stripGitSuffix(final String repositoryUrl) {
    return repositoryUrl.endsWith(".git")
        ? repositoryUrl.substring(0, repositoryUrl.length() - 4)
        : repositoryUrl;
  }
}
