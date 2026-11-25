package net.explorviz.code.analysis.metrics.cohesion;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Method or property with code. Compared by identity since signatures are not resolved.
 *
 * <p>Methods without code are no component of their own, but connect the methods that call them.
 */
public final class Method {

  private final String name;
  private final String self;
  private final int minArguments;
  private final int maxArguments;
  private final boolean hasCode;
  private final List<ParserRuleContext> code;

  private Method(
      final String name,
      final String self,
      final int minArguments,
      final int maxArguments,
      final boolean hasCode,
      final ParserRuleContext... code) {
    this.name = name;
    this.self = self;
    this.minArguments = minArguments;
    this.maxArguments = maxArguments;
    this.hasCode = hasCode;
    this.code = Arrays.stream(code).filter(Objects::nonNull).toList();
  }

  /**
   * Creates a method.
   *
   * @param name the simple name
   * @param self the name of the own object in the method, {@code null} if there is none
   * @param arguments the accepted numbers of arguments
   * @param hasCode whether the method has statements
   * @param code the rules that contain the parameters and the statements, may be {@code null}
   */
  public static Method of(
      final String name,
      final String self,
      final Arguments arguments,
      final boolean hasCode,
      final ParserRuleContext... code) {
    return new Method(name, self, arguments.min(), arguments.max(), hasCode, code);
  }

  /** Creates a property with code, which is used like a field. */
  public static Method property(
      final String name, final boolean hasCode, final ParserRuleContext... code) {
    return new Method(name, Access.SELF, 0, Integer.MAX_VALUE, hasCode, code);
  }

  public String name() {
    return name;
  }

  /* package */ String self() {
    return self;
  }

  /* package */ boolean hasCode() {
    return hasCode;
  }

  /* package */ List<ParserRuleContext> code() {
    return code;
  }

  /* package */ boolean accepts(final int arguments) {
    return arguments < 0 || arguments >= minArguments && arguments <= maxArguments;
  }

  /** Numbers of arguments that a method accepts. */
  public record Arguments(int min, int max) {

    /** For languages without overloads. */
    public static final Arguments ANY = new Arguments(0, Integer.MAX_VALUE);

    /** Returns the numbers of arguments for the given parameters. */
    public static Arguments of(final int required, final int optional, final boolean isVariadic) {
      return new Arguments(required, isVariadic ? Integer.MAX_VALUE : required + optional);
    }
  }
}
