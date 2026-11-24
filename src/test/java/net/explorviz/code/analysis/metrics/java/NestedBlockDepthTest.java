package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.NESTED_BLOCK_DEPTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.explorviz.code.proto.ClassData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NestedBlockDepthTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        "1 # ``",
        "1 # f(); g();",
        "2 # if (a) { f(); } if (b) { g(); }",
        "2 # if (a) f(); else if (b) g(); else if (c) h(); else k();",
        "3 # if (a) f(); else { if (b) g(); }",
        "3 # if (a) f(); else if (b) { while (c) g(); }",
        "4 # for (var x : y) while (a) do { f(); } while (b);",
        "3 # try { f(); } catch (A a) { if (b) g(); } finally { h(); }",
        "2 # try (A a = f()) { g(); }",
        "3 # synchronized (this) { if (a) f(); }",
        "3 # switch (i) { case 1: if (a) f(); break; default: }",
        "3 # switch (i) { case 1 -> { if (a) f(); } default -> g(); }",
        "2 # return switch (i) { case 1 -> f(); default -> g(); };",
        "3 # list.forEach(x -> { if (a) f(); });",
        "1 # list.forEach(x -> f());",
        "2 # if (a) { new T() { void m() { if (b) { if (c) f(); } } }; }",
      })
  void ofMethod(final double expected, final String statements) {
    assertEquals(expected, JavaMetricsTestSupport.ofBody(NESTED_BLOCK_DEPTH, statements));
  }

  @Test
  void anonymousClassesAreMeasuredOnTheirOwn() {
    final ClassData outer =
        JavaMetricsTestSupport.parse(
                """
                package p;
                class A {
                  void m() {
                    if (a) {
                      new T() { void n() { f(); } };
                      if (b) { f(); }
                    }
                  }
                }
                """)
            .getClasses(0);

    assertEquals(3.0, outer.getFunctions(0).getMetricsOrThrow(NESTED_BLOCK_DEPTH));
    assertEquals(
        1.0, outer.getInnerClasses(0).getFunctions(0).getMetricsOrThrow(NESTED_BLOCK_DEPTH));
  }
}
