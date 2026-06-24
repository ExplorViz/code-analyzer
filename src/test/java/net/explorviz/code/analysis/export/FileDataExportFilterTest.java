package net.explorviz.code.analysis.export;

import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FileDataExportFilterTest {

  @Test
  void includesDataStructuresWhenEnabled() {
    final FileData fileData = sampleFileData();

    final FileData filtered = FileDataExportFilter.filter(fileData, true);

    Assertions.assertSame(fileData, filtered);
  }

  @Test
  void stripsClassesAndFunctionsWhenDisabled() {
    final FileData fileData = sampleFileData();

    final FileData filtered = FileDataExportFilter.filter(fileData, false);

    Assertions.assertEquals("src/Main.java", filtered.getFilePath());
    Assertions.assertEquals(42.0, filtered.getMetricsMap().get("loc"));
    Assertions.assertEquals(0, filtered.getClassesCount());
    Assertions.assertEquals(0, filtered.getFunctionsCount());
  }

  private static FileData sampleFileData() {
    return FileData.newBuilder()
        .setFilePath("src/Main.java")
        .putMetrics("loc", 42.0)
        .addClasses(ClassData.newBuilder().setName("Main").build())
        .addFunctions(FunctionData.newBuilder()
            .setName("run")
            .addParameters(ParameterData.newBuilder().setName("args").setType("String[]").build())
            .build())
        .build();
  }
}
