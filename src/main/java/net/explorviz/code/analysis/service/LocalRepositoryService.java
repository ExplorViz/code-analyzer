package net.explorviz.code.analysis.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Resolves and lists repositories stored in the local clone folder.
 */
@ApplicationScoped
public class LocalRepositoryService {

  private static final String DEFAULT_CLONE_ROOT = "cloned-repositories";

  @ConfigProperty(name = "explorviz.gitanalysis.remote.storage-path", defaultValue = DEFAULT_CLONE_ROOT)
  /* default */ String cloneRootProperty; // NOCS

  /**
   * Lists all Git repositories below the configured clone root.
   *
   * @return repository metadata for paths relative to the clone root
   * @throws IOException if the clone root cannot be read
   */
  public List<LocalRepositoryInfo> listRepositories() throws IOException {
    final Path cloneRoot = getCloneRoot();
    if (!Files.isDirectory(cloneRoot)) {
      return Collections.emptyList();
    }

    try (Stream<Path> paths = Files.walk(cloneRoot)) {
      return paths
          .filter(path -> !path.equals(cloneRoot))
          .filter(this::isGitRepository)
          .map(path -> getRepositoryInfo(cloneRoot, path))
          .sorted(Comparator.comparing(LocalRepositoryInfo::path))
          .toList();
    }
  }

  /**
   * Resolves a clone-root-relative repository path to an absolute path.
   *
   * @param relativeRepositoryPath repository path relative to the clone root
   * @return absolute repository path
   * @throws IOException if the path escapes the clone root
   */
  public Path resolveRelativeRepositoryPath(final String relativeRepositoryPath) throws IOException {
    final Path cloneRoot = getCloneRoot();
    final Path repositoryPath = cloneRoot.resolve(relativeRepositoryPath.trim()).normalize();

    if (!repositoryPath.startsWith(cloneRoot)) {
      throw new IOException("Local repository path must be relative to " + cloneRoot);
    }

    return repositoryPath;
  }

  private Path getCloneRoot() {
    final String cloneRoot = cloneRootProperty == null || cloneRootProperty.isBlank()
        ? DEFAULT_CLONE_ROOT
        : cloneRootProperty;
    final Path configuredPath = Paths.get(cloneRoot);

    if (configuredPath.isAbsolute()) {
      return configuredPath.normalize();
    }
    String systemPath = System.getProperty("user.dir");
    systemPath = systemPath.replace("\\build\\classes\\java\\main", "");
    systemPath = systemPath.replace("/build/classes/java/main", "");
    return Paths.get(systemPath).resolve(configuredPath).normalize();
  }

  private boolean isGitRepository(final Path path) {
    final Path gitMetadataPath = path.resolve(".git");
    return Files.isDirectory(gitMetadataPath) || Files.isRegularFile(gitMetadataPath);
  }

  private LocalRepositoryInfo getRepositoryInfo(final Path cloneRoot, final Path repositoryPath) {
    final String relativePath = cloneRoot.relativize(repositoryPath).toString()
        .replace(File.separatorChar, '/');
    return new LocalRepositoryInfo(relativePath, listBranches(repositoryPath));
  }

  private List<String> listBranches(final Path repositoryPath) {
    final Set<String> branchNames = new LinkedHashSet<>();
    try (Git repository = Git.open(repositoryPath.toFile())) {
      for (final Ref branch : repository.branchList().setListMode(ListMode.ALL).call()) {
        final String branchName = branch.getName()
            .replaceFirst("^refs/heads/", "")
            .replaceFirst("^refs/remotes/origin/", "");
        if (!branchName.equals("HEAD")) {
          branchNames.add(branchName);
        }
      }
    } catch (GitAPIException | IOException exception) {
      return Collections.emptyList();
    }
    return branchNames.stream().sorted().toList();
  }
}
