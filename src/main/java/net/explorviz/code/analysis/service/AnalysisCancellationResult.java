package net.explorviz.code.analysis.service;

/** Result of requesting cancellation for an analysis job. */
public enum AnalysisCancellationResult {
  /** Cancellation was accepted. */
  CANCELLED,
  /** No job exists for the given landscape token. */
  NOT_FOUND,
  /** The job has already reached a terminal state. */
  ALREADY_TERMINAL
}
