package net.explorviz.code.analysis.export;

import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import net.explorviz.code.proto.CommitData;
import net.explorviz.code.proto.ContributorData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.StateData;
import net.explorviz.code.proto.StateDataRequest;
import net.explorviz.code.proto.TrackableResourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports the data into files in JSON format.
 */
public class JsonExporter implements DataExporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonExporter.class);
  private static final String JSON_FILE_EXTENSION = ".json";
  private static final String[] SOURCE_FILE_EXTENSIONS = {
      ".java", ".ts", ".tsx", ".js", ".jsx", ".py",
      ".c", ".cpp", ".cxx", ".cc", ".h", ".hpp", ".hxx"
  };

  private final String storageDirectory;
  private int commitCount;

  /**
   * Creates a JSON exporter that exports the data into folder based on the
   * current working folder and the given
   * repository and application name.
   *
   * @param repositoryName  the name of the repository
   * @param applicationName the name of the application
   * @throws IOException gets thrown if the needed directories were not created.
   */
  public JsonExporter(final String repositoryName, final String applicationName) throws IOException {
    String systemPath = System.getProperty("user.dir");
    systemPath = systemPath.replace("\\build\\classes\\java\\main", "");
    systemPath = systemPath.replace("/build/classes/java/main", "");

    if (applicationName == null || applicationName.isBlank()) {
      this.storageDirectory = Paths.get(systemPath, "analysis-data", repositoryName).toString();
    } else {
      this.storageDirectory = Paths.get(systemPath, "analysis-data", repositoryName, applicationName).toString();
    }

    Files.createDirectories(Paths.get(storageDirectory));

    LOGGER.atInfo().addArgument(repositoryName).addArgument(applicationName).addArgument(storageDirectory)
        .log("The analysis-data folder for repository '{}' and application '{}' is created here: {}");

    this.commitCount = 0;
  }

  /**
   * Creates a JSON exporter that exports the data into folder given.
   *
   * @param pathToStorageDirectory the path to the JSON export folder
   */
  public JsonExporter(final java.nio.file.Path pathToStorageDirectory) {
    this.storageDirectory = pathToStorageDirectory.toString();
    this.commitCount = 0;
  }

  @Override
  public StateData getStateData(final String repositoryName, final String branchName,
      final String token,
      final Map<String, String> applicationPaths) {
    LOGGER.atInfo()
        .addArgument(repositoryName)
        .addArgument(branchName)
        .addArgument(applicationPaths != null ? applicationPaths.keySet() : "[]")
        .log("📥 State data requested for {} on branch {} (applications: {})");

    final StateData stateData = StateData.newBuilder().build();
    try {
      final StateDataRequest.Builder requestBuilder = StateDataRequest.newBuilder()
          .setRepositoryName(repositoryName)
          .setBranchName(branchName)
          .setLandscapeToken(token);
      if (applicationPaths != null) {
        requestBuilder.putAllApplicationPaths(applicationPaths);
      }

      final StateDataRequest request = requestBuilder.build();

      final String resultJson = unescapeHtml(JsonFormat.printer().print(stateData));
      final String requestJson = unescapeHtml(JsonFormat.printer().print(request));

      final String stateFileName;
      final String requestFileName;
      if (applicationPaths == null || applicationPaths.isEmpty()) {
        stateFileName = "StateData" + JSON_FILE_EXTENSION;
        requestFileName = "StateRequest" + JSON_FILE_EXTENSION;
      } else if (applicationPaths.size() == 1) {
        final String onlyName = applicationPaths.keySet().iterator().next();
        stateFileName = "StateData_" + onlyName + JSON_FILE_EXTENSION;
        requestFileName = "StateRequest_" + onlyName + JSON_FILE_EXTENSION;
      } else {
        stateFileName = "StateData_multi" + JSON_FILE_EXTENSION;
        requestFileName = "StateRequest_multi" + JSON_FILE_EXTENSION;
      }

      Files.write(Paths.get(storageDirectory, stateFileName), resultJson.getBytes());
      Files.write(Paths.get(storageDirectory, requestFileName), requestJson.getBytes());

      LOGGER.atInfo()
          .log("✅ Successfully exported state request and result");
    } catch (IOException e) {
      LOGGER.atError()
          .addArgument(e.getMessage())
          .log("❌ Failed to export state data: {}");
    }
    return stateData;
  }

  @Override
  public void persistFile(final FileData fileData) {
    try {
      LOGGER.atInfo()
          .addArgument(fileData.getFilePath())
          .addArgument(fileData.getLanguage())
          .log("📤 Exporting file data: {} (language: {})");

      final String json = unescapeHtml(JsonFormat.printer().print(fileData));

      // Remove file extension from filename
      String filePath = fileData.getFilePath();

      for (final String extension : SOURCE_FILE_EXTENSIONS) {
        if (filePath.endsWith(extension)) {
          filePath = filePath.substring(0, filePath.length() - extension.length());
          break;
        }
      }

      final String fileName = filePath + "_" + fileData.getFileHash() + JSON_FILE_EXTENSION;
      final var outputPath = Paths.get(storageDirectory, fileName);

      // Create parent directories if they don't exist
      // This is necessary because fileName may contain subdirectories
      // (e.g., "src/utils/file.json")
      if (outputPath.getParent() != null) {
        Files.createDirectories(outputPath.getParent());
      }

      Files.write(outputPath, json.getBytes());

      LOGGER.atInfo()
          .addArgument(fileName)
          .log("✅ Successfully exported file data to: {}");
    } catch (IOException e) { // NOPMD
      LOGGER.atError()
          .addArgument(fileData.getFilePath())
          .addArgument(e.getMessage())
          .log("❌ Failed to export file data for {}: {}");
      throw new RuntimeException(e); // NOPMD
    }
  }

  @Override
  public void persistCommit(final CommitData commitData) {
    try {
      final String json = unescapeHtml(JsonFormat.printer().print(commitData));
      final String fileName = "CommitReport_" + commitData.getCommitId() + "_" + commitCount
          + JSON_FILE_EXTENSION;
      Files.write(Paths.get(storageDirectory, fileName), json.getBytes());
    } catch (IOException e) { // NOPMD
      throw new RuntimeException(e); // NOPMD
    }
    this.commitCount++;
  }

  @Override
  public void persistTrackableResourceEvent(final TrackableResourceEvent trackableResourceEvent) {
    try {
      final String json = unescapeHtml(JsonFormat.printer().print(trackableResourceEvent));
      final String fileName = "TrackableResourceEvent" + trackableResourceEvent.getResourceId() + JSON_FILE_EXTENSION;
      Files.write(Paths.get(storageDirectory, fileName), json.getBytes());
    } catch (IOException e) { // NOPMD
      throw new RuntimeException(e); // NOPMD
    }
  }

  @Override
  public boolean isRemote() {
    return false;
  }

  @Override
  public boolean isInvalidCommitHash(final String hash) {
    return false;
  }

  /**
   * Protobuf's JsonFormat uses Gson which HTML-escapes certain characters.
   * Since our JSON is written to files and not embedded in HTML, we undo this.
   */
  private static String unescapeHtml(final String json) {
    return json.replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u0026", "&")
        .replace("\\u003d", "=")
        .replace("\\u0027", "'");
  }
}
