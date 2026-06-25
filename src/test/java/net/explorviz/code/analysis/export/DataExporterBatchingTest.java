package net.explorviz.code.analysis.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.TrackableResourceEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DataExporterBatchingTest {

  @Test
  void smallCommitProducesSingleBatchDespiteHighSendConcurrency() throws InterruptedException {
    final RecordingExporter exporter = new RecordingExporter();
    final BlockingQueue<FileData> completedFiles = new LinkedBlockingQueue<>();
    final CountDownLatch analysisFinished = new CountDownLatch(1);

    final Thread batcher = Thread.ofVirtual().start(() -> exporter.persistFilesFromQueueInBatches(
        completedFiles, analysisFinished, 500, 8));

    for (int i = 0; i < 5; i++) {
      completedFiles.offer(FileData.getDefaultInstance());
    }
    analysisFinished.countDown();
    batcher.join();

    Assertions.assertEquals(List.of(5), exporter.batchSizes);
  }

  @Test
  void drainsQueuedFilesAsSoonAsPersistThreadStarts() throws InterruptedException {
    final RecordingExporter exporter = new RecordingExporter();
    final BlockingQueue<FileData> completedFiles = new LinkedBlockingQueue<>();
    final CountDownLatch analysisFinished = new CountDownLatch(1);

    completedFiles.offer(FileData.getDefaultInstance());

    final Thread batcher = Thread.ofVirtual().start(() -> exporter.persistFilesFromQueueInBatches(
        completedFiles, analysisFinished, 500, 8));

    analysisFinished.countDown();
    batcher.join();

    Assertions.assertEquals(List.of(1), exporter.batchSizes);
  }

  @Test
  void largeCommitUsesFullBatchesAndMinimalRemainder() throws InterruptedException {
    final RecordingExporter exporter = new RecordingExporter();
    final BlockingQueue<FileData> completedFiles = new LinkedBlockingQueue<>();
    final CountDownLatch analysisFinished = new CountDownLatch(1);

    final Thread batcher = Thread.ofVirtual().start(() -> exporter.persistFilesFromQueueInBatches(
        completedFiles, analysisFinished, 500, 8));

    for (int i = 0; i < 1200; i++) {
      completedFiles.offer(FileData.getDefaultInstance());
    }
    analysisFinished.countDown();
    batcher.join();

    Assertions.assertEquals(List.of(500, 500, 200), exporter.batchSizes);
  }

  private static final class RecordingExporter implements DataExporter {

    private final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());

    @Override
    public StateData getStateData(final String repositoryName, final String branchName,
        final String token, final Map<String, String> applicationPaths,
        final String repositoryUrl, final boolean skipLatestCommitLookup) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void persistFile(final FileData fileData) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void persistFilesBatch(final List<FileData> files) {
      batchSizes.add(files.size());
    }

    @Override
    public void persistCommit(final CommitData commitData) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isRemote() {
      return false;
    }

    @Override
    public boolean isInvalidCommitHash(final String hash) {
      return false;
    }
  }
}
