package net.explorviz.code.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import net.explorviz.code.proto.AnnotationType;
import net.explorviz.code.proto.TrackableResourceEvent;
import net.explorviz.code.proto.TrackableResourceType;
import org.junit.jupiter.api.Test;

class GithubFetcherParseTest {

  private static final String TOKEN = "test-token";
  private static final String REPO = "kieker-monitoring/kieker";
  private static final String ISSUE_BASIC = "issue-basic.json";

  private final GithubFetcherService service =
      new GithubFetcherService();

  // --- fixture helpers -----------------------------------------------------

  /** Loads {@code search.nodes} from a fixture on the test classpath. */
  private static JsonArray loadNodes(final String fixture) {
    final String resource = "/github/" + fixture;
    try (InputStream in =
            GithubFetcherParseTest.class.getResourceAsStream(resource);
         JsonReader reader = Json.createReader(in)) {
      return reader.readObject().getJsonObject("search").getJsonArray("nodes");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static JsonObject nodeByNumber(final JsonArray nodes, final int number) {
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.getJsonObject(i).getInt("number") == number) {
        return nodes.getJsonObject(i);
      }
    }
    throw new IllegalArgumentException("No node with number " + number);
  }

  private static List<AnnotationType> annotationTypes(
      final List<TrackableResourceEvent> events) {
    return events.stream()
        .map(TrackableResourceEvent::getAnnotationType)
        .collect(Collectors.toList());
  }

  // --- worked example: field extraction ------------------------------------

  @Test
  void parseBaseResource_closedIssue_extractsCoreFields() {
    final JsonObject issue = nodeByNumber(loadNodes(ISSUE_BASIC), 3008);

    final TrackableResourceEvent base =
        service.parseBaseResource(issue, TOKEN, REPO).build();

    assertEquals(TrackableResourceType.ISSUE, base.getResourceType());
    assertEquals("3008", base.getResourceId());
    assertEquals("[GH-25] Delete Empty Tool runtime-analysis", base.getTitle());
    assertEquals("user-1", base.getActor().getGithubLogin());
    // repositoryName is stored bare (owner stripped) — see parseBaseResource
    assertEquals("kieker", base.getRepositoryName());
    assertTrue(base.getLabelsList().isEmpty());
  }

  @Test
  void mapToEvents_mergedPullRequest_producesExpectedEventSet() {
    final JsonObject pr = nodeByNumber(loadNodes("pr-merged-closing-issue.json"), 2868);

    final List<TrackableResourceEvent> events = service.mapToEvents(pr, TOKEN, REPO);
    final List<AnnotationType> types = annotationTypes(events);

    //System.out.println(types);

    // Use `types` (List<AnnotationType>) to check what mapToEvents produced.
    assertNotNull(types);
    assertEquals(16, types.size());
  }

  @Test
  void parseBaseResource_closingIssue_extractsCoreFields() {
    final JsonObject pr = nodeByNumber(loadNodes("pr-merged-closing-issue.json"), 2868);
    TrackableResourceEvent.Builder baseBuilder = service.parseBaseResource(pr, TOKEN, REPO);
    TrackableResourceEvent event = baseBuilder.build();
    assertEquals(TrackableResourceType.PULL_REQUEST, event.getResourceType());
    assertEquals(1, event.getReferencedIssueNumbersCount());
    assertEquals(2855, event.getReferencedIssueNumbers(0));
  }
}
