package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.LCOM4;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Lcom4Test {

  /** Components of a class with the methods {@code a()}, {@code b()} and the given members. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        // Fields
        "2 # int x; int y; # x++; # y++;",
        "1 # int x; # x++; # return x;",
        "1 # int x, y; # y++; # y--;",
        "1 # int x; # this.x++; # A.this.x--;",
        "1 # static int x; # A.x++; # x--;",
        "2 # static final int X = 1; # f(X); # g(X);",
        "2 # int x; # other.x++; # x--;",
        "2 # int x; # y.f(1); # x--;",
        "1 # int x; # f(x.y.z); # x[0] = 1;",
        "1 # AutoCloseable x; # try (x) { f(); } # x.f();",
        "2 # int x; # super.x++; # x--;",
        "2 # int x; # f(xy); # x--;",
        // Calls
        "1 # `` # b(); # f();",
        "1 # `` # this.b(); # f();",
        "1 # `` # A.this.b(); # f();",
        "1 # `` # A.b(); # f();",
        "1 # `` # this.<String>b(); # f();",
        "1 # `` # list.map(this::b); # f();",
        "1 # `` # list.map(A::b); # f();",
        "2 # `` # other.b(); # f();",
        "2 # `` # super.b(); # f();",
        "2 # `` # b(1); # f();",
        "1 # void c(int i) { b(); } # c(1); # f();",
        "1 # void c(int... i) { b(); } # c(1, 2, 3); # f();",
        "1 # void c(int... i) { b(); } # c(); # f();",
        "2 # void c(int i) { f(); } void c() { b(); } # c(1); # f();",
        "1 # void c(int i) { f(); } void c() { f(); } # c(1); c(); # b(); c();",
        // Methods without code connect their callers but are no component
        "2 # void c() { } # f(); # g();",
        "1 # abstract void c(); # c(); # c();",
        "2 # abstract void c(); # f(); # g();",
        // Constructors and initializers are ignored
        "2 # int x; int y; A() { x = y; } { x = y; } # x++; # y++;",
        // Shadowing
        "2 # int x; # int x = 1; f(x); # x--;",
        "2 # int x; # var x = 1; f(x); # x--;",
        "1 # int x; # int x = 1; this.x = x; # x--;",
        "1 # int x; # { int x = 1; } f(x); # x--;",
        "1 # int x; # f(x); int x = 1; # x--;",
        "2 # int x; # for (int x = 0; x < 1; x++) f(x); # x--;",
        "1 # int x; # for (int x = 0; x < 1; x++) f(x); f(x); # x--;",
        "2 # int x; # for (var x : list) f(x); # x--;",
        "1 # int x; # for (var x : x) f(x); # x--;",
        "2 # int x; # try { f(); } catch (E x) { f(x); } # x--;",
        "1 # int x; # try { f(); } catch (E x) { f(x); } f(x); # x--;",
        "2 # int x; # try (var x = f()) { f(x); } # x--;",
        "2 # int x; # try (R x = f()) { f(x); } # x--;",
        "2 # int x; # list.map(x -> x); # x--;",
        "2 # int x; # list.map((x, y) -> x); # x--;",
        "2 # int x; # list.map((int x) -> x); # x--;",
        "2 # int x; # list.map((var x) -> x); # x--;",
        "1 # int x; # list.map(y -> x); # x--;",
        "1 # int x; # list.map(x -> x).f(x); # x--;",
        "2 # int x; # if (o instanceof String x) f(x); # x--;",
        "2 # int x; # if (o instanceof List<String> x) f(x); # x--;",
        "2 # int x; # if (o instanceof java.util.Map.Entry x) f(x); # x--;",
        "2 # int x; # if (o instanceof final String x) f(x); # x--;",
        "2 # int x; # if (o instanceof P(var x)) f(x); # x--;",
        "1 # int x; # if (o instanceof String y) f(x); # x--;",
        "1 # int x; # if (o instanceof x.y.X) f(x); # x--;",
        "2 # int x; # if (!(o instanceof String x)) return; f(x); # x--;",
        "2 # int x; # return switch (o) { case P(int x) -> x; default -> 1; }; # x--;",
        "1 # int x; # return switch (o) { case P(int x) -> 1; default -> x; }; # x--;",
        "2 # int x; # switch (o) { case x: f(); break; default: } # x--;",
        "2 # int x; # return switch (o) { case x, y -> f(); default -> 1; }; # x--;",
        "2 # int x; # switch (o) { case 1, 2: int x = 1; break; default: x = 2; } # x--;",
        // Nested types
        "1 # int x; # new T() { void m() { x++; } }; # x--;",
        "1 # int x; # new T() { void m() { A.this.x++; } }; # x--;",
        "2 # int x; # new T() { void m() { this.x++; } }; # x--;",
        "2 # int x; # new T() { int x; void m() { x++; } }; # x--;",
        "2 # int x; # new T() { void m(int x) { x++; } }; # x--;",
        "1 # int x; # new T() { int x; void m() { x++; } }; f(x); # x--;",
        "1 # `` # new T() { void m() { b(); } }; # f();",
        "2 # `` # new T() { void m() { b(); } void b() { } }; # f();",
        "2 # int x; # class L { int x; void m() { x++; } } # x--;",
        "2 # int x; # record L(int x) { void m() { f(x); } } # x--;",
        "2 # int x; # enum L { x; void m() { f(x); } } # x--;",
        "2 # int x; # interface L { int x = 1; default void m() { f(x); } } # x--;",
        "2 # int x; class I { void m() { x++; } } # f(); # x--;",
      })
  void ofClass(
      final double expected, final String members, final String first, final String second) {
    final String source =
        "class A { %s void a() { %s } void b() { %s } }".formatted(members, first, second);
    assertEquals(expected, JavaMetricsTestSupport.ofClass(LCOM4, source));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        "0 # class A { int x; }",
        "0 # interface A { void m(); int X = 1; }",
        "0 # @interface A { int m(); }",
        "2 # class A { int x; int m(int x) { return x; } int n() { return x; } }",
        "1 # class A { int x; <T> int m() { return x; } int n() { return x; } }",
        "1 # interface A { int m(); default int a() { return m(); } default int b() { return m(); } }",
        "2 # interface A { int X = 1; default int a() { return X; } default int b() { return X; } }",
        "1 # record A(int x) { int m() { return x; } int n() { return x(); } }",
        "1 # record A(int x) { A { f(x); } int m() { return x; } static A n(A a) { return m(); } }",
        "1 # enum A { P, Q; int x; int m() { return x; } int n() { return x; } }",
        "2 # enum A { P, Q; boolean m() { return this == P; } boolean n() { return this == P; } }",
        "1 # enum A { P { int m() { return 2; } }; int m() { return 1; } }",
      })
  void ofType(final double expected, final String source) {
    assertEquals(expected, JavaMetricsTestSupport.ofClass(LCOM4, source));
  }

  @Test
  void nestedTypesAreMeasuredOnTheirOwn() {
    final FileData file =
        JavaMetricsTestSupport.parse(
            """
            package p;
            class A {
              int x;
              void m() {
                new T() {
                  int y;
                  void a() { y++; }
                  void b() { x++; }
                };
              }
              static class Inner {
                int x;
                void a() { x++; }
                void b() { x--; }
              }
            }
            """);

    assertEquals(1.0, file.getClasses(0).getMetricsOrThrow(LCOM4));
    assertEquals(2.0, file.getClasses(0).getInnerClasses(0).getMetricsOrThrow(LCOM4));
    assertEquals(1.0, file.getClasses(0).getInnerClasses(1).getMetricsOrThrow(LCOM4));
  }
}
