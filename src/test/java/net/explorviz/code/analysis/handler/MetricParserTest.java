package net.explorviz.code.analysis.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.explorviz.code.proto.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MetricParserTest {

  @Test
  void zeroIsAValue() {
    final TextFileDataHandler file = new TextFileDataHandler("a.txt", Language.PLAINTEXT);
    file.addMetric("metric", "0");
    assertEquals(0.0, file.getProtoBufObject().getMetricsOrThrow("metric"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "NaN", "Infinity", "unknown"})
  void metricsWithoutNumberHaveNoValue(final String value) {
    final TextFileDataHandler file = new TextFileDataHandler("a.txt", Language.PLAINTEXT);
    file.addMetric("metric", value);
    assertFalse(file.getProtoBufObject().containsMetrics("metric"));
  }
}
