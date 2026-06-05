package net.explorviz.code.analysis.handler;

import net.explorviz.code.analysis.FileLanguageResolver;
import net.explorviz.code.analysis.git.GitMetricCollector;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.proto.Language;

/**
 * Creates file data handlers with basic metrics when full parsing is not possible.
 */
public final class FallbackFileDataHandlerFactory {

  private FallbackFileDataHandlerFactory() {}

  /**
   * Creates a fallback handler with the given language and basic metrics from file content.
   */
  public static TextFileDataHandler create(final FileDescriptor file, final String fileContent,
      final Language language) {
    final TextFileDataHandler handler = new TextFileDataHandler(file.reportedPath, language);
    handler.setFileHash(file.objectId.getName());
    if (fileContent != null) {
      handler.calculateMetrics(fileContent);
    }
    GitMetricCollector.addFileGitMetrics(handler, file);
    return handler;
  }

  /**
   * Creates a fallback handler, inferring the language from the file name or path.
   */
  public static TextFileDataHandler create(final FileDescriptor file, final String fileContent) {
    return create(file, fileContent,
        FileLanguageResolver.resolveFromFileName(file.fileName, file.reportedPath));
  }
}
