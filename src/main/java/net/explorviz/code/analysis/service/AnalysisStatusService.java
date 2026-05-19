package net.explorviz.code.analysis.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jboss.logging.Logger;

/**
 * In-memory status tracking for analysis jobs.
 */
@ApplicationScoped
public class AnalysisStatusService {

  private static final String STATUS_PENDING = "pending";
  private static final String STATUS_RUNNING = "running";
  private static final String STATUS_FINISHED = "finished";
  private static final String STATUS_FAILED = "failed";
  private static final String UNKNOWN_TOKEN = "unknown";

  private final Map<String, AnalysisProgressState> stateByLandscapeToken = new ConcurrentHashMap<>();
  private final Map<String, Set<SseSubscriber>> subscribersByLandscapeToken = new ConcurrentHashMap<>();

  private record SseSubscriber(SseEventSink sink, Sse sse) {
  }

  public void markPending(final String landscapeToken) {
    upsertStateAndNotify(landscapeToken, current -> emptyState(STATUS_PENDING));
  }

  public void markRunning(final String landscapeToken, final int totalCommits,
      final int totalFiles) {
    upsertStateAndNotify(landscapeToken, current -> {
      final AnalysisProgressState previous = current == null ? emptyState(STATUS_PENDING) : current;
      return new AnalysisProgressState(STATUS_RUNNING, totalCommits, previous.analyzedCommits(),
          totalFiles, previous.analyzedFiles(), previous.currentAnalysingFile());
    });
  }

  public void incrementAnalyzedCommit(final String landscapeToken) {
    updateExistingStateAndNotify(landscapeToken,
        state -> new AnalysisProgressState(state.status(), state.totalCommits(),
            state.analyzedCommits() + 1, state.totalFiles(), state.analyzedFiles(),
            state.currentAnalysingFile()));
  }

  public void setCurrentCommitFiles(final String landscapeToken, final int totalFiles) {
    updateExistingStateAndNotify(landscapeToken,
        state -> new AnalysisProgressState(state.status(), state.totalCommits(),
            state.analyzedCommits(), Math.max(0, totalFiles), 0, null));
  }

  public void setCurrentAnalyzingFile(final String landscapeToken, final String currentAnalysingFile) {
    updateExistingStateAndNotify(landscapeToken,
        state -> new AnalysisProgressState(state.status(), state.totalCommits(),
            state.analyzedCommits(), state.totalFiles(), state.analyzedFiles(),
            currentAnalysingFile));
  }

  public void incrementAnalyzedFile(final String landscapeToken) {
    updateExistingStateAndNotify(landscapeToken,
        state -> new AnalysisProgressState(state.status(), state.totalCommits(),
            state.analyzedCommits(), state.totalFiles(), state.analyzedFiles() + 1,
            state.currentAnalysingFile()));
  }

  public void markFinished(final String landscapeToken) {
    upsertStateAndNotify(landscapeToken, current -> {
      if (current == null) {
        return emptyState(STATUS_FINISHED);
      }
      return new AnalysisProgressState(STATUS_FINISHED, current.totalCommits(),
          current.totalCommits(), current.totalFiles(), current.totalFiles(), null);
    });
  }

  public void markFailed(final String landscapeToken) {
    upsertStateAndNotify(landscapeToken, current -> {
      if (current == null) {
        return emptyState(STATUS_FAILED);
      }
      return new AnalysisProgressState(STATUS_FAILED, current.totalCommits(),
          current.analyzedCommits(), current.totalFiles(), current.analyzedFiles(),
          current.currentAnalysingFile());
    });
  }

  public Optional<String> getStatus(final String landscapeToken) {
    return getState(landscapeToken).map(AnalysisProgressState::status);
  }

  public Optional<AnalysisProgressState> getState(final String landscapeToken) {
    return Optional.ofNullable(stateByLandscapeToken.get(normalizeToken(landscapeToken)));
  }

  public void subscribeToStateUpdates(final String landscapeToken,
      final SseEventSink sink, final Sse sse) {
    final String token = normalizeToken(landscapeToken);
    if (sink == null || sse == null || sink.isClosed()) {
      return;
    }

    final SseSubscriber subscriber = new SseSubscriber(sink, sse);
    subscribersByLandscapeToken
        .computeIfAbsent(token, ignored -> ConcurrentHashMap.newKeySet())
        .add(subscriber);

    final AnalysisProgressState currentState = stateByLandscapeToken.getOrDefault(token, emptyState(STATUS_PENDING));
    sendState(token, subscriber, currentState);
  }

  private void updateExistingStateAndNotify(final String landscapeToken,
      final Function<AnalysisProgressState, AnalysisProgressState> update) {
    final String token = normalizeToken(landscapeToken);
    final AnalysisProgressState updatedState = stateByLandscapeToken.computeIfPresent(token,
        (ignored, current) -> update.apply(current));

    if (updatedState != null) {
      notifySubscribers(token, updatedState);
    }
  }

  private void upsertStateAndNotify(final String landscapeToken,
      final Function<AnalysisProgressState, AnalysisProgressState> update) {
    final String token = normalizeToken(landscapeToken);
    final AnalysisProgressState updatedState = stateByLandscapeToken.compute(token,
        (ignored, current) -> update.apply(current));

    if (updatedState != null) {
      notifySubscribers(token, updatedState);
    }
  }

  private AnalysisProgressState emptyState(final String status) {
    return new AnalysisProgressState(status, 0, 0, 0, 0, null);
  }

  private String normalizeToken(final String landscapeToken) {
    if (landscapeToken == null || landscapeToken.isBlank()) {
      return UNKNOWN_TOKEN;
    }
    return landscapeToken;
  }

  private void notifySubscribers(final String landscapeToken, final AnalysisProgressState state) {
    final Set<SseSubscriber> subscribers = subscribersByLandscapeToken.get(landscapeToken);
    if (subscribers == null || subscribers.isEmpty()) {
      return;
    }

    subscribers.forEach(subscriber -> sendState(landscapeToken, subscriber, state));
  }

  private void sendState(final String landscapeToken, final SseSubscriber subscriber,
      final AnalysisProgressState state) {
    final SseEventSink sink = subscriber.sink();
    if (sink.isClosed()) {
      removeSubscriber(landscapeToken, subscriber);
      return;
    }

    sink.send(subscriber.sse().newEventBuilder()
        .mediaType(MediaType.APPLICATION_JSON_TYPE)
        .data(AnalysisProgressState.class, state)
        .build())
        .whenComplete((ignored, throwable) -> {
          if (throwable != null || sink.isClosed()) {
            removeSubscriber(landscapeToken, subscriber);
          }
        });

    if (STATUS_FINISHED.equals(state.status()) || STATUS_FAILED.equals(state.status())) {
      sink.close();
      removeSubscriber(landscapeToken, subscriber);
    }
  }

  private void removeSubscriber(final String landscapeToken, final SseSubscriber subscriber) {
    final Set<SseSubscriber> subscribers = subscribersByLandscapeToken.get(landscapeToken);
    if (subscribers == null) {
      return;
    }
    subscribers.remove(subscriber);
    if (subscribers.isEmpty()) {
      subscribersByLandscapeToken.remove(landscapeToken);
    }
  }
}
