package net.explorviz.code.analysis.listener;

import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Common methods for file data listeners.
 */
public interface CommonFileDataListener {
  String FILE_SIZE = "size";
  String SLOC = "sloc";
  String LOC = "loc";
  String CLOC = "cloc";
  String FUNCTION_COUNT = "functionCount";
  String VARIABLE_COUNT = "variableCount";
  String IMPORT_COUNT = "importCount";
  String CLASS_COUNT = "classCount";

  default void addImportAndClassCountMetrics(final AbstractFileDataHandler handler) {
    handler.addMetric(IMPORT_COUNT, String.valueOf(handler.getImportCount()));
    handler.addMetric(CLASS_COUNT, String.valueOf(handler.getClassCount()));
  }

  default int calculateLoc(final ParserRuleContext ctx) {
    if (ctx == null || ctx.start == null || ctx.stop == null) {
      return 0;
    }
    return ctx.stop.getLine() - ctx.start.getLine() + 1;
  }

  default int getLoc(final ParserRuleContext ctx) {
    if (ctx == null || ctx.stop == null) {
      return 0;
    }
    if (ctx.start != null) {
      return ctx.stop.getLine() - ctx.start.getLine() + 1;
    }
    return ctx.stop.getLine();
  }

  /**
   * Calculates the Source Lines of Code (SLOC) by counting unique lines that contain tokens on the
   * default channel (channel 0). This excludes comments and whitespace if the grammar follows
   * standard conventions.
   *
   * @param tokens The token stream
   * @return The number of lines containing code tokens
   */
  default int getSloc(final org.antlr.v4.runtime.CommonTokenStream tokens) {
    if (tokens == null) {
      return 0;
    }
    final java.util.Set<Integer> codeLines = new java.util.HashSet<>();
    for (int i = 0; i < tokens.size(); i++) {
      final org.antlr.v4.runtime.Token token = tokens.get(i);
      if (token.getChannel() == 0) {
        final String text = token.getText();
        if (text != null && !text.trim().isEmpty()) {
          codeLines.add(token.getLine());
        }
      }
    }
    return codeLines.size();
  }

  /**
   * Calculates the Source Lines of Code (SLOC) for a specific context by counting unique lines that
   * contain tokens on the default channel (channel 0) within the range of the context.
   *
   * @param ctx    The parser rule context
   * @param tokens The token stream
   * @return The number of lines containing code tokens within the context's range
   */
  default int getSloc(final ParserRuleContext ctx, final org.antlr.v4.runtime.CommonTokenStream tokens) {
    if (ctx == null || ctx.start == null || ctx.stop == null || tokens == null) {
      return 0;
    }
    final int startLine = ctx.start.getLine();
    final int endLine = ctx.stop.getLine();
    final java.util.Set<Integer> codeLines = new java.util.HashSet<>();

    // Find tokens within the line range of the context
    // We can optimize this by starting from ctx.start.getTokenIndex() to ctx.stop.getTokenIndex()
    for (int i = ctx.start.getTokenIndex(); i <= ctx.stop.getTokenIndex(); i++) {
      if (i < 0 || i >= tokens.size()) {
        continue;
      }
      final org.antlr.v4.runtime.Token token = tokens.get(i);
      if (token.getChannel() == 0) {
        final String text = token.getText();
        if (text != null && !text.trim().isEmpty()) {
          final int line = token.getLine();
          if (line >= startLine && line <= endLine) {
            codeLines.add(line);
          }
        }
      }
    }
    return codeLines.size();
  }

  default String getClassPathFromFqn(final String fqn, final String fileExtension,
      final String currentFilePath, final String currentPackage) {
    if (fqn == null || fqn.isEmpty()) {
      return "unknown/file";
    }

    String baseFqn = fqn;

    // Clean up fqn to prepare for path conversion
    final int genericIdxAngle = baseFqn.indexOf('<');
    if (genericIdxAngle != -1) {
      baseFqn = baseFqn.substring(0, genericIdxAngle);
    }
    final int genericIdxSquare = baseFqn.indexOf('[');
    if (genericIdxSquare != -1) {
      baseFqn = baseFqn.substring(0, genericIdxSquare);
    }
    baseFqn = baseFqn.replace("[]", "");

    // Convert fqn to path
    final int lastDot = baseFqn.lastIndexOf('.');
    final String fqnPath;
    if (lastDot != -1) {
      fqnPath = baseFqn.replace('.', '/') + fileExtension;
    } else {
      fqnPath = baseFqn + fileExtension;
    }

    // Use current file path to best guess path prefix of given class
    String pathPrefix = "";
    if (currentFilePath != null && currentPackage != null && !currentPackage.isEmpty()) {
      final String packagePath = currentPackage.replace('.', '/');
      final int packageIdx = currentFilePath.lastIndexOf(packagePath);
      if (packageIdx != -1) {
        pathPrefix = currentFilePath.substring(0, packageIdx);
      }
    } else if (currentFilePath != null && currentFilePath.contains("/")) {
      pathPrefix = currentFilePath.substring(0, currentFilePath.lastIndexOf("/") + 1);
    }

    // Return (best guess) path for given fqn
    return pathPrefix + fqnPath;
  }

  default String getClassNameFromFqn(final String fqn) {
    if (fqn == null || fqn.isEmpty()) {
      return "unknown";
    }

    String baseFqn = fqn;
    final int genericIdxAngle = baseFqn.indexOf('<');
    if (genericIdxAngle != -1) {
      baseFqn = baseFqn.substring(0, genericIdxAngle);
    }
    final int genericIdxSquare = baseFqn.indexOf('[');
    if (genericIdxSquare != -1) {
      baseFqn = baseFqn.substring(0, genericIdxSquare);
    }

    final int lastDot = baseFqn.lastIndexOf('.');
    if (lastDot != -1) {
      return baseFqn.substring(lastDot + 1);
    }

    return baseFqn;
  }
}
