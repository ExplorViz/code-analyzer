package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.AVERAGE_CYCLOMATIC_COMPLEXITY;
import static net.explorviz.code.analysis.metrics.CodeMetrics.CYCLOMATIC_COMPLEXITY;
import static net.explorviz.code.analysis.metrics.CodeMetrics.NESTED_BLOCK_DEPTH;
import static net.explorviz.code.analysis.metrics.CodeMetrics.NPATH_COMPLEXITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.io.IOException;
import java.math.BigInteger;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CyclomaticComplexityTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        "1 # return o;",
        "1 # { f(); } throw new X();",
        "2 # if (i > 0) f();",
        "2 # if (i > 0) f(); else g();",
        "3 # if (i > 0) f(); else if (i < 0) g(); else h();",
        "2 # for (int k = 0; k < i; k++) f();",
        "2 # for (var x : list) f();",
        "2 # while (i > 0) f();",
        "2 # do { f(); } while (i > 0);",
        "3 # try { f(); } catch (A a) { } catch (B | C b) { } finally { g(); }",
        "2 # return i > 0 ? o : null;",
        "3 # return i > 0 && i < 5 || i == 9;",
        "1 # return i & 3 | 4;",
        "3 # switch (i) { case 1: f(); break; case 2: g(); break; default: h(); }",
        "3 # switch (i) { case 1: case 2: f(); break; default: h(); }",
        "3 # switch (i) { case 1, 2: f(); break; case 3: g(); }",
        "3 # return switch (i) { case 1, 2 -> f(); case 3 -> { yield g(); } default -> h(); };",
        "4 # return switch (o) { case String s when i > 0 -> f(); case null, default -> h(); };",
        "4 # return switch (o) { case P(int a) -> f(); case null -> g(); case Q q -> h(); };",
        "3 # list.map(x -> x > 0 ? x : 1).filter(x -> { if (x > 1) return f(); return g(); });",
        "1 # new Thread() { public void run() { if (x) f(); } }.start();",
        "2 # new Thread(i > 0 ? a : b) { public void run() { if (x) f(); } }.start();",
        "1 # class L { void l() { if (x) f(); } }",
        "1 # enum E { A { void l() { if (x) f(); } }; void k() { if (x) f(); } }",
      })
  void ofMethod(final double expected, final String statements)
      throws IOException, CheckstyleException {
    assertEquals(expected, JavaMetricsTestSupport.ofBody(CYCLOMATIC_COMPLEXITY, statements));

    // The first reported function is the method around the statements.
    final BigInteger checkstyle =
        JavaMetricsTestSupport.checkstyleOfBody(statements)
            .cyclomaticComplexities()
            .values()
            .iterator()
            .next();
    assertEquals(expected, checkstyle.doubleValue());
  }

  @Test
  void classesAndFileAggregateTheirFunctions() {
    final FileData file =
        JavaMetricsTestSupport.parse(
            """
            package p;
            class A {
              A() { if (x) f(); }
              void simple() { }
              <T> void generic() { if (a && b) f(); }
              abstract void none();
              class Inner {
                void m() {
                  while (x) {
                    new Thread() { public void run() { if (x) f(); } };
                  }
                }
              }
              void last() { }
            }
            record R(int i) {
              R { if (i < 0) throw new X(); }
            }
            interface I {
              void none();
              default void m() { if (x) f(); }
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(8.0, a.getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(1.6, a.getMetricsOrThrow(AVERAGE_CYCLOMATIC_COMPLEXITY));
    final ClassData inner = JavaMetricsTestSupport.findClass(a.getInnerClassesList(), "Inner");
    assertEquals(2.0, inner.getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(2.0, inner.getInnerClasses(0).getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(2.0, file.getClasses(1).getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(3.0, file.getClasses(2).getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(17.0, file.getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(1.7, file.getMetricsOrThrow(AVERAGE_CYCLOMATIC_COMPLEXITY));

    assertEquals(1.0, a.getFunctions(3).getMetricsOrThrow(CYCLOMATIC_COMPLEXITY));
    assertEquals(0.0, a.getFunctions(3).getMetricsOrThrow(NPATH_COMPLEXITY));
    assertFalse(a.getFunctions(3).containsMetrics(NESTED_BLOCK_DEPTH));
  }

  @Test
  void typesWithoutCodeHaveNoComplexity() {
    final FileData file = JavaMetricsTestSupport.parse("@interface A { int value() default 1; }");

    final ClassData annotation = file.getClasses(0);
    assertFalse(annotation.containsMetrics(CYCLOMATIC_COMPLEXITY));
    assertFalse(annotation.containsMetrics(AVERAGE_CYCLOMATIC_COMPLEXITY));
    assertFalse(file.containsMetrics(CYCLOMATIC_COMPLEXITY));
    assertFalse(file.containsMetrics(AVERAGE_CYCLOMATIC_COMPLEXITY));
  }
}
