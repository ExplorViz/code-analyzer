package net.explorviz.code.analysis.exceptions;

import java.io.Serial;

/** Exception for not found things. */
public class NotFoundException extends Exception {

  @Serial private static final long serialVersionUID = 987345021;

  public NotFoundException(final String reason) {
    super(reason);
  }
}
