package net.explorviz.code.analysis.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;

/**
 * A DataExporter handles the export of {@link FileData}, {@link CommitData} and
 * request of {@link StateData}.
 */
public interface DataExporter {

  /**
   * Number of files collected before a single {@code PersistFiles} streaming call
   * is made.
   */
  int FILE_PERSIST_BATCH_SIZE = 50;

  StateData getStateData(final String repositoryName, final String branchName, final String token,
      final Map<String, String> applicationPaths, final String repositoryUrl,
      final boolean skipLatestCommitLookup);

  void persistFile(final FileData fileData);

  /**
   * Persists a batch of files in a single operation. The default implementation
   * calls {@link #persistFile} for each file sequentially; exporters that support
   * client-streaming gRPC should override this to use {@code PersistFiles}.
   */
  default void persistFilesBatch(final List<FileData> files) {
    files.forEach(this::persistFile);
  }

  /**
   * Drains {@code completedFiles} as files arrive and dispatches them to
   * {@link #persistFilesBatch} in chunks of {@link #FILE_PERSIST_BATCH_SIZE}.
   * Runs
   * concurrently with the analysis pipeline; call
   * {@code analysisFinished.countDown()}
   * once all producers have finished to signal the final flush.
   */
  default void persistFilesFromQueueInBatches(final BlockingQueue<FileData> completedFiles,
      final CountDownLatch analysisFinished) {
    final List<FileData> batch = new ArrayList<>(FILE_PERSIST_BATCH_SIZE);
    try {
      while (analysisFinished.getCount() > 0 || !completedFiles.isEmpty()) {
        final FileData fileData = completedFiles.poll(100, TimeUnit.MILLISECONDS);
        if (fileData == null) {
          continue;
        }
        batch.add(fileData);
        if (batch.size() >= FILE_PERSIST_BATCH_SIZE) {
          persistFilesBatch(List.copyOf(batch));
          batch.clear();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!batch.isEmpty()) {
      persistFilesBatch(List.copyOf(batch));
    }
  }

  /**
   * Persists files as they become available on the queue, while analysis is still
   * running. Uses {@link Runtime#availableProcessors()} minus one when
   * parallelism
   * is not positive.
   */
  default void persistFilesFromQueue(final BlockingQueue<FileData> completedFiles,
      final CountDownLatch analysisFinished) {
    final int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    persistFilesFromQueue(completedFiles, analysisFinished, parallelism);
  }

  /**
   * Persists files as they become available on the queue, while analysis is still
   * running, using up to {@code parallelism} concurrent persist operations.
   */
  default void persistFilesFromQueue(final BlockingQueue<FileData> completedFiles,
      final CountDownLatch analysisFinished, final int parallelism) {
    final int effectiveParallelism = Math.max(1, parallelism);
    final Semaphore inFlightPermits = new Semaphore(effectiveParallelism);
    try {
      while (analysisFinished.getCount() > 0 || !completedFiles.isEmpty()
          || inFlightPermits.availablePermits() < effectiveParallelism) {
        inFlightPermits.acquire();
        final FileData fileData;
        try {
          fileData = completedFiles.poll(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          inFlightPermits.release();
          Thread.currentThread().interrupt();
          return;
        }
        if (fileData == null) {
          inFlightPermits.release();
          continue;
        }
        Thread.ofVirtual().start(() -> {
          try {
            persistFile(fileData);
          } finally {
            inFlightPermits.release();
          }
        });
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  void persistCommit(final CommitData commitData);

  void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent);

  boolean isRemote();

  boolean isInvalidCommitHash(final String hash);
}
