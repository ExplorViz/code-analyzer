package net.explorviz.code.analysis.service;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GithubPager {

  private static final Logger LOGGER = LoggerFactory.getLogger(GithubPager.class);
  private static final int MIN_REMAINING = 50;
  private static final int RETRY_ATTEMPTS = 5;
  private static final int WAIT_MS = 2000;

  private final DynamicGraphQLClient client;
  private final Document query;
  private final Map<String, Object> variables;
  private final String resourceType;

  private String cursor;
  private boolean hasNextPage = true;
  private int totalCount = -1;
  private int seen;
  private boolean failed;
  private int lastRateLimit;
  private long lastQueryNanos;

  GithubPager(
      final DynamicGraphQLClient client,
      final Document query,
      final Map<String, Object> variables,
      final String resourceType) {
    this.client = client;
    this.query = query;
    this.variables = variables;
    this.resourceType = resourceType;
  }

  public Optional<JsonArray> nextPage() {
    if (!hasNextPage) {
      return Optional.empty();
    }

    final Map<String, Object> vars = new HashMap<>(variables);
    vars.put("cursor", cursor);

    long delay = WAIT_MS;

    for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
      try {
        long t0 = System.nanoTime();
        final Response response = client.executeSync(query, vars);
        lastQueryNanos = System.nanoTime() - t0;
        if (!response.hasError()) {
          final JsonObject data =
              response.getData().getJsonObject("repository").getJsonObject(resourceType);
          if (totalCount < 0) {
            totalCount = data.getInt("totalCount", -1);
          }

          checkRateLimit(response.getData());
          final JsonObject pageInfo = data.getJsonObject("pageInfo");
          hasNextPage = pageInfo.getBoolean("hasNextPage", false);
          cursor =
              hasNextPage && !pageInfo.isNull("endCursor") ? pageInfo.getString("endCursor") : null;
          final JsonArray nodes = data.getJsonArray("nodes");
          seen += nodes.size();
          return Optional.of(nodes);
        }
        LOGGER.warn(
            "attempt {} of {} returned errors: {}", attempt, RETRY_ATTEMPTS, response.getErrors());

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        LOGGER.error(
            "attempt {}/{} failed at cursor {}: {}",
            attempt,
            RETRY_ATTEMPTS,
            cursor,
            e.getMessage());
      }
      if (attempt < RETRY_ATTEMPTS) {
        try {
          Thread.sleep(delay);
          delay *= 2;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
    failed = true;
    hasNextPage = false;
    return Optional.empty();
  }

  private void checkRateLimit(final JsonObject data) {
    if (!data.containsKey("rateLimit")) {
      return;
    }
    final JsonObject rateLimit = data.getJsonObject("rateLimit");
    final int remaining = rateLimit.getInt("remaining");
    lastRateLimit = remaining;
    if (remaining >= MIN_REMAINING) {
      return;
    }
    final Instant resetAt = Instant.parse(rateLimit.getString("resetAt"));
    final long waitMs = Math.max(0, Duration.between(Instant.now(), resetAt).toMillis());
    try {
      Thread.sleep(waitMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      hasNextPage = false;
      failed = true;
    }
  }

  public int getTotalCount() {
    return totalCount;
  }

  public int getSeen() {
    return seen;
  }

  public boolean isFailed() {
    return failed;
  }

  public int getLastRateLimit() {
    return lastRateLimit;
  }

  public long getLastQueryNanos() {
    return lastQueryNanos;
  }
}
