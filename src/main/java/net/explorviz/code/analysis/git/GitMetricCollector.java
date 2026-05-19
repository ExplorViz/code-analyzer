package net.explorviz.code.analysis.git;

import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.ContributorData.Builder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple collector for metrics based on git data.
 */
public final class GitMetricCollector {

  private static final Logger LOGGER = LoggerFactory.getLogger(GitMetricCollector.class);

  private static final ThreadLocal<String> AUTHOR_CACHE = ThreadLocal.withInitial(() -> "");

  private GitMetricCollector() {
  }


  /**
   * Resets the author, call once per commit before calling
   * {@link GitMetricCollector#addCommitGitMetrics(AbstractFileDataHandler, RevCommit)}.
   */
  public static void resetAuthor() {
    AUTHOR_CACHE.remove();
  }

  /**
   * Adds git metrics that are valid for all files within a commit. For performance reasons, some data gets cached.
   * before calling this method for a commit, call {@link GitMetricCollector#resetAuthor()} once for every new commit.
   *
   * @param fileDataHandler the fileDataHandler to add the metric to
   * @param commit          the current commit
   */
  public static void addCommitGitMetrics(final AbstractFileDataHandler fileDataHandler,
      final RevCommit commit) {
    String author = AUTHOR_CACHE.get();
    if (author.isBlank()) {
      author = commit.getAuthorIdent().getEmailAddress();
      AUTHOR_CACHE.set(author);
    }
    fileDataHandler.setAuthor(author);
  }


  /**
   * Adds git metrics that are valid for a specific file.
   *
   * @param fileDataHandler the fileDataHandler to add the metric to
   * @param fileDescriptor  the fileDescriptor holding the file data
   */
  public static void addFileGitMetrics(final AbstractFileDataHandler fileDataHandler,
      final FileDescriptor fileDescriptor) {
    try {
      fileDataHandler.setModifications(fileDescriptor.modifiedLines, fileDescriptor.addedLines,
          fileDescriptor.removedLines);
    } catch (NullPointerException e) {  // NOPMD
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Failed to add modifications");
      }
    }
  }

  /** 
   * Creates a ContributorData object based on the given commit, landscape token and repository name.
   *
   * @param commit the commit to extract contributor information from
   * @param landscapeToken the landscape token to set in the ContributorData
   * @param repositoryName the repository name to set in the ContributorData
   * @return a ContributorData object containing the contributor information
   */
  public static ContributorData createContributorData(
        final RevCommit commit,
        final String landscapeToken,
        final String repositoryName
      
  ) {
    String name = commit.getAuthorIdent().getName();
    String email = commit.getAuthorIdent().getEmailAddress();

    Builder builder = ContributorData.newBuilder()
        .setGitUsername(name)
        .setEmail(email)
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repositoryName);

    return builder.build();
  }
}
