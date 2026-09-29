package net.explorviz.code.analysis.exceptions;

import java.io.Serial;

/** Exception for malformed paths. */
public class MalformedPathException extends RuntimeException {

  @Serial private static final long serialVersionUID = 265745021;

  public MalformedPathException(final String reason) {
    super(reason);
  }
}
