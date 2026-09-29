package net.explorviz.code.analysis.regression;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Assertions;

/** Shared helpers for golden-file parser regression tests. */
final class GoldenFileRegressionSupport {

  static final String FILE_HASH = "regression-hash";

  private GoldenFileRegressionSupport() {}

  static String fixturePath(final String languageDir, final String fileName) {
    return "src/test/resources/regression/" + languageDir + "/" + fileName;
  }

  static String expectedResourcePath(final String languageDir, final String fileName) {
    return "/regression/" + languageDir + "/" + expectedFileName(fileName);
  }

  static String expectedFilesystemPath(final String languageDir, final String fileName) {
    return fixturePath(languageDir, expectedFileName(fileName));
  }

  static String expectedFileName(final String sourceFileName) {
    final int dot = sourceFileName.lastIndexOf('.');
    final String base = dot > 0 ? sourceFileName.substring(0, dot) : sourceFileName;
    final String extension = dot > 0 ? sourceFileName.substring(dot + 1) : "";
    if (extension.isEmpty()) {
      return base + ".expected.json";
    }
    // Distinguish .js/.ts/.jsx/.tsx (and similar) that share a basename.
    return base + "." + extension + ".expected.json";
  }

  static String readFixture(final String languageDir, final String fileName) throws IOException {
    final String resource = "/regression/" + languageDir + "/" + fileName;
    try (InputStream in = GoldenFileRegressionSupport.class.getResourceAsStream(resource)) {
      Assertions.assertNotNull(in, "Missing regression fixture resource: " + resource);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  static String toJson(final Message message) throws IOException {
    return unescapeHtml(JsonFormat.printer().print(message));
  }

  static FileData parseFileDataJson(final String json) throws IOException {
    final FileData.Builder builder = FileData.newBuilder();
    JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    return builder.build();
  }

  static String readExpectedJson(
      final Class<?> owner, final String languageDir, final String fileName) throws IOException {
    final String resource = expectedResourcePath(languageDir, fileName);
    try (InputStream in = owner.getResourceAsStream(resource)) {
      Assertions.assertNotNull(in, "Missing expected JSON resource: " + resource);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
    }
  }

  static void assertMatchesGolden(
      final Class<?> owner, final String languageDir, final String fileName, final FileData actual)
      throws IOException {
    final String actualJson = toJson(actual);

    if (Boolean.getBoolean("updateGoldenFiles")) {
      Files.writeString(
          Path.of(expectedFilesystemPath(languageDir, fileName)),
          actualJson + "\n",
          StandardCharsets.UTF_8);
      return;
    }

    final String expectedJson = readExpectedJson(owner, languageDir, fileName);
    final FileData expected = parseFileDataJson(expectedJson);

    Assertions.assertEquals(
        expected,
        actual,
        () ->
            "Analyzer output diverged from golden file for "
                + languageDir
                + "/"
                + fileName
                + ".\n"
                + "If the change is intentional, regenerate with -DupdateGoldenFiles=true\n"
                + "--- expected ---\n"
                + expectedJson
                + "\n"
                + "--- actual ---\n"
                + actualJson);
  }

  private static String unescapeHtml(final String json) {
    return json.replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u0026", "&")
        .replace("\\u003d", "=")
        .replace("\\u0027", "'");
  }
}
