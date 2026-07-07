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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.explorviz.code.analysis.export.DataExporter;
import net.explorviz.code.analysis.git.RepositoryFileUrlBuilder;
import net.explorviz.code.proto.AnnotationType;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.ResourceState;
import net.explorviz.code.proto.TrackableResourceEvent;
import net.explorviz.code.proto.TrackableResourceType;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Service to fetch GitHub social data for a given repository.
 */
@ApplicationScoped
public class GithubFetcherService {

  private static final Logger LOGGER = LoggerFactory.getLogger(GithubFetcherService.class);

  private static final String GITHUB_URL = "https://api.github.com/graphql";
  private static final Integer PAGE_SIZE = 100;
  private static final Integer NUM_LABELS = 10;
  private static final Integer NUM_TIMELINE_ITEMS = 100;
  private static final Integer NUM_COMMITS = 100;
  private static final Integer NUM_ISSUE_REFERENCES = 20;


  Optional<CompletableFuture<Void>> fetchSocialData(
      final AnalysisConfig config, final DataExporter exporter, final ManagedExecutor managedExecutor) {
    final int days = config.socialDataTimeFrameDays().orElse(365);
    final Date endDate = determineEndDate(config);
    final Date startDate = Date.from(endDate.toInstant().minus(days, ChronoUnit.DAYS));
    return fetchSocialData(config, exporter, managedExecutor, startDate, endDate);
  }

  Optional<CompletableFuture<Void>> fetchSocialData(
      final AnalysisConfig config,
      final DataExporter exporter,
      ManagedExecutor managedExecutor,
      Date startDate, Date endDate) {
    if (!config.fetchSocialData()) {
      LOGGER.info("Skipping GitHub social data fetch, not enabled in config.");
      return Optional.empty();
    }

    if (config.repoRemoteUrl().isEmpty()) {
      LOGGER.info("Skipping GitHub social data fetch, no remote URL configured.");
      return Optional.empty();
    }

    // determine repo sub string with format "owner/repo" needed for graphql query
    final Optional<String> repoSubString = extractGithubRepoSubString(config.repoRemoteUrl().get());
    if (repoSubString.isEmpty()) {
      return Optional.empty();
    }

    // send state data before fetching to make sure precondition is met
    preInitializeRemoteState(config, exporter, config.branch().orElse("main"), "");

    return Optional.of(
        managedExecutor.runAsync(() -> {
              try {
                LOGGER.info("Starting independent background fetch for GitHub Social Data from {} to {}",
                    startDate, endDate);
                fetchSocialDataInRange(
                    repoSubString.get(),
                    startDate,
                    endDate,
                    exporter,
                    config.landscapeToken(),
                    config.gitPassword().orElse(""));
              } catch (final Exception e) {
                LOGGER.error("Background social fetch aborted: {}", e.getMessage());
              }
            }
        )
    );
  }

  private Optional<String> extractGithubRepoSubString(String remoteUrl) {
    if (!remoteUrl.contains("github.com")) {
      LOGGER.info("Skipping GitHub collaboration data fetch, not a GitHub repository: {}", remoteUrl);
      return Optional.empty();
    }
    final String[] parts = remoteUrl.split("github.com[:/]");
    if (parts.length < 2) {
      LOGGER.warn("Could not extract repo name from GitHub URL: {}", remoteUrl);
      return Optional.empty();
    }
    return Optional.of(parts[1].replace(".git", ""));
  }

  private Date determineEndDate(AnalysisConfig config) {

    Date endDate = Date.from(Instant.now()); // Default fallback
    if (config.fetchEndDate().isPresent() && !config.fetchEndDate().get().isBlank()) {
      final String dateStr = config.fetchEndDate().get();
      try {
        // Try parsing ISO timestamp first
        endDate = Date.from(Instant.parse(dateStr));
      } catch (final DateTimeParseException e) {
        // Fallback to simple date parsing "YYYY-MM-DD"
        endDate = Date.from(LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant());
      }
    }
    return endDate;
  }

  void preInitializeRemoteState(final AnalysisConfig config, final DataExporter exporter,
      final String branch, final String repositoryUrl) {
    if (exporter.isRemote()) {
      try {
        final String resolvedRepositoryUrl =
            RepositoryFileUrlBuilder.resolveRepositoryUrl(
                    repositoryUrl.isBlank()
                        ? config.repoRemoteUrl()
                        : Optional.of(repositoryUrl),
                    "")
                .orElse("");
        exporter.getStateData(
            config.getRepositoryName(),
            branch,
            config.landscapeToken(),
            config.applicationPathsMap(),
            resolvedRepositoryUrl,
            true);
      } catch (final Exception e) {
        LOGGER.warn("Could not pre-initialize remote state: {}", e.getMessage());
      }
    }
  }

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
        .url(GITHUB_URL)
        .header("Authorization", "Bearer " + githubToken)
        .build()) {

      if (!isTokenValid(githubToken)) {
        return;
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

  private boolean isTokenValid(final String token) {
    Document viewerQuery = document(
        operation(
            field("viewer",
                field("login")
            )
        )
    );

    try (DynamicGraphQLClient githubClient = DynamicGraphQLClientBuilder.newBuilder()
        .url(GITHUB_URL)
        .header("Authorization", "Bearer " + token)
        .build()) {

      githubClient.executeSync(viewerQuery);

    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid GitHub token, aborting GitHub Data fetching: " + e.getMessage(), e);
    }

    return true;
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

        //        String pathString = String.format("json/response%d.json", System.currentTimeMillis());
        //        java.nio.file.Path path = java.nio.file.Path.of(pathString);V
        //        Files.createDirectories(path.getParent());
        //
        //        java.nio.file.Files.writeString(
        //            path,
        //            response.getData().toString());

        if (response.hasError()) {
          LOGGER.warn("GraphQL query {} failed with errors: {}", queryStr, response.getErrors());
          break;
        } else if (isRateLimitExceeded(response)) {
          LOGGER.warn("Rate-limit exceeded for query: {}", queryStr);
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

  List<TrackableResourceEvent> mapToEvents(
      JsonObject node, String landscapeToken, String repositoryName) {

    List<TrackableResourceEvent> events = new ArrayList<>();

    TrackableResourceEvent.Builder baseBuilder = parseBaseResource(node, landscapeToken, repositoryName);
    if (baseBuilder == null) {
      return events;
    }


    final List<TrackableResourceEvent> lifecycleEvents = generateLifecycleEvents(node, baseBuilder);

    List<TrackableResourceEvent> timelineEvents = new ArrayList<>();
    if (node.containsKey("timelineItems") && !node.isNull("timelineItems")) {
      timelineEvents = parseTimelineEvents(node, baseBuilder);
    }

    final Set<AnnotationType> timelineTypes = EnumSet.noneOf(AnnotationType.class);
    for (final TrackableResourceEvent timelineEvent : timelineEvents) {
      timelineTypes.add(timelineEvent.getAnnotationType());
    }

    // skip synthetic CLOSE or MERGE events if full timeline event exists
    for (final TrackableResourceEvent lifecycleEvent : lifecycleEvents) {
      if (!timelineTypes.contains(lifecycleEvent.getAnnotationType())) {
        events.add(lifecycleEvent);
      }
    }
    events.addAll(timelineEvents);

    return events;
  }

  TrackableResourceEvent.Builder parseBaseResource(
      JsonObject node, String landscapeToken, String repositoryName) {

    String typeName = getJsonString(node, "__typename", "Unknown");

    final TrackableResourceType resourceType = typeName.equals("Issue")
        ? TrackableResourceType.ISSUE
        : TrackableResourceType.PULL_REQUEST;

    //  String rawState = getJsonString(node, "state", "OPEN");

    String authorLogin = "unknown";
    String authorEmail = "";
    String avatarUrl = "";

    if (node.containsKey("author") && !node.isNull("author")) {
      JsonObject authorObj = node.getJsonObject("author");
      authorLogin = getJsonString(authorObj, "login", "unknown");
      authorEmail = getJsonString(authorObj, "email", "");
      avatarUrl = getJsonString(authorObj, "avatarUrl", "");
    }


    // Parse Labels
    List<String> labelNames = new ArrayList<>();
    if (node.containsKey("labels") && !node.isNull("labels")) {
      jakarta.json.JsonArray labelNodes = node.getJsonObject("labels").getJsonArray("nodes");
      for (int l = 0; l < labelNodes.size(); l++) {
        labelNames.add(labelNodes.getJsonObject(l).getString("name", "unknown"));
      }
    }
    // Commit SHAs
    List<String> commitShas = new ArrayList<>();
    if (node.containsKey("commits") && !node.isNull("commits")) {
      JsonArray commitsNodes = node.getJsonObject("commits").getJsonArray("nodes");
      for (int c = 0; c < commitsNodes.size(); c++) {
        commitShas.add(commitsNodes.getJsonObject(c).getJsonObject("commit").getString("oid", ""));
      }
    }
    if (node.containsKey("mergeCommit") && !node.isNull("mergeCommit")) {
      commitShas.add(node.getJsonObject("mergeCommit").getString("oid", ""));
    }

    List<Integer> closingIssuesReferences = new ArrayList<>();
    if (node.containsKey("closingIssuesReferences") && !node.isNull("closingIssuesReferences")) {
      JsonArray refNodes = node.getJsonObject("closingIssuesReferences").getJsonArray("nodes");
      for (int c = 0; c < refNodes.size(); c++) {
        closingIssuesReferences.add(refNodes.getJsonObject(c).getInt("number"));
      }
    }

    String resourceId = String.valueOf(node.getInt("number"));
    String title = getJsonString(node, "title", "");
    String description = getJsonString(node, "body", "");
    String webUrl = getJsonString(node, "url", "");
    String repoName = repositoryName.split("/")[1];

    ContributorData actor = ContributorData.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setEmail(authorEmail)
        .setGithubLogin(authorLogin)
        .setGitUsername(authorLogin)
        .setAvatarUrl(avatarUrl)
        .build();

    return TrackableResourceEvent.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setResourceId(resourceId)
        .setResourceType(resourceType)
        .setActor(actor)
        .setTitle(title)
        .setDescription(description)
        .setWebUrl(webUrl)
        .addAllLabels(labelNames)
        .addAllCommitShas(commitShas)
        .addAllReferencedIssueNumbers(closingIssuesReferences);
  }

  private List<TrackableResourceEvent> generateLifecycleEvents(
      JsonObject node, TrackableResourceEvent.Builder baseBuilder) {

    List<TrackableResourceEvent> events = new ArrayList<>();

    String id = getJsonString(node, "id", "");

    if (node.containsKey("createdAt") && !node.isNull("createdAt")) {
      Optional<Timestamp> timestamp = parseTimestamp(node.getString("createdAt"));
      if (timestamp.isEmpty()) {
        LOGGER.warn("Skipping CREATE event for id={}: missing or invalid createdAt", id);
      } else {
        events.add(baseBuilder.clone()
            .setAnnotationType(AnnotationType.CREATE)
            .setAnnotationId(id + "-" + AnnotationType.CREATE.name())
            .setNewState(ResourceState.OPEN)
            .setEventTimestamp(timestamp.get())
            .build());
      }
    }

    if (node.containsKey("mergedAt") && !node.isNull("mergedAt")) {
      Optional<Timestamp> timestamp = parseTimestamp(node.getString("mergedAt"));
      if (timestamp.isEmpty()) {
        LOGGER.warn("Skipping MERGE event for id={}: missing or invalid mergedAt", id);
      } else {
        events.add(baseBuilder.clone()
            .setAnnotationType(AnnotationType.MERGE)
            .setAnnotationId(id + "-" + AnnotationType.MERGE.name())
            .setNewState(ResourceState.MERGED)
            .setEventTimestamp(timestamp.get())
            .build());
      }
    } else if (node.containsKey("closedAt") && !node.isNull("closedAt")) {
      Optional<Timestamp> timestamp = parseTimestamp(node.getString("closedAt"));
      if (timestamp.isEmpty()) {
        LOGGER.warn("Skipping CLOSE event for id={}: missing or invalid closedAt", id);
      } else {
        events.add(baseBuilder.clone()
            .setAnnotationType(AnnotationType.CLOSE)
            .setAnnotationId(id + "-" + AnnotationType.CLOSE.name())
            .setNewState(ResourceState.CLOSED)
            .setEventTimestamp(timestamp.get())
            .build());
      }
    }
    return events;
  }

  private List<TrackableResourceEvent> parseTimelineEvents(
      JsonObject node, TrackableResourceEvent.Builder baseBuilder) {

    List<TrackableResourceEvent> events = new ArrayList<>();
    String id = getJsonString(node, "id", "");

    ContributorData baseActor = baseBuilder.getActor();

    // Process Timeline Items
    if (node.containsKey("timelineItems") && !node.isNull("timelineItems")) {
      jakarta.json.JsonArray timelineNodes = node.getJsonObject("timelineItems").getJsonArray("nodes");
      for (int i = 0; i < timelineNodes.size(); i++) {
        JsonObject eventNode = timelineNodes.getJsonObject(i);
        String type = eventNode.getString("__typename", "");

        // skip closedEvent if merged like generateLifecycleEvents
        if ("ClosedEvent".equals(type) && node.containsKey("mergedAt") && !node.isNull("mergedAt")) {
          continue;
        }

        AnnotationType annotationType = mapToAnnotationType(type);
        if (annotationType == null) {
          continue;
        }

        String timestamp = eventNode.containsKey("createdAt") ? eventNode.getString("createdAt") : "";

        // Determine Actor
        String eventActorLogin = "";
        String eventActorEmail = "";
        String eventActorAvatarUrl = "";
        if (eventNode.containsKey("actor") && !eventNode.isNull("actor")) {
          JsonObject actorObj = eventNode.getJsonObject("actor");
          eventActorLogin = getJsonString(actorObj, "login", "");
          eventActorEmail = getJsonString(actorObj, "email", "");
          eventActorAvatarUrl = getJsonString(actorObj, "avatarUrl", "");
        } else if (eventNode.containsKey("author") && !eventNode.isNull("author")) {
          JsonObject authorObj = eventNode.getJsonObject("author");
          eventActorLogin = getJsonString(authorObj, "login", "");
          eventActorEmail = getJsonString(authorObj, "email", "");
          eventActorAvatarUrl = getJsonString(authorObj, "avatarUrl", "");
        }


        ResourceState newState = ResourceState.UNCHANGED; // Default to unchanged and update on transition only

        // Special handling for PullRequestCommit
        if ("PullRequestCommit".equals(type)) {
          JsonObject commitNode = eventNode.getJsonObject("commit");
          timestamp = commitNode.getString("authoredDate", "");
          if (commitNode.containsKey("author") && !commitNode.isNull("author")) {
            JsonObject commitAuthor = commitNode.getJsonObject("author");
            eventActorEmail = getJsonString(commitAuthor, "email", "");
            if (commitAuthor.containsKey("user") && !commitAuthor.isNull("user")) {
              JsonObject userObj = commitAuthor.getJsonObject("user");
              eventActorLogin = getJsonString(userObj, "login", "");
              eventActorAvatarUrl = getJsonString(userObj, "avatarUrl", "");
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

        ContributorData eventActor = ContributorData.newBuilder()
            .setLandscapeToken(baseActor.getLandscapeToken())
            .setRepositoryName(baseActor.getRepositoryName())
            .setGithubLogin(eventActorLogin)
            .setGitUsername(eventActorLogin)
            .setAvatarUrl(eventActorAvatarUrl)
            .setEmail(eventActorEmail)
            .build();

        Optional<Timestamp> parsedTimestamp = parseTimestamp(timestamp);
        if (parsedTimestamp.isEmpty()) {
          LOGGER.warn("Skipping {} event for id={}: missing or invalid timestamp", annotationType, id);
        } else {
          events.add(baseBuilder.clone()
              .setAnnotationType(annotationType)
              .setAnnotationId(id + "-" + type + "-" + i)
              .setEventTimestamp(parsedTimestamp.get())
              .setActor(eventActor)
              .setNewState(newState)
              .build());
        }
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
                    arg("first", PAGE_SIZE),
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
                        field("labels", args(arg("first", NUM_LABELS)),
                            field("nodes", field("name"))
                        ),
                        field("timelineItems", args(arg("first", NUM_TIMELINE_ITEMS)),
                            field("nodes",
                                field("__typename"),
                                on("ClosedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("ReopenedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("IssueComment", field("createdAt"),
                                    field("author", field("login"), field("avatarUrl"),
                                        on("User", field("email"))))
                            )
                        )
                    ),
                    on("PullRequest",
                        field("commits", args(arg("first", NUM_COMMITS)),
                            field("nodes", field("commit", field("oid")))
                            ),
                        field("closingIssuesReferences", args(arg("first", NUM_ISSUE_REFERENCES)),
                            field("nodes", field("number"))),
                        field("mergeCommit", field("oid")),
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
                        field("labels", args(arg("first", NUM_LABELS)),
                            field("nodes", field("name"))
                        ),
                        field("timelineItems", args(arg("first", NUM_TIMELINE_ITEMS)),
                            field("nodes",
                                field("__typename"),
                                on("ClosedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("MergedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("ReopenedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("IssueComment", field("createdAt"),
                                    field("author", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("HeadRefForcePushedEvent", field("createdAt"),
                                    field("actor", field("login"), field("avatarUrl"),
                                        on("User", field("email")))),
                                on("PullRequestCommit",
                                    field("commit",
                                        field("authoredDate"),
                                        field("author",
                                            field("email"),
                                            field("user", field("login"), field("avatarUrl"))
                                        )
                                    )
                                ),
                                on("PullRequestReview", field("createdAt"),
                                    field("author", field("login"), field("avatarUrl"),
                                        on("User", field("email"))))
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

  public Optional<Timestamp> parseTimestamp(String isoTimestamp) {
    if (isoTimestamp == null || isoTimestamp.isBlank()) {
      return Optional.empty();
    }

    try {
      Instant instant = Instant.parse(isoTimestamp);
      return Optional.of(Timestamp.newBuilder()
          .setSeconds(instant.getEpochSecond())
          .setNanos(instant.getNano())
          .build());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
