package net.explorviz.code.analysis.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Resolves and lists repositories stored in the local clone folder. */
@ApplicationScoped
public class LocalRepositoryService {

  private static final String DEFAULT_CLONE_ROOT = "cloned-repositories";
  private static final String REMOTE_ORIGIN_PREFIX = Constants.R_REMOTES + "origin/";

  @ConfigProperty(
      name = "explorviz.gitanalysis.remote.storage-path",
      defaultValue = DEFAULT_CLONE_ROOT)
  /* default */ String cloneRootProperty;

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

    final Path walkRoot = resolveExistingRealPath(cloneRoot);

    final List<Path> repositoryPaths = findRepositories(walkRoot);

    // Opening a repository and reading its refs is I/O bound, so do it in parallel.
    return repositoryPaths.parallelStream()
        .map(path -> getRepositoryInfo(walkRoot, path))
        .sorted(Comparator.comparing(LocalRepositoryInfo::path))
        .toList();
  }

  /**
   * Finds Git repositories below the given root. Directories are not descended into once a
   * repository has been found, so working trees (and their potentially huge file counts) are never
   * traversed.
   */
  private List<Path> findRepositories(final Path walkRoot) throws IOException {
    final List<Path> repositories = new ArrayList<>();

    Files.walkFileTree(
        walkRoot,
        EnumSet.of(FileVisitOption.FOLLOW_LINKS),
        Integer.MAX_VALUE,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(
              final Path directory, final BasicFileAttributes attributes) {
            if (!directory.equals(walkRoot) && isGitRepository(directory)) {
              repositories.add(directory);
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(final Path file, final IOException exception) {
            // Unreadable entries and symlink loops must not fail the whole listing.
            return FileVisitResult.CONTINUE;
          }
        });

    return repositories;
  }

  /**
   * Resolves a clone-root-relative repository path to an absolute path.
   *
   * @param relativeRepositoryPath repository path relative to the clone root
   * @return absolute repository path
   * @throws IOException if the path escapes the clone root
   */
  public Path resolveRelativeRepositoryPath(final String relativeRepositoryPath)
      throws IOException {
    final Path cloneRoot = getCloneRoot();
    final Path repositoryPath = cloneRoot.resolve(relativeRepositoryPath.trim()).normalize();

    if (!isWithinCloneRoot(cloneRoot, repositoryPath)) {
      throw new IOException("Local repository path must be relative to " + cloneRoot);
    }

    return repositoryPath;
  }

  private Path getCloneRoot() {
    final String cloneRoot =
        cloneRootProperty == null || cloneRootProperty.isBlank()
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

  private LocalRepositoryInfo getRepositoryInfo(final Path walkRoot, final Path repositoryPath) {
    final String relativePath =
        walkRoot.relativize(repositoryPath).toString().replace(File.separatorChar, '/');
    return new LocalRepositoryInfo(relativePath, listBranches(repositoryPath));
  }

  private boolean isWithinCloneRoot(final Path cloneRoot, final Path repositoryPath)
      throws IOException {
    if (repositoryPath.startsWith(cloneRoot)) {
      return true;
    }
    if (!Files.exists(cloneRoot) || !Files.exists(repositoryPath)) {
      return repositoryPath.startsWith(cloneRoot);
    }
    return resolveExistingRealPath(repositoryPath).startsWith(resolveExistingRealPath(cloneRoot));
  }

  private Path resolveExistingRealPath(final Path path) throws IOException {
    return path.toRealPath();
  }

  private List<String> listBranches(final Path repositoryPath) {
    final Set<String> branchNames = new TreeSet<>();
    try (Repository repository =
        new RepositoryBuilder().setWorkTree(repositoryPath.toFile()).setMustExist(true).build()) {
      final RefDatabase refDatabase = repository.getRefDatabase();
      addBranchNames(
          branchNames, refDatabase.getRefsByPrefix(Constants.R_HEADS), Constants.R_HEADS);
      addBranchNames(
          branchNames, refDatabase.getRefsByPrefix(REMOTE_ORIGIN_PREFIX), REMOTE_ORIGIN_PREFIX);
    } catch (IOException | RuntimeException exception) {
      return Collections.emptyList();
    }
    return List.copyOf(branchNames);
  }

  private static void addBranchNames(
      final Set<String> branchNames, final List<Ref> refs, final String prefix) {
    for (final Ref ref : refs) {
      final String branchName = ref.getName().substring(prefix.length());
      if (!"HEAD".equals(branchName)) {
        branchNames.add(branchName);
      }
    }
  }
}
