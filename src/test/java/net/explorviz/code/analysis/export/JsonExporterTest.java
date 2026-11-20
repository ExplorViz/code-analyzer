package net.explorviz.code.analysis.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.explorviz.code.proto.AnnotationType;
import net.explorviz.code.proto.TrackableResourceEvent;
import net.explorviz.code.proto.TrackableResourceType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonExporterTest {

  @TempDir Path storageDirectory;

  @Test
  void eventsOfTheSameResourceDoNotOverwriteEachOther() throws Exception {
    final JsonExporter exporter = new JsonExporter(storageDirectory);

    exporter.persistTrackableResourceEvent(event("I_kwDO/1+x=-CREATE", AnnotationType.CREATE));
    exporter.persistTrackableResourceEvent(event("I_kwDO/1+x=-CLOSE", AnnotationType.CLOSE));
    exporter.persistTrackableResourceEvent(
        event("I_kwDO/1+x=-IssueComment-0", AnnotationType.COMMENT));

    try (Stream<Path> files = Files.list(storageDirectory)) {
      Assertions.assertEquals(
          java.util.List.of(
              "TrackableResourceEvent_ISSUE_42_I_kwDO_1_x_-CLOSE.json",
              "TrackableResourceEvent_ISSUE_42_I_kwDO_1_x_-CREATE.json",
              "TrackableResourceEvent_ISSUE_42_I_kwDO_1_x_-IssueComment-0.json"),
          files.map(path -> path.getFileName().toString()).sorted().toList());
    }
  }

  private static TrackableResourceEvent event(
      final String annotationId, final AnnotationType annotationType) {
    return TrackableResourceEvent.newBuilder()
        .setResourceType(TrackableResourceType.ISSUE)
        .setResourceId("42")
        .setAnnotationId(annotationId)
        .setAnnotationType(annotationType)
        .build();
  }
}
