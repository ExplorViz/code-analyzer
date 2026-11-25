package net.explorviz.code.analysis.metrics.cohesion;

/**
 * Use of a name that may refer to a member of the measured type.
 *
 * @param qualifier the object or type whose member is used, {@code null} if it is implicit
 * @param name the name of the member
 * @param arguments the number of arguments or {@link #NO_CALL}
 */
public record Access(String qualifier, String name, int arguments) {

  /** Qualifier of members of the own object, unless the language calls it differently. */
  public static final String SELF = "this";

  /** Number of arguments of uses that are no call, e.g. of fields and method references. */
  public static final int NO_CALL = -1;

  /** Number of arguments of calls whose number of arguments is unknown. */
  public static final int UNKNOWN = -2;

  /** Use of a field or method that is no call. */
  public static Access of(final String qualifier, final String name) {
    return new Access(qualifier, name, NO_CALL);
  }

  /** Call of a method. */
  public static Access call(final String qualifier, final String name, final int arguments) {
    return new Access(qualifier, name, arguments);
  }
}
