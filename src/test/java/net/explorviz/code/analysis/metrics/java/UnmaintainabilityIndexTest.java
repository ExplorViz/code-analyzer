package net.explorviz.code.analysis.metrics.java;

import static net.explorviz.code.analysis.metrics.CodeMetrics.UNMAINTAINABILITY_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.explorviz.code.analysis.metrics.UnmaintainabilityIndex;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnmaintainabilityIndexTest {

  private static final double DELTA = 1e-9;

  private static double indexOf(final double volume, final double complexity, final double lines) {
    final double maintainability =
        171 - 5.2 * Math.log(volume) - 0.23 * complexity - 16.2 * Math.log(lines);
    return 100 - maintainability * 100 / 171;
  }

  private static double volume(final int length, final int vocabulary) {
    return length * Math.log(vocabulary) / Math.log(2);
  }

  private static double ofFunctions(final int count, final double volume, final int lines) {
    final UnmaintainabilityIndex index = new UnmaintainabilityIndex();
    for (int i = 0; i < count; i++) {
      index.add(volume, 1, lines);
    }
    return index.value();
  }

  @Test
  void isTheInverseOfTheMaintainabilityIndex() {
    // MI = (171 - 5.2 * ln(1000) - 0.23 * 1 - 16.2 * ln(20)) * 100 / 171 = 50.4788...
    assertEquals(49.5212, ofFunctions(1, 1000, 20), 1e-4);
  }

  @Test
  void isLimitedToTheRangeFrom0To100() {
    assertEquals(0.0, ofFunctions(0, 0, 0));
    assertEquals(100.0, ofFunctions(1, 1e9, 100_000));
    assertTrue(ofFunctions(1, 0, 0) > 0);
    assertTrue(ofFunctions(1, 0, 0) < 1);
  }

  @Test
  void severalFunctionsAreAveraged() {
    assertEquals(ofFunctions(1, 500, 10), ofFunctions(7, 500, 10), DELTA);

    final UnmaintainabilityIndex index = new UnmaintainabilityIndex();
    index.add(100, 1, 4);
    index.add(300, 4, 12);
    assertEquals(indexOf(200, 2.5, 8), index.value(), DELTA);
  }

  /** The expected values are the length and the vocabulary including {@code Object m(...)}. */
  @ParameterizedTest
  @CsvSource(
      delimiter = '#',
      quoteCharacter = '`',
      value = {
        // Object m ( Object o , int i ) { }
        "9 # 8 # ``",
        // return o ;
        "12 # 10 # return o;",
        // return i + 1 ;
        "14 # 12 # return i + 1;",
        // return i >> 1 ; counts the shift once
        "14 # 12 # return i >> 1;",
        "14 # 12 # return i >>> 1;",
        // return i ?: 1 2 ;
        "15 # 13 # return i ? 1 : 2;",
        // List <> Object , int x = null ; with the generic brackets as one operator
        "18 # 14 # List<Object, int> x = null;",
        // return i < i > i ; differs from generic brackets
        "16 # 12 # return i < i > i;",
        // f ( ) . g ( a [ 1 ] ) ; where ( occurs three times and [ once
        "18 # 15 # f().g(a[1]);",
        // record = true ; where record is a name
        "13 # 12 # record = true;",
        // comments have no tokens
        "9 # 8 # /* return i; */",
      })
  void ofMethod(final int length, final int vocabulary, final String statements) {
    assertEquals(
        indexOf(volume(length, vocabulary), statements.contains("?") ? 2 : 1, 1),
        JavaMetricsTestSupport.ofBody(UNMAINTAINABILITY_INDEX, statements),
        DELTA);
  }

  @Test
  void linesWithoutCodeDoNotCount() {
    final String statements = "f();\n g();\n";
    final String withComments = "f();\n\n // comment\n /* a\n b */\n g();\n";
    assertEquals(
        JavaMetricsTestSupport.ofBody(UNMAINTAINABILITY_INDEX, statements),
        JavaMetricsTestSupport.ofBody(UNMAINTAINABILITY_INDEX, withComments));
  }

  @Test
  void classesAndFileAverageTheirOwnFunctions() {
    final FileData file =
        JavaMetricsTestSupport.parse(
            """
            package p;
            abstract class A {
              abstract int none();
              int one() { return 1; }
              Object two() {
                return new B() {
                  public int three() { return a && b ? 1 : 2; }
                };
              }
            }
            """);
    final ClassData outer = file.getClasses(0);
    final ClassData anonymous = outer.getInnerClasses(0);

    // int one ( ) { return 1 ; }
    final double one = volume(7, 7);
    // Object two ( ) { return new B ( ) ; } on four lines without the class body
    final double two = volume(9, 8);
    // int three ( ) { return a && b ?: 1 2 ; }
    final double three = volume(12, 12);

    assertFalse(outer.getFunctions(0).containsMetrics(UNMAINTAINABILITY_INDEX));
    assertIndex(indexOf(one, 1, 1), outer.getFunctions(1).getMetricsMap());
    assertIndex(indexOf(two, 1, 4), outer.getFunctions(2).getMetricsMap());
    assertIndex(indexOf(three, 3, 1), anonymous.getFunctions(0).getMetricsMap());
    assertIndex(indexOf((one + two) / 2, 1, 2.5), outer.getMetricsMap());
    assertIndex(indexOf(three, 3, 1), anonymous.getMetricsMap());
    assertIndex(indexOf((one + two + three) / 3, 5 / 3.0, 2), file.getMetricsMap());
  }

  @Test
  void typesWithoutCodeAreMaintainable() {
    final FileData file = JavaMetricsTestSupport.parse("interface A { int a(); }");
    assertIndex(0, file.getClasses(0).getMetricsMap());
    assertIndex(0, file.getMetricsMap());
  }

  private static void assertIndex(final double expected, final Map<String, Double> metrics) {
    assertEquals(expected, metrics.get(UNMAINTAINABILITY_INDEX), DELTA);
  }
}
