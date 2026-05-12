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
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class GithubSocialFetcherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GithubSocialFetcherService.class);

  @ConfigProperty(name = "quarkus.smallrye-graphql-client.github.url")
  String githubUrl;

  public void fetchSocialDataInRangeAsync(
      final String repositoryName,
      final Date startDate,
      final Date endDate,
      final DataExporter exporter,
      final String landscapeToken,
      final String githubToken
  ) {

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String dateRange = String.format("%s..%s", sdf.format(startDate), sdf.format(endDate));

    String issueQueryStr = String.format("repo:%s is:issue updated:%s", repositoryName, dateRange);
    String pullRequestQueryStr = String.format("repo:%s is:pr updated:%s", repositoryName, dateRange);


    try (DynamicGraphQLClient githubClient = DynamicGraphQLClientBuilder.newBuilder()
        .url(githubUrl)
        .header("Authorization", "Bearer " + githubToken)
        .build()) {

      if (!githubToken.startsWith("github_pat")) {
        throw new IllegalArgumentException("Invalid github token. Aborting GitHub Data fetching.");
      }

      LOGGER.info("Executing Issue Search with query: {}", issueQueryStr);
      executePaginatedSearch(issueQueryStr, exporter, landscapeToken, repositoryName, githubClient);

      LOGGER.info("Executing Pull Request Search with query: {}", pullRequestQueryStr);
      executePaginatedSearch(pullRequestQueryStr, exporter, landscapeToken, repositoryName, githubClient);
    } catch (Exception e) {
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

    //System.out.println("Constructed GraphQL Query: " + queryStr);

    boolean hasNextPage = true;
    String cursor = null;

    while (hasNextPage) {
      hasNextPage = false;
      try {

        Map<String, Object> variables = new HashMap<>();
        variables.put("searchQuery", queryStr);
        variables.put("searchType", "ISSUE");
        variables.put("cursor", cursor);

        Response response = githubClient.executeSync(query, variables);

        if (response.hasError()) {
          LOGGER.error("GraphQL Errors: {}", response.getErrors());
          break;
        }

        //LOGGER.info("Raw Response Data: {}", response.getData().toString());

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
          //System.out.println("Node " + i + ": " + node.toString());
          List<TrackableResourceEvent> events = mapToEvents(node, landscapeToken, repositoryName);

          for (TrackableResourceEvent event : events) {
            LOGGER.info(
                "Generated Event: {} for resource {} with state {}",
                event.getAnnotationType(),
                event.getResourceId(),
                event.getNewState()
            );
            exporter.persistTrackableResourceEvent(event);
            // TODO: test, integrate into git analysis, decide on window
          }
        }

      } catch (Exception e) {
        LOGGER.error("Error fetching data from GitHub: {}", e.getMessage(), e);
        break;
      }
    }
  }

  private List<TrackableResourceEvent> mapToEvents(
      JsonObject node, String landscapeToken, String repositoryName) {
    List<TrackableResourceEvent> events = new ArrayList<>();

    // FIX 1: Check containsKey FIRST, then isNull
    String typeName = (node.containsKey("__typename") && !node.isNull("__typename"))
        ? node.getString("__typename") : "Unknown";

    final TrackableResourceType resourceType = typeName.equals("Issue")
        ? TrackableResourceType.ISSUE
        : TrackableResourceType.PULL_REQUEST;

    LOGGER.info("Processing {}, {}", typeName, node.getInt("number"));

    String resourceId = String.valueOf(node.getInt("number"));

    String id = (node.containsKey("id") && !node.isNull("id")) ? node.getString("id") : "";

    String title = (node.containsKey("title") && !node.isNull("title")) ? node.getString("title") : "";

    String description = (node.containsKey("body") && !node.isNull("body")) ? node.getString("body") : "";

    String webUrl = (node.containsKey("url") && !node.isNull("url")) ? node.getString("url") : "";

    String rawState = (
        node.containsKey("state") && !node.isNull("state")) ? node.getString("state") : "OPEN";

    String repoName = repositoryName.split("/")[1];

    ResourceState resourceState = switch (rawState) {
      case "OPEN" -> ResourceState.OPEN;
      case "CLOSED" -> ResourceState.CLOSED;
      case "MERGED" -> ResourceState.MERGED;
      default -> {
        LOGGER.warn("Unknown state '{}' for resource {}, defaulting to OPEN", rawState, resourceId);
        yield ResourceState.OPEN;
      }
    };

    // Safe Author parsing
    String authorLogin = "unknown";
    String authorEmail = "";
    String avatarUrl = "";
    String authorName = "unknown";

    if (node.containsKey("author") && !node.isNull("author")) {
      JsonObject authorObj = node.getJsonObject("author");
      authorLogin = (authorObj.containsKey("login") && !authorObj.isNull("login"))
          ? authorObj.getString("login") : "unknown";

      authorEmail = (authorObj.containsKey("email") && !authorObj.isNull("email"))
          ? authorObj.getString("email") : "";

      avatarUrl = (authorObj.containsKey("avatarUrl") && !authorObj.isNull("avatarUrl"))
          ? authorObj.getString("avatarUrl") : "";

      authorName = (authorObj.containsKey("name") && !authorObj.isNull("name"))
          ? authorObj.getString("name") : authorName;
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

    TrackableResourceEvent.Builder baseBuilder = TrackableResourceEvent.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setResourceId(resourceId)
        .setResourceType(resourceType)
        .setActor(actor)
        .setTitle(title)
        .setDescription(description)
        .setWebUrl(webUrl)
        .setLabels(labelsStr);

    if (node.containsKey("createdAt") && !node.isNull("createdAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.CREATE)
          .setAnnotationId(id)
          .setNewState(ResourceState.OPEN)
          .setEventTimestamp(parseTimestamp(node.getString("createdAt")))
          .build();
      LOGGER.info("Fetched event: {} for {} by {}", event.getAnnotationType(), resourceId,
          event.getActor().getGithubLogin());
      events.add(event);
    }

    if (node.containsKey("mergedAt") && !node.isNull("mergedAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.MERGE)
          .setAnnotationId(id + "-MERGE")
          .setNewState(ResourceState.MERGED)
          .setEventTimestamp(parseTimestamp(node.getString("mergedAt")))
          .build();
      LOGGER.info("Fetched event: {} for {} by {}", event.getAnnotationType(), resourceId,
          event.getActor().getGithubLogin());
      events.add(event);
    } else if (node.containsKey("closedAt") && !node.isNull("closedAt")) {
      TrackableResourceEvent event = baseBuilder.clone()
          .setAnnotationType(AnnotationType.CLOSE)
          .setAnnotationId(id + "-CLOSE")
          .setNewState(ResourceState.CLOSED)
          .setEventTimestamp(parseTimestamp(node.getString("closedAt")))
          .build();
      LOGGER.info("Fetched event: {} for {} by {}", event.getAnnotationType(), resourceId,
          event.getActor().getGithubLogin());
      events.add(event);
    }

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

        ResourceState newState = resourceState; // Default to current state

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

        ContributorData eventActor = actor.toBuilder()
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
        
        LOGGER.info("Fetched timeline event: {} for {} by {}", event.getAnnotationType(), resourceId,
            event.getActor().getGithubLogin());
        events.add(event);
      }
    }

    return events;
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

  public Timestamp parseTimestamp(String isoTimestamp) {
    if (isoTimestamp == null || isoTimestamp.isBlank()) {
      return Timestamp.getDefaultInstance(); // Safe fallback
    }

    // 1. Parse the ISO-8601 string into a Java Instant
    Instant instant = Instant.parse(isoTimestamp);

    // 2. Build the Protobuf Timestamp explicitly
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
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
            )
        )
    );
  }
}
