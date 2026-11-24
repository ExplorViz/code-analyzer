package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.NPATH_COMPLEXITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NpathComplexityTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        "0 # ``",
        "1 # f(); int x = 1; return o;",
        "2 # if (a) f();",
        "2 # if (a) f(); else g();",
        "3 # if (a) f(); else if (b) g(); else h();",
        "4 # if (a) f(); if (b) g();",
        "4 # if (a && b || c) f();",
        "3 # if (a) { if (b) f(); }",
        "2 # while (a) f();",
        "3 # while (a && b) f();",
        "3 # do { if (a) f(); } while (b);",
        "3 # for (int k = 0; k < 1 && a; k++) f();",
        "2 # for (var x : y) f();",
        "2 # return a ? b : c;",
        "4 # return a ? b : c ? d : e;",
        "2 # return a && b || c;",
        "2 # int x = a ? 1 : 2;",
        "3 # int x = a && b ? 1 : 2;",
        "0 # boolean x = a && b || c;",
        "0 # f(a && b || c);",
        "2 # return f(a && b || c);",
        "3 # try { f(); } catch (A x) { g(); } catch (B x) { h(); }",
        "3 # try { if (a) f(); } finally { g(); }",
        "3 # try { f(); } catch (A x) { g(); } finally { if (a) h(); }",
        "2 # synchronized (this) { if (a) f(); }",
        "2 # label: if (a) f();",
        "4 # { if (a) f(); } { if (b) g(); }",
        "3 # switch (i) { case 1: f(); break; case 2: g(); break; default: h(); }",
        "3 # switch (i) { case 1: f(); break; case 2: g(); break; }",
        "3 # switch (i) { case 1: case 2: f(); break; default: h(); }",
        "2 # switch (i) { case 1: f(); default: }",
        "3 # switch (i) { case 1, 2: case 3: f(); break; default: h(); }",
        "3 # switch (i) { case 1 -> f(); case 2 -> g(); }",
        "3 # switch (i) { case 1 -> { if (a) f(); } default -> g(); }",
        "4 # return switch (e) { case A -> f(); case B -> g(); };",
        "3 # switch (o) { case String s -> f(); case Integer n -> g(); }",
        "3 # switch (o) { case A -> f(); case null -> g(); }",
        "5 # return switch (o) { case A x when b && c -> f(); default -> g(); };",
        "0 # list.forEach(x -> { f(); });",
        "2 # list.forEach(x -> { if (a) f(); });",
        "0 # new T() { void m() { if (a) f(); } };",
        "2 # if (a) f(); new T() { int x; }; return o;",
        "1 # if (a) f(); new T() { void m() { } }; return o;",
        "0 # class L { void l() { if (a) f(); } }",
      })
  void ofMethod(final double expected, final String statements)
      throws IOException, CheckstyleException {
    assertEquals(expected, JavaMetricsTestSupport.ofBody(NPATH_COMPLEXITY, statements));

    // The first reported function is the method around the statements.
    final BigInteger checkstyle =
        JavaMetricsTestSupport.checkstyleOfBody(statements)
            .npathComplexities()
            .values()
            .iterator()
            .next();
    assertEquals(expected, checkstyle.doubleValue());
  }

  @Test
  void largeValuesAreFinite() {
    assertEquals(
        Math.pow(2, 200),
        JavaMetricsTestSupport.ofBody(NPATH_COMPLEXITY, "if (a) f(); ".repeat(200)));
    assertEquals(
        Double.MAX_VALUE,
        JavaMetricsTestSupport.ofBody(NPATH_COMPLEXITY, "if (a) f(); ".repeat(2000)));
  }
}
