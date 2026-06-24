package net.explorviz.code.analysis.export;

import net.explorviz.code.proto.FileData;

/**
 * Filters {@link FileData} before persistence based on export options.
 */
public final class FileDataExportFilter {

  private FileDataExportFilter() {}

  /**
   * Returns file data suitable for export. When data structures are excluded,
   * classes and functions (including nested parameters) are stripped while
   * file-level metadata and metrics are retained.
   */
  public static FileData filter(final FileData fileData, final boolean includeDataStructures) {
    if (includeDataStructures) {
      return fileData;
    }
    return fileData.toBuilder()
        .clearClasses()
        .clearFunctions()
        .build();
  }
}
