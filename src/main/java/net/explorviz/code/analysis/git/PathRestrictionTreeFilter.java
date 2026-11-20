package net.explorviz.code.analysis.git;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/**
 * Restricts a tree walk to files within the given path restrictions.
 *
 * <p>Each restriction is a glob (or a {@code glob:}/{@code regex:} expression) relative to the
 * repository root. A file is included if its own path or the path of one of its parent directories
 * matches a restriction, so {@code backend} selects everything below the {@code backend} directory
 * and {@code **}{@code /*.java} selects all Java files.
 *
 * <p>Paths are matched against the walked commit trees, not against the working tree on disk, so
 * the result only depends on the analyzed commits. A restriction that matches nothing selects no
 * files.
 */
public final class PathRestrictionTreeFilter extends TreeFilter {

  private final List<String> restrictions;
  private final List<PathMatcher> matchers;

  /** Whether a directory or one of its parent directories matches a restriction. */
  private final Map<String, Boolean> includedDirectories;

  private PathRestrictionTreeFilter(
      final List<String> restrictions,
      final List<PathMatcher> matchers,
      final Map<String, Boolean> includedDirectories) {
    this.restrictions = restrictions;
    this.matchers = matchers;
    this.includedDirectories = includedDirectories;
  }

  /**
   * Creates a filter for the given restrictions.
   *
   * @param pathRestrictions globs or {@code glob:}/{@code regex:} expressions; blank entries are
   *     ignored
   * @return {@link TreeFilter#ALL} if no restriction is given or one of them denotes the repository
   *     root, otherwise a filter that selects the matching files
   * @throws IllegalArgumentException if a restriction is not a valid glob or regular expression
   */
  public static TreeFilter create(final List<String> pathRestrictions) {
    final List<String> restrictions = new ArrayList<>();
    final List<PathMatcher> matchers = new ArrayList<>();
    for (final String rawRestriction : pathRestrictions) {
      if (rawRestriction == null || rawRestriction.isBlank()) {
        continue;
      }
      final String restriction = normalize(rawRestriction.trim());
      if (restriction.isEmpty()) {
        return TreeFilter.ALL;
      }
      restrictions.add(restriction);
      matchers.add(compile(restriction));
    }
    if (matchers.isEmpty()) {
      return TreeFilter.ALL;
    }
    return new PathRestrictionTreeFilter(
        List.copyOf(restrictions), List.copyOf(matchers), new ConcurrentHashMap<>());
  }

  /** Removes leading {@code ./} and {@code /} and trailing {@code /} from plain globs. */
  private static String normalize(final String restriction) {
    if (restriction.startsWith("glob:") || restriction.startsWith("regex:")) {
      return restriction;
    }
    String normalized = restriction;
    boolean changed = true;
    while (changed) {
      changed = false;
      if (normalized.startsWith("./")) {
        normalized = normalized.substring(2);
        changed = true;
      }
      if (normalized.startsWith("/")) {
        normalized = normalized.substring(1);
        changed = true;
      }
      if (normalized.endsWith("/")) {
        normalized = normalized.substring(0, normalized.length() - 1);
        changed = true;
      }
    }
    return ".".equals(normalized) ? "" : normalized;
  }

  private static PathMatcher compile(final String restriction) {
    final String syntaxAndPattern =
        restriction.startsWith("glob:") || restriction.startsWith("regex:")
            ? restriction
            : "glob:" + restriction;
    try {
      return FileSystems.getDefault().getPathMatcher(syntaxAndPattern);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Malformed path restriction '" + restriction + "': " + e.getMessage(), e);
    }
  }

  @Override
  public boolean include(final TreeWalk walker) {
    if (walker.isSubtree()) {
      // Files below any directory may match, e.g. for "**/*.java", so every subtree is entered.
      // Diff walks still skip subtrees that are identical in both commits.
      return true;
    }
    final String path = walker.getPathString();
    return matches(path) || isDirectoryIncluded(parentOf(path));
  }

  private boolean isDirectoryIncluded(final String directory) {
    if (directory.isEmpty()) {
      return false;
    }
    final Boolean cached = includedDirectories.get(directory);
    if (cached != null) {
      return cached;
    }
    final boolean included = matches(directory) || isDirectoryIncluded(parentOf(directory));
    includedDirectories.put(directory, included);
    return included;
  }

  private boolean matches(final String path) {
    final java.nio.file.Path candidate = Paths.get(path);
    for (final PathMatcher matcher : matchers) {
      if (matcher.matches(candidate)) {
        return true;
      }
    }
    return false;
  }

  private static String parentOf(final String path) {
    final int lastSlash = path.lastIndexOf('/');
    return lastSlash < 0 ? "" : path.substring(0, lastSlash);
  }

  @Override
  public boolean shouldBeRecursive() {
    return true;
  }

  @Override
  public TreeFilter clone() {
    // Stateless apart from a cache of deterministic results, so instances can be shared.
    return this;
  }

  @Override
  public String toString() {
    return "PATH_RESTRICTIONS" + restrictions;
  }
}
