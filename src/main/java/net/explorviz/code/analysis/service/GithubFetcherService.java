package net.explorviz.code.analysis.service;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Argument.args;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Enum.gqlEnum;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.InlineFragment.on;
import static io.smallrye.graphql.client.core.InputObject.inputObject;
import static io.smallrye.graphql.client.core.InputObjectField.prop;
import static io.smallrye.graphql.client.core.Operation.operation;
import static io.smallrye.graphql.client.core.Variable.var;
import static io.smallrye.graphql.client.core.Variable.vars;
import static io.smallrye.graphql.client.core.VariableType.nonNull;

import com.google.protobuf.Timestamp;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.Variable;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
  private static final int PAGE_SIZE = 25;
  private static final int NUM_LABELS = 10;
  private static final int NUM_TIMELINE_ITEMS = 25;
  private static final int NUM_COMMITS = 25;
  private static final int NUM_ISSUE_REFERENCES = 20;

  private long tmapTotal = 0;
  private long tpersistTotal = 0;
  private long tqueryTotal = 0;
  private long numUpdates = 0;
  private long eventsTotal = 0;


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

    final Optional<String> repoSubString = extractGithubRepoSubString(config.repoRemoteUrl().get());
    if (repoSubString.isEmpty()) {
      return Optional.empty();
    }


    tmapTotal = 0;
    tpersistTotal = 0;
    tqueryTotal = 0;
    numUpdates = 0;
    eventsTotal = 0;

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
    try (DynamicGraphQLClient githubClient = DynamicGraphQLClientBuilder.newBuilder()
        .url(GITHUB_URL)
        .header("Authorization", "Bearer " + githubToken)
        .build()) {
      final long tStart = System.nanoTime();
      validateToken(githubClient);

      fetchData(exporter, githubClient, landscapeToken, repoOwnerAndName, "issues", startDate, endDate);
      fetchData(exporter, githubClient, landscapeToken, repoOwnerAndName, "pullRequests", startDate, endDate);

      LOGGER.info("✅ Completed all social fetch queries.");

      long tqueryAvg = tqueryTotal / numUpdates;
      long tmapAvg =  tmapTotal / numUpdates;
      long tpersistAvg = tpersistTotal / numUpdates;
      long eventsAvg = eventsTotal / numUpdates;

      LOGGER.info(
          "tQuery avg: {}ms, tMap avg: {}ms, tPersist avg: {}ms, {} events, social analysis took {} seconds with PAGE_SIZE: {}",
          TimeUnit.NANOSECONDS.toMillis(tqueryAvg),
          TimeUnit.NANOSECONDS.toMillis(tmapAvg),
          TimeUnit.NANOSECONDS.toMillis(tpersistAvg),
          eventsAvg,
          String.format(Locale.ROOT, "%.2f", (System.nanoTime() - tStart) / 1_000_000_000.0),
          PAGE_SIZE);

    } catch (final Exception e) {
      if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.error("Could not fetch social data from GitHub: {}", e.getMessage());
    }
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

  @SuppressWarnings("try")
  private void validateToken(DynamicGraphQLClient client) {
    Document viewerQuery = document(
        operation(
            field("viewer",
                field("login")
            )
        )
    );
    try {
      client.executeSync(viewerQuery);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid GitHub token, aborting GitHub Data fetching: " + e.getMessage(), e);
    }
  }

  private void fetchData(final DataExporter exporter, final DynamicGraphQLClient client, final String landscapeToken,
      final String repoOwnerName, final String resourceType, final Date startDate, final Date endDate) {
    final String[] splits = repoOwnerName.split("/");
    final GithubPager pager = new GithubPager(
        client, buildQuery(resourceType), Map.of("owner", splits[0], "name", splits[1]), resourceType);

    final Instant start =  startDate.toInstant();
    final Instant end = endDate.toInstant();
    Instant currentDate = end;

    boolean startFound = false;
    Optional<JsonArray> page;
    while (!startFound && (page = pager.nextPage()).isPresent()) {
      final JsonArray nodes = page.get();
      final List<TrackableResourceEvent> events = new ArrayList<>();
      long t1 = System.nanoTime();
      for (int i = 0; i < nodes.size(); i++) {
        final JsonObject node = (JsonObject) nodes.get(i);

        Optional<Timestamp> date = parseTimestamp(node.getString("updatedAt"));
        if (date.isEmpty()) {
          LOGGER.warn("Could not parse date for node {}.", node.getInt("number", -1));
        } else {
          final Instant nodeDate =  Instant.ofEpochSecond(date.get().getSeconds(), date.get().getNanos());
          if (nodeDate.isAfter(end)) {
            continue;
          }
          if (nodeDate.isBefore(start)) {
            startFound = true;
            break;
          }
          currentDate = nodeDate;
          events.addAll(mapToEvents(node, landscapeToken, repoOwnerName));
        }
      }
      long tmap = System.nanoTime() - t1;
      long t2 = System.nanoTime();
      exporter.persistTrackableResourceEventBatch(events);
      long tpersist = System.nanoTime() - t2;
      logProgress(resourceType, pager, currentDate, start, end, events.size(), tmap, tpersist);
    }
    if (pager.isFailed()) {
      LOGGER.error("{}: Fetch failed after {}/{} nodes", resourceType, pager.getSeen(), pager.getTotalCount());
    }
  }

  private void logProgress(
      final String resourceType, final GithubPager pager, final Instant currentTime,
      final Instant startTime, final Instant endTime, final int batchSize, long tmap, long tpersist) {
    final double windowMs = Math.max(1, Duration.between(startTime, endTime).toMillis());
    final double estimatePercent =
        Math.clamp(100 * Duration.between(currentTime, endTime).toMillis() / windowMs, 0, 100);
    LOGGER.info(
        "{}: {}% of time window (at {}), {} nodes processed, sending {} events. Rate limit: {}",
        resourceType, String.format("%.1f", estimatePercent), currentTime,
        pager.getSeen(), batchSize, pager.getLastRateLimit());
    tmapTotal += tmap;
    tpersistTotal += tpersist;
    tqueryTotal += pager.getLastQueryNanos();
    eventsTotal += batchSize;
    numUpdates += 1;

    LOGGER.debug(
        "tQuery: {}ms, tMap: {}ms, tPersist: {}",
        TimeUnit.NANOSECONDS.toMillis(pager.getLastQueryNanos()),
        TimeUnit.NANOSECONDS.toMillis(tmap),
        TimeUnit.NANOSECONDS.toMillis(tpersist));
  }

  private Document buildQuery(final String resourceType) {
    final Variable owner = var("owner", nonNull("String"));
    final Variable name = var("name", nonNull("String"));
    final Variable cursor = var("cursor", "String");

    return document(operation(
        vars(owner, name, cursor),
        field("repository",
            args(arg("owner", owner), arg("name", name)),
            field(resourceType,
                args(
                    arg("first", PAGE_SIZE),
                    arg("after", cursor),
                    arg("orderBy", inputObject(
                        prop("field", gqlEnum("UPDATED_AT")),
                        prop("direction", gqlEnum("DESC"))))),
                field("totalCount"),
                field("pageInfo",
                    field("hasNextPage"),
                    field("endCursor")),
                field("nodes",
                    field("__typename"),
                    field("updatedAt"),
                    "issues".equals(resourceType)
                        ? on("Issue",
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
                    )
                        : on("PullRequest",
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
            )
        ),
        field("rateLimit", field("cost"), field("remaining"), field("resetAt"))));
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

    final Set<Integer> referencedIssues = new HashSet<>(closingIssuesReferences);
    if (resourceType == TrackableResourceType.PULL_REQUEST) {
      final Matcher issueNumber = Pattern.compile("#(\\d+)").matcher(description);
      while (issueNumber.find()) {
        try {
          final int num = Integer.parseInt(issueNumber.group(1));
          referencedIssues.add(num);
        } catch (NumberFormatException e) {
          LOGGER.warn("failed parsing issue number " + issueNumber.group());
        }
      }
    }

    ContributorData actor = ContributorData.newBuilder()
        .setLandscapeToken(landscapeToken)
        .setRepositoryName(repoName)
        .setEmail(authorEmail)
        .setGithubLogin(authorLogin)
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
        .addAllReferencedIssueNumbers(referencedIssues);
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
