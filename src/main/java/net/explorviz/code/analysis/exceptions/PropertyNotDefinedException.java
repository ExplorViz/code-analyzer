package net.explorviz.code.analysis.exceptions;

import java.io.Serial;

/** Exception regarding not existing property. */
public class PropertyNotDefinedException extends Exception {

  @Serial private static final long serialVersionUID = 765345121;

  public PropertyNotDefinedException(final String propertyName) {
    this(propertyName, "");
  }

  public PropertyNotDefinedException(final String propertyName, final String errorMessage) {
    super(toMessage(new String[] {propertyName}, errorMessage));
  }

  public PropertyNotDefinedException(final String[] propertyNames) {
    this(propertyNames, "");
  }

  public PropertyNotDefinedException(final String[] propertyNames, final String errorMessage) {
    super(PropertyNotDefinedException.toMessage(propertyNames, errorMessage));
  }

  private static String toMessage(final String[] propertyNames, final String errorMessage) {
    final StringBuilder message = new StringBuilder(80);
    message.append("Properties ");
    for (final String item : propertyNames) {
      message.append('<').append(item).append("> ");
    }
    message.append("not defined in application.properties\n").append(errorMessage);
    return message.toString();
  }
}
