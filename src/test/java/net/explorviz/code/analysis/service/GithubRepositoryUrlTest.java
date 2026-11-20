package net.explorviz.code.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GithubRepositoryUrlTest {

  @ParameterizedTest
  @CsvSource({
    "https://github.com/ExplorViz/code-analyzer, ExplorViz/code-analyzer",
    "https://github.com/ExplorViz/code-analyzer.git, ExplorViz/code-analyzer",
    "https://github.com/ExplorViz/code-analyzer/, ExplorViz/code-analyzer",
    "https://user@github.com/ExplorViz/code-analyzer.git, ExplorViz/code-analyzer",
    "git@github.com:ExplorViz/code-analyzer.git, ExplorViz/code-analyzer",
    "ssh://git@github.com/ExplorViz/code-analyzer.git, ExplorViz/code-analyzer",
    "https://github.com/octocat/octocat.github.io, octocat/octocat.github.io",
    "https://github.com/octocat/octocat.github.io.git, octocat/octocat.github.io",
    "https://github.com/octocat/dotgit.gitignore, octocat/dotgit.gitignore",
  })
  void extractsOwnerAndName(final String url, final String expected) {
    assertEquals(Optional.of(expected), GithubFetcherService.extractGithubRepoSubString(url));
  }

  @Test
  void rejectsUrlsWithoutOwnerAndName() {
    assertEquals(
        Optional.empty(),
        GithubFetcherService.extractGithubRepoSubString("https://github.com/ExplorViz"));
    assertEquals(
        Optional.empty(),
        GithubFetcherService.extractGithubRepoSubString("https://gitlab.com/ExplorViz/x.git"));
  }
}
