package net.explorviz.code.analysis.exceptions;

/** Thrown when an analysis job is cancelled while running. */
public class AnalysisCancelledException extends RuntimeException {

  private final String landscapeToken;

  public AnalysisCancelledException(final String landscapeToken) {
    super("Analysis cancelled for landscapeToken=" + landscapeToken);
    this.landscapeToken = landscapeToken;
  }

  public String getLandscapeToken() {
    return landscapeToken;
  }
}
