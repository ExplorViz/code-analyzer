package net.explorviz.code.analysis.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AnalysisServiceLocLimitTest {

  @Inject
  AnalysisService analysisService;

  @Test
  void skipsFullAnalysisWhenLocExceedsLimitForSourceFiles() {
    final AnalysisConfig config = new AnalysisConfig.Builder()
        .maxLocForFullAnalysis(Optional.of(100))
        .build();

    Assertions.assertTrue(
        analysisService.shouldUseMinimalSourceAnalysis(config, "Main.java", 101));
    Assertions.assertFalse(
        analysisService.shouldUseMinimalSourceAnalysis(config, "Main.java", 100));
    Assertions.assertFalse(
        analysisService.shouldUseMinimalSourceAnalysis(config, "Main.java", 50));
  }

  @Test
  void analyzesAllFilesWhenLimitIsUnset() {
    final AnalysisConfig config = new AnalysisConfig.Builder().build();

    Assertions.assertFalse(
        analysisService.shouldUseMinimalSourceAnalysis(config, "Main.java", 10_000));
  }

  @Test
  void doesNotApplyLimitToNonSourceFiles() {
    final AnalysisConfig config = new AnalysisConfig.Builder()
        .maxLocForFullAnalysis(Optional.of(100))
        .build();

    Assertions.assertFalse(
        analysisService.shouldUseMinimalSourceAnalysis(config, "README.md", 500));
  }
}
