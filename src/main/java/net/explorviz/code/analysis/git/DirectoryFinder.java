package net.explorviz.code.analysis.git;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class to ease the finding of directories inside a local git Repository. */
public final class DirectoryFinder {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryFinder.class);

  private DirectoryFinder() {}

  /**
   * Searches and return the relative path to the directory matching the search string.
   *
   * @param paths a list of search strings for the paths.
   * @param root the path the return should be relative to
   * @return the directories matching the search strings
   * @throws NotFoundException thrown if no directory matches the given search string
   */
  public static List<String> getRelativeDirectory(final List<String> paths, final String root)
      throws NotFoundException {
    final List<String> relativePaths = new ArrayList<>();
    for (final String path : getDirectories(root, paths)) {
      relativePaths.add(path.replace(root, ""));
    }
    return relativePaths;
  }

  /**
   * Searches and return the absolute path to the directory matching the search string.
   *
   * @param searchPaths a list of search strings for the paths.
   * @param root the root path to start the search from
   * @return the directories matching the search strings
   * @throws MalformedPathException thrown if the search string is malformed and can not be used
   * @throws NotFoundException thrown if no directory matches the given search string
   */
  public static List<String> getDirectories(String root, List<String> searchPaths)
      throws NotFoundException {
    Set<String> pathSet = new HashSet<>(); // Use a HashSet to avoid duplicates
    Path startPath = Paths.get(root);

    try {
      Files.walkFileTree(
          startPath,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              for (String searchPath : searchPaths) {
                String glob = searchPath;
                if (!glob.startsWith("glob:") && !glob.startsWith("regex:")) {
                  glob = "glob:" + glob;
                }
                java.nio.file.PathMatcher matcher =
                    java.nio.file.FileSystems.getDefault().getPathMatcher(glob);
                if (matcher.matches(startPath.relativize(dir))) {
                  pathSet.add(
                      dir.toAbsolutePath()
                          .normalize()
                          .toString()); // Normalize the path and add to the set
                  LOGGER
                      .atTrace()
                      .addArgument(dir)
                      .log("Directory matched glob and was added to set: {}");
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new NotFoundException("Couldn't find path");
    }

    return new ArrayList<>(pathSet);
  }
}
