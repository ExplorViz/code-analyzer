package net.explorviz.code.analysis;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.explorviz.code.analysis.export.JsonExporter;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test to demonstrate ANTLR parsing and JSON export.
 */
@QuarkusTest
public class AntlrJsonExportTest {

  @Inject
  AntlrParserService antlrParserService;

  @Test
  void testAntlrParseAndExportToJson() throws IOException {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ANTLR JSON Export Test");
    System.out.println("=".repeat(80));

    // Create output directory
    final String outputDir = "test-json-output";
    Files.createDirectories(Paths.get(outputDir));

    // Parse a test file using ANTLR
    System.out.println("\n1. Reading test file...");
    final String testFile = "src/test/resources/files/Happy.java";
    final String fileContent = Files.readString(Path.of(testFile));
    System.out.println("   File: " + testFile);
    System.out.println("   Size: " + fileContent.length() + " bytes");

    // Parse with ANTLR
    System.out.println("\n2. Parsing with ANTLR...");
    final JavaFileDataHandler fileDataHandler = antlrParserService.parseFileContent(
        fileContent, "Happy.java", "test-commit-sha1");

    Assertions.assertNotNull(fileDataHandler, "JavaFileDataHandler should not be null");

    // Convert to protobuf
    System.out.println("\n3. Converting to protobuf...");
    final FileData fileData = fileDataHandler.getProtoBufObject();

    // Print some extracted data
    System.out.println("\n4. Extracted Data:");
    System.out.println("   Package: " + fileData.getPackageName());
    System.out.println("   Imports: " + fileData.getImportNamesCount());
    System.out.println("   Classes: " + fileData.getClassesCount());
    System.out.println("   lineCount: " + fileData.getMetricsOrDefault("lineCount", 0.0));
    System.out.println("   CLOC: " + fileData.getMetricsOrDefault("cloc", 0.0));

    if (fileData.getClassesCount() > 0) {
      fileData.getClassesList().forEach(classData -> {
        System.out.println("\n   Class: " + classData.getName());
        System.out.println("   - Type: " + classData.getType());
        System.out.println("   - Methods: " + classData.getFunctionsCount());
        System.out.println("   - Fields: " + classData.getFieldsCount());
        System.out.println("   - Modifiers: " + classData.getModifiersList());
      });
    }

    // Export to JSON
    System.out.println("\n5. Exporting to JSON...");
    final JsonExporter jsonExporter = new JsonExporter(Paths.get(outputDir));
    jsonExporter.persistFile(fileData);

    final String expectedFileName = "Happy_test-commit-sha1.json";
    final Path jsonFilePath = Paths.get(outputDir, expectedFileName);

    Assertions.assertTrue(Files.exists(jsonFilePath),
        "JSON file should be created at: " + jsonFilePath);

    // Read and display the JSON
    final String jsonContent = Files.readString(jsonFilePath);
    System.out.println("   Output file: " + jsonFilePath);
    System.out.println("   File size: " + jsonContent.length() + " bytes");

    System.out.println("\n6. JSON Content (first 1500 chars):");
    System.out.println("-".repeat(80));
    System.out.println(jsonContent.substring(0, Math.min(1500, jsonContent.length())));
    if (jsonContent.length() > 1500) {
      System.out.println("\n   ... (truncated, total " + jsonContent.length() + " bytes)");
    }
    System.out.println("-".repeat(80));

    System.out.println("\n" + "=".repeat(80));
    System.out.println("✅ Test completed successfully!");
    System.out.println("Full JSON file available at: " + jsonFilePath.toAbsolutePath());
    System.out.println("=".repeat(80) + "\n");

    // Assertions
    Assertions.assertTrue(jsonContent.contains("\"packageName\":"));
    Assertions.assertTrue(jsonContent.contains("com.easy.life"));
    Assertions.assertTrue(jsonContent.length() > 100, "JSON should have substantial content");
  }

  @Test
  void testAntlrParseMultipleFiles() throws IOException {
    System.out.println("\n" + "=".repeat(80));
    System.out.println("ANTLR Multiple Files Test");
    System.out.println("=".repeat(80));

    final String outputDir = "test-json-output-multi";
    Files.createDirectories(Paths.get(outputDir));
    final JsonExporter jsonExporter = new JsonExporter(Paths.get(outputDir));

    final String[] testFiles = {
        "src/test/resources/files/Happy.java",
        "src/test/resources/files/Nested.java",
        "src/test/resources/files/ColorParam.java"
    };

    int fileCount = 0;
    for (final String testFile : testFiles) {
      System.out.println("\nProcessing: " + testFile);

      final String fileContent = Files.readString(Path.of(testFile));
      final String fileName = Paths.get(testFile).getFileName().toString();

      final JavaFileDataHandler handler = antlrParserService.parseFileContent(
          fileContent, fileName, "commit-" + fileCount);

      if (handler != null) {
        final FileData fileData = handler.getProtoBufObject();
        jsonExporter.persistFile(fileData);

        System.out.println("  ✅ Parsed: " + fileData.getPackageName() +
            " (" + fileData.getClassesCount() + " classes)");
        fileCount++;
      }
    }

    System.out.println("\n" + "=".repeat(80));
    System.out.println("✅ Processed " + fileCount + " files");
    System.out.println("JSON files in: " + Paths.get(outputDir).toAbsolutePath());
    System.out.println("=".repeat(80) + "\n");

    Assertions.assertEquals(testFiles.length, fileCount,
        "All files should be processed");
  }
}
