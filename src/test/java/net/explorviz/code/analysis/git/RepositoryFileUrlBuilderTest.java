package net.explorviz.code.analysis.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryFileUrlBuilderTest {

  @Test
  void normalizesHttpsRemoteUrl() {
    assertEquals(
        Optional.of("https://github.com/spring-projects/spring-petclinic"),
        RepositoryFileUrlBuilder.normalizeRepositoryUrl(
            "https://github.com/spring-projects/spring-petclinic.git"));
  }

  @Test
  void normalizesSshRemoteUrl() {
    assertEquals(
        Optional.of("https://github.com/ExplorViz/code-analyzer"),
        RepositoryFileUrlBuilder.normalizeRepositoryUrl(
            "git@github.com:ExplorViz/code-analyzer.git"));
  }

  @Test
  void resolvesConfiguredRemoteBeforeOrigin() {
    assertEquals(
        Optional.of("https://github.com/configured/repo"),
        RepositoryFileUrlBuilder.resolveRepositoryUrl(
            Optional.of("https://github.com/configured/repo.git"),
            "git@github.com:origin/repo.git"));
  }

  @Test
  void returnsEmptyForUnknownUrlFormat() {
    assertTrue(RepositoryFileUrlBuilder.normalizeRepositoryUrl("not-a-url").isEmpty());
  }
}
