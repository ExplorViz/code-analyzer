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
import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.Variable;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class GithubSocialFetcherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GithubSocialFetcherService.class);

  @Inject
  @GraphQLClient("github")
  DynamicGraphQLClient githubClient;

  public void fetchSocialDataInRangeAsync(
      final String repositoryName,
      final Date startDate,
      final Date endDate,
      final DataExporter exporter,
      final String landscapeToken
  ) {

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String dateRange = String.format("%s..%s", sdf.format(startDate), sdf.format(endDate));

    String issueQueryStr = String.format("repo:%s is:issue created:%s", repositoryName, dateRange);
    String pullRequestQueryStr = String.format("repo:%s is:pr created:%s", repositoryName, dateRange);

    LOGGER.info("Executing Issue Search with query: {}", issueQueryStr);
    executePaginatedSearch(issueQueryStr, exporter, landscapeToken, repositoryName);

    LOGGER.info("Executing Pull Request Search with query: {}", pullRequestQueryStr);
    executePaginatedSearch(pullRequestQueryStr, exporter, landscapeToken, repositoryName);
  }

  private void executePaginatedSearch(
      String queryStr, DataExporter exporter, String landscapeToken, String repositoryName) {

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
        .setRepositoryName(repositoryName)
        .setEmail(authorEmail)
        .setName(authorLogin)
        // TODO .setLogin(authorLogin) instead?
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
        .setRepositoryName(repositoryName)
        .setResourceId(resourceId)
        .setResourceType(resourceType)
        .setActor(actor)
        .setTitle(title)
        .setDescription(description)
        .setWebUrl(webUrl)
        .setLabels(labelsStr);

    if (node.containsKey("createdAt") && !node.isNull("createdAt")) {
      events.add(baseBuilder.clone()
          .setAnnotationType(AnnotationType.CREATE)
          .setAnnotationId(id + "-CREATE")
          .setNewState(ResourceState.OPEN)
          .setEventTimestamp(parseTimestamp(node.getString("createdAt")))
          .build());
    }

    if (node.containsKey("mergedAt") && !node.isNull("mergedAt")) {
      events.add(baseBuilder.clone()
          .setAnnotationType(AnnotationType.MERGE)
          .setAnnotationId(id + "-MERGE")
          .setNewState(ResourceState.MERGED)
          .setEventTimestamp(parseTimestamp(node.getString("mergedAt")))
          .build());
    } else if (node.containsKey("closedAt") && !node.isNull("closedAt")) {
      events.add(baseBuilder.clone()
          .setAnnotationType(AnnotationType.CLOSE)
          .setAnnotationId(id + "-CLOSE")
          .setNewState(ResourceState.CLOSED)
          .setEventTimestamp(parseTimestamp(node.getString("closedAt")))
          .build());
    }

    return events;
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
                        )
                    )
                )
            )
        )
    );
  }
}
