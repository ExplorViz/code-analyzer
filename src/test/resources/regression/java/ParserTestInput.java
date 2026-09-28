package net.explorviz.code.analysis.fixtures;

import static java.lang.Math.abs;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Comprehensive Java parser test input covering programming constructs for regression testing.
 * <p>
 * Includes type declarations, modifiers, methods, constructors, fields, comments,
 * inheritance, generics, and control-flow keywords.
 */
@Deprecated
@SuppressWarnings("all")
public strictfp class ParserTestInput extends AbstractBase
    implements Serializable, Runnable, Callable<String> {

  // Single-line comment

  /* Single-line block comment */

  /*
   * Multi-line
   * block comment
   */

  /** Field Javadoc comment. */
  public static final int PUBLIC_STATIC_FINAL_INT = 42;

  private static volatile boolean privateStaticVolatileFlag;

  protected transient String protectedTransientField;

  final double[] finalArrayField = new double[] {1.0, 2.0};

  private Map<String, List<Integer>> nestedGenericField;

  /**
   * No-arg constructor.
   */
  public ParserTestInput() {
    super(0);
    this.nestedGenericField = new HashMap<>();
  }

  /**
   * Constructor with parameters, including {@code final} and varargs.
   */
  protected ParserTestInput(final String name, int... values) throws IOException {
    super(values == null ? 0 : values.length);
    this.protectedTransientField = name;
  }

  @Override
  public synchronized void run() {
    assert PUBLIC_STATIC_FINAL_INT > 0 : "constant must be positive";
  }

  @Override
  public String call() throws Exception {
    return doWork(true, (byte) 1, (short) 2, 'x', 3, 4L, 5.0f, 6.0d, null);
  }

  public final <T extends Number & Comparable<T>> T genericMethod(final T value) {
    return value;
  }

  private static native void nativeMethod();

  static void packagePrivateStaticMethod() {
    // empty
  }

  protected synchronized boolean doWork(
      boolean flag,
      byte b,
      short s,
      char c,
      int i,
      long l,
      float f,
      double d,
      Object obj) throws IOException, RuntimeException {
    if (flag) {
      return true;
    } else if (obj instanceof String) {
      return false;
    }

    switch (i) {
      case 0:
        break;
      case 1:
        continueLabel:
        for (int n = 0; n < 3; n++) {
          if (n == 1) {
            continue continueLabel;
          }
          if (n == 2) {
            break continueLabel;
          }
        }
        break;
      default:
        while (s > 0) {
          s--;
        }
        do {
          b++;
        } while (b < 3);
        break;
    }

    try (Closeable ignored = () -> { }) {
      if (l < 0L) {
        throw new IOException("negative");
      }
    } catch (IOException | RuntimeException ex) {
      throw ex;
    } finally {
      privateStaticVolatileFlag = abs(i) > 0;
    }

    synchronized (this) {
      nestedGenericField.put("key", new ArrayList<>());
    }

    class LocalHelper {
      void help() {
        // local class method
      }
    }
    new LocalHelper().help();

    return f > 0.0f && d > 0.0d && c != '\0';
  }

  @Override
  public int abstractHook() {
    return getBaseValue();
  }

  /** Public static nested class. */
  public static final class StaticNested {
    private int nestedField;

    public StaticNested(int nestedField) {
      this.nestedField = nestedField;
    }

    public int getNestedField() {
      return nestedField;
    }
  }

  /** Non-static inner class. */
  private class InnerClass {
    void innerMethod() {
      // accesses enclosing instance
      run();
    }
  }

  /** Nested interface. */
  public interface NestedInterface extends Serializable {
    default void defaultMethod() {
      // default interface method
    }

    static void staticInterfaceMethod() {
      // static interface method
    }

    void abstractInterfaceMethod();
  }

  /** Nested enum with constants and a method. */
  public enum NestedEnum {
    ALPHA,
    BETA,
    GAMMA;

    public boolean isAlpha() {
      return this == ALPHA;
    }
  }

  /** Nested annotation type. */
  public @interface NestedAnnotation {
    String value() default "";

    int priority() default 0;
  }
}

/**
 * Package-private abstract base class.
 */
abstract class AbstractBase {
  private final int baseValue;

  protected AbstractBase(int baseValue) {
    this.baseValue = baseValue;
  }

  public int getBaseValue() {
    return baseValue;
  }

  public abstract int abstractHook();
}

/**
 * Package-private interface with multiple method kinds.
 */
interface MarkerInterface extends Closeable {
  void mark();

  default void defaultMark() {
    mark();
  }

  static MarkerInterface noop() {
    return () -> { };
  }
}

/**
 * Package-private enum.
 */
enum TopLevelEnum {
  ONE,
  TWO,
  THREE
}

/**
 * Package-private record with compact constructor and method.
 */
record PointRecord(int x, int y) {
  PointRecord {
    if (x < 0 || y < 0) {
      throw new IllegalArgumentException("negative coordinate");
    }
  }

  public int sum() {
    return x + y;
  }
}
