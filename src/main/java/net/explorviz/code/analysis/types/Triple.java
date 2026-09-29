package net.explorviz.code.analysis.types;

/**
 * Generic Triple Class to store three generic values.
 *
 * @param <L> left type
 * @param <M> middle type
 * @param <R> right type
 */
public record Triple<L, M, R>(L left, M middle, R right) {

  /**
   * Creates a Triple of data entries.
   *
   * @param left the left data entry
   * @param middle the middle data entry
   * @param right the right data entry
   */
  public Triple {}
}
