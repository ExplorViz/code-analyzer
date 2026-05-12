package net.explorviz.code.analysis.service;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Argument.args;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.InlineFragment.on;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;
import static io.smallrye.graphql.client.core.Variable.vars;
import static io.smallrye.graphql.client.core.VariableType.nonNull;

import com.google.protobuf.Timestamp;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.Variable;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.proto.AnnotationType;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.ResourceState;
import net.explorviz.code.proto.TrackableResourceEvent;
import net.explorviz.code.proto.TrackableResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service to fetch GitHub social data for a given repository.
 */
@ApplicationScoped
public class GithubCollaborationFetcherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GithubCollaborationFetcherService.class);

  final String githubUrl = "https://api.github.com/graphql";

  /**
   * Fetches social data for a given time range.
   *
   * @param repoOwnerAndName the name of the owner and repository
   * @param startDate the date for the analysis to start
   * @param endDate the date for the analysis to end
   * @param exporter the exporter to use for sending data
   * @param landscapeToken the landscape token to use
   * @param githubToken the GitHub personal access token to use
   */
  @SuppressWarnings("try")
  public void fetchSocialDataInRange(
      final String repoOwnerAndName,
      final Date startDate,
      final Date endDate,
      final DataExporter exporter,
      final String landscapeToken,
      final String githubToken
  ) {

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String dateRange = String.format("%s..%s", sdf.format(startDate), sdf.format(endDate));

    // predefine base graphql queries
    String issueQueryStr = String.format("repo:%s is:issue updated:%s", repoOwnerAndName, dateRange);
    String pullRequestQueryStr = String.format("repo:%s is:pr updated:%s", repoOwnerAndName, dateRange);

    try (DynamicGraphQLClient githubClient = DynamicGraphQLClientBuilder.newBuilder()
        .url(githubUrl)
        .header("Authorization", "Bearer " + githubToken)
        .build()) {

      if (!githubToken.startsWith("github_pat")) {
        throw new IllegalArgumentException("Invalid github token. Aborting GitHub Data fetching.");
      }

      LOGGER.info("Initiating Issue search with query: {}", issueQueryStr);
      executePaginatedSearch(issueQueryStr, exporter, landscapeToken, repoOwnerAndName, githubClient);

      LOGGER.info("Initiating Pull-Request search with query: {}", pullRequestQueryStr);
      executePaginatedSearch(pullRequestQueryStr, exporter, landscapeToken, repoOwnerAndName, githubClient);

      LOGGER.info("✅ Completed all social fetch queries.");
    } catch (Exception e) {
      if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("Failed to fetch social data: {}", e.getMessage(), e);
    }
  }

  private void executePaginatedSearch(
      String queryStr, DataExporter exporter, String landscapeToken, String repositoryName,
      DynamicGraphQLClient githubClient) {

    // Build query
    Document query = buildUnifiedSearchQuery(
        var("searchQuery", nonNull("String")),
        var("searchType", nonNull("SearchType")),
        var("cursor", "String")
        );

    boolean hasNextPage = true;
    String cursor = null;

    while (hasNextPage) {
      try {

        Map<String, Object> variables = new HashMap<>();
        variables.put("searchQuery", queryStr);
        variables.put("searchType", "ISSUE");
        variables.put("cursor", cursor);

        Response response = githubClient.executeSync(query, variables);

        if (response.hasError() || isRateLimitExceeded(response)) {
          break;
        }

        JsonObject search = response.getData().getJsonObject("search");
        JsonObject pageInfo = search.getJsonObject("pageInfo");

        hasNextPage = pageInfo.getBoolean("hasNextPage", false);

        if (hasNextPage && pageInfo.containsKey("endCursor") && !pageInfo.isNull("endCursor")) {
          cursor = pageInfo.getString("endCursor");
        } else {
          cursor = null;
        }

        JsonArray nodes = search.getJsonArray("nodes");

        for (int i = 0; i < nodes.size(); i++) {
          JsonObject node = nodes.getJsonObject(i);
          List<TrackableResourceEvent> events = mapToEvents(node, landscapeToken, repositoryName);

          for (TrackableResourceEvent event : events) {
            exporter.persistTrackableResourceEvent(event);
          }
        }

      } catch (Exception e) {
        LOGGER.error("Error fetching data from GitHub: {}", e.getMessage(), e);
        break;
      }
    }
    LOGGER.info("completed social data fetch for query {}", queryStr);
  }

  private List<TrackableResourceEvent> mapToEvents(
      JsonObject node, String landscapeToken, String repositoryName) {

    List<TrackableResourceEvent> events = new ArrayList<>();

    TrackableResourceEvent.Builder baseBuilder = parseBaseResource(node, landscapeToken, repositoryName);
    if (baseBuilder == null) {
      return events;
    }

    events.addAll(generateLifecycleEvents(node, baseBuilder));

    if (node.containsKey("timelineItems") && !node.isNull("timelineItems")) {
      events.addAll(parseTimelineEvents(node, baseBuilder));
    }

    return events;
  }

  private TrackableResourceEvent.Builder parseBaseResource(
      JsonObject node, String landscapeToken, String repositoryName) {

    String typeName = getJsonString(node, "__typename", "Unknown");

    final TrackableResourceType resourceType = typeName.equals("Issue")
        ? TrackableResourceType.ISSUE
        : TrackableResourceType.PULL_REQUEST;

    String resourceId = String.valueOf(node.getInt("number"));
    String title = getJsonString(node, "title", "");
    String description = getJsonString(node, "body", "");
    String webUrl = getJsonString(node, "url", "");
    //  String rawState = getJsonString(node, "state", "OPEN");
    String repoName = repositoryName.split("/")[1];

    String authorLogin = "unknown";
    String authorEmail = "";
    String avatarUrl = "";

    if (node.containsKey("author") && !node.isNull("author")) {
      JsonObject authorObj = node.getJsonObject("author");
      authorLogin = getJsonString(authorObj, "login", "unknown");
      authorEmail = getJsonString(authorObj, "email", "");
      avatarUrl = getJsonString(authorObj, "avatarUrl", "");
    }

    ContributorData actor = ContributorData.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setEmail(authorEmail)
        .setGithubLogin(authorLogin)
        .setGitUsername(authorLogin)
        .setAvatarUrl(avatarUrl)
        .build();

    // Parse Labels
    String labelsStr = "";
    if (node.containsKey("labels") && !node.isNull("labels")) {
      jakarta.json.JsonArray labelNodes = node.getJsonObject("labels").getJsonArray("nodes");
      List<String> labelNames = new ArrayList<>();
      for (int l = 0; l < labelNodes.size(); l++) {
        labelNames.add(labelNodes.getJsonObject(l).getString("name", "unknown"));
      }
      labelsStr = String.join(",", labelNames);
    }

    return TrackableResourceEvent.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setResourceId(resourceId)
        .setResourceType(resourceType)
        .setActor(actor)
        .setTitle(title)
        .setDescription(description)
        .setWebUrl(webUrl)
        .setLabels(labelsStr);
  }

  private List<TrackableResourceEvent> generateLifecycleEvents(
      JsonObject node, TrackableResourceEvent.Builder baseBuilder) {

    List<TrackableResourceEvent> events = new ArrayList<>();

    String id = getJsonString(node, "id", "");

    if (node.containsKey("createdAt") && !node.isNull("createdAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.CREATE)
          .setAnnotationId(id)
          .setNewState(ResourceState.OPEN)
          .setEventTimestamp(parseTimestamp(node.getString("createdAt")))
          .build();
      events.add(event);
    }

    if (node.containsKey("mergedAt") && !node.isNull("mergedAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.MERGE)
          .setAnnotationId(id)
          .setNewState(ResourceState.MERGED)
          .setEventTimestamp(parseTimestamp(node.getString("mergedAt")))
          .build();
      events.add(event);
    } else if (node.containsKey("closedAt") && !node.isNull("closedAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.CLOSE)
          .setAnnotationId(id)
          .setNewState(ResourceState.CLOSED)
          .setEventTimestamp(parseTimestamp(node.getString("closedAt")))
          .build();
      events.add(event);
    }
    return events;
  }

  private List<TrackableResourceEvent> parseTimelineEvents(
      JsonObject node, TrackableResourceEvent.Builder baseBuilder) {

    List<TrackableResourceEvent> events = new ArrayList<>();
    String id = getJsonString(node, "id", "");

    String authorLogin = baseBuilder.getActor().getGithubLogin();

    // Process Timeline Items
    if (node.containsKey("timelineItems") && !node.isNull("timelineItems")) {
      jakarta.json.JsonArray timelineNodes = node.getJsonObject("timelineItems").getJsonArray("nodes");
      for (int i = 0; i < timelineNodes.size(); i++) {
        JsonObject eventNode = timelineNodes.getJsonObject(i);
        String type = eventNode.getString("__typename", "");

        AnnotationType annotationType = mapToAnnotationType(type);
        if (annotationType == null) {
          continue;
        }

        String timestamp = eventNode.containsKey("createdAt") ? eventNode.getString("createdAt") : "";

        // Determine Actor
        String eventActorLogin = authorLogin; // default to resource author
        if (eventNode.containsKey("actor") && !eventNode.isNull("actor")) {
          eventActorLogin = eventNode.getJsonObject("actor").getString("login", authorLogin);
        } else if (eventNode.containsKey("author") && !eventNode.isNull("author")) {
          eventActorLogin = eventNode.getJsonObject("author").getString("login", authorLogin);
        }

        ResourceState newState = baseBuilder.getNewState(); // Default to current state

        // Special handling for PullRequestCommit
        if ("PullRequestCommit".equals(type)) {
          JsonObject commitNode = eventNode.getJsonObject("commit");
          timestamp = commitNode.getString("authoredDate", "");
          if (commitNode.containsKey("author") && !commitNode.isNull("author")) {
            JsonObject commitAuthor = commitNode.getJsonObject("author");
            if (commitAuthor.containsKey("user") && !commitAuthor.isNull("user")) {
              eventActorLogin = commitAuthor.getJsonObject("user").getString("login", authorLogin);
            }
          }
        }

        // State Transitions
        if (annotationType == AnnotationType.CLOSE) {
          newState = ResourceState.CLOSED;
        } else if (annotationType == AnnotationType.REOPEN) {
          newState = ResourceState.OPEN;
        } else if (annotationType == AnnotationType.MERGE) {
          newState = ResourceState.MERGED;
        }

        ContributorData eventActor = baseBuilder.getActor().toBuilder()
            .setGithubLogin(eventActorLogin)
            .setGitUsername(eventActorLogin)
            .build();

        TrackableResourceEvent event = baseBuilder.clone()
            .setAnnotationType(annotationType)
            .setAnnotationId(id + "-" + type + "-" + i)
            .setEventTimestamp(parseTimestamp(timestamp))
            .setActor(eventActor)
            .setNewState(newState)
            .build();

        events.add(event);
      }
    }
    return events;
  }

  private Document buildUnifiedSearchQuery(Variable queryVar, Variable typeVar, Variable cursorVar) {
    return document(
        operation(
            vars(queryVar, typeVar, cursorVar),
            field("search",
                args(
                    arg("query", queryVar),
                    arg("type", typeVar),
                    arg("first", 100),
                    arg("after", cursorVar)
                ),
                field("pageInfo",
                    field("hasNextPage"),
                    field("endCursor")
                ),
                field("nodes",
                    field("__typename"),

                    on("Issue",
                        field("id"),
                        field("number"),
                        field("title"),
                        field("body"),
                        field("url"),
                        field("state"),
                        field("createdAt"),
                        field("closedAt"),
                        field("author",
                            field("login"),
                            field("avatarUrl"),
                            on("User",
                                field("name"),
                                field("email")
                            )
                        ),
                        field("labels", args(arg("first", 10)),
                            field("nodes", field("name"))
                        ),
                        field("timelineItems", args(arg("first", 50)),
                            field("nodes",
                                field("__typename"),
                                on("ClosedEvent", field("createdAt"), field("actor", field("login"))),
                                on("ReopenedEvent", field("createdAt"), field("actor", field("login"))),
                                on("IssueComment", field("createdAt"), field("author", field("login")))
                            )
                        )
                    ),
                    on("PullRequest",
                        field("id"),
                        field("number"),
                        field("title"),
                        field("body"),
                        field("url"),
                        field("state"),
                        field("createdAt"),
                        field("closedAt"),
                        field("mergedAt"),
                        field("author",
                            field("login"),
                            field("avatarUrl"),
                            on("User",
                                field("name"),
                                field("email")
                            )
                        ),
                        field("labels", args(arg("first", 10)),
                            field("nodes", field("name"))
                        ),
                        field("timelineItems", args(arg("first", 50)),
                            field("nodes",
                                field("__typename"),
                                on("ClosedEvent", field("createdAt"), field("actor", field("login"))),
                                on("MergedEvent", field("createdAt"), field("actor", field("login"))),
                                on("ReopenedEvent", field("createdAt"), field("actor", field("login"))),
                                on("IssueComment", field("createdAt"), field("author", field("login"))),
                                on("HeadRefForcePushedEvent", field("createdAt"), field("actor", field("login"))),
                                on("PullRequestCommit",
                                    field("commit",
                                        field("authoredDate"),
                                        field("author", field("user", field("login")))
                                    )
                                ),
                                on("PullRequestReview", field("createdAt"), field("author", field("login")))
                            )
                        )
                    )
                )
            ),
            field("rateLimit",
                field("cost"),
                field("remaining"),
                field("resetAt")
            )
        )
    );
  }

  private AnnotationType mapToAnnotationType(String typeName) {
    return switch (typeName) {
      case "ClosedEvent" -> AnnotationType.CLOSE;
      case "MergedEvent" -> AnnotationType.MERGE;
      case "ReopenedEvent" -> AnnotationType.REOPEN;
      case "IssueComment" -> AnnotationType.COMMENT;
      case "PullRequestCommit" -> AnnotationType.COMMIT;
      case "PullRequestReview" -> AnnotationType.REVIEW;
      case "HeadRefForcePushedEvent" -> AnnotationType.FORCE_PUSH;
      default -> null;
    };
  }

  private boolean isRateLimitExceeded(Response response) {

    // Check Rate Limit
    if (response.getData().containsKey("rateLimit")) {
      JsonObject rateLimit = response.getData().getJsonObject("rateLimit");
      int cost = rateLimit.getInt("cost");
      int remaining = rateLimit.getInt("remaining");
      String resetAt = rateLimit.getString("resetAt");

      LOGGER.info("GitHub API Rate Limit: Cost: {}, Remaining: {}, Resets At: {}", cost, remaining, resetAt);

      if (remaining < 50) {
        LOGGER.warn("GitHub API Rate Limit reached (Remaining: {}). Stopping social data fetch.", remaining);
        return true;
      }
    }
    return false;
  }

  private String getJsonString(JsonObject obj, String key, String defaultValue) {
    return (obj != null && obj.containsKey(key) && !obj.isNull(key))
        ? obj.getString(key)
        : defaultValue;
  }

  public Timestamp parseTimestamp(String isoTimestamp) {
    if (isoTimestamp == null || isoTimestamp.isBlank()) {
      return Timestamp.getDefaultInstance(); // Safe fallback
    }

    // parse instant to protobuf timestamp
    Instant instant = Instant.parse(isoTimestamp);
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
