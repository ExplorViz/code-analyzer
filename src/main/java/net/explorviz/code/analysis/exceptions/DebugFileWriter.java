package net.explorviz.code.analysis.exceptions;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.YamlPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple Class to write failing file content to files. */
public final class DebugFileWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(DebugFileWriter.class);

  private DebugFileWriter() {}

  /**
   * Saves the given content to a file.
   *
   * @param directoryPath the path where the file should be created
   * @param content the content that should be written
   * @param filename the name of the file
   */
  public static void saveDebugFile(
      final String directoryPath, final String content, final String filename) {
    try {
      final Path path = Paths.get(directoryPath);
      if (!Files.exists(path)) {
        Files.createDirectories(path);
      }
      Files.write(Paths.get(directoryPath, filename), content.getBytes());
    } catch (IOException e) {
      handleError(e);
    }
  }

  /**
   * Saves the given compilationUnit as a YAML represented file.
   *
   * @param compilationUnit the Abstract syntax tree
   * @param path the storage path
   */
  public static void saveAstAsYaml(final CompilationUnit compilationUnit, final String path) {
    final YamlPrinter printer = new YamlPrinter(true);
    try {
      Files.write(Paths.get(path), printer.output(compilationUnit).getBytes());
    } catch (IOException e) {
      handleError(e);
    }
  }

  private static void handleError(IOException e) {
    if (LOGGER.isErrorEnabled()) {
      // Log error (other than that we ignore IO errors)
      LOGGER.error("Unable to write the content to file: {}", e.getMessage());
    }
  }
}
