package net.explorviz.code.analysis.metrics;

import net.explorviz.code.analysis.handler.AbstractFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrCParserService;
import net.explorviz.code.analysis.parser.AntlrCSharpParserService;
import net.explorviz.code.analysis.parser.AntlrCppParserService;
import net.explorviz.code.analysis.parser.AntlrGoParserService;
import net.explorviz.code.analysis.parser.AntlrKotlinParserService;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.analysis.parser.AntlrPhpParserService;
import net.explorviz.code.analysis.parser.AntlrPythonParserService;
import net.explorviz.code.analysis.parser.AntlrRustParserService;
import net.explorviz.code.analysis.parser.AntlrSwiftParserService;
import net.explorviz.code.analysis.parser.AntlrTypeScriptParserService;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Assertions;

/** Languages whose source snippets can be analyzed. */
enum TestLanguage {
  JAVA("java", "java"),
  C("c", "c"),
  CPP("cpp", "cpp"),
  CSHARP("csharp", "cs"),
  GO("go", "go"),
  JAVASCRIPT("javascript", "js"),
  KOTLIN("kotlin", "kt"),
  PHP("php", "php"),
  PYTHON("python", "py"),
  RUST("rust", "rs"),
  SWIFT("swift", "swift"),
  TYPESCRIPT("javascript", "ts");

  private final String directory;
  private final String extension;

  TestLanguage(final String directory, final String extension) {
    this.directory = directory;
    this.extension = extension;
  }

  /** Directory of the resources of the language. */
  String directory() {
    return directory;
  }

  String extension() {
    return extension;
  }

  FileData parse(final String source) {
    final AbstractFileDataHandler handler = tryParse(source);
    Assertions.assertNotNull(handler, source);
    return handler.getProtoBufObject();
  }

  /** Returns {@code null} if the analysis fails. */
  AbstractFileDataHandler tryParse(final String source) {
    final String file = "Test." + extension;
    final String hash = "hash";
    return switch (this) {
      case JAVA -> new AntlrParserService().parseFileContent(source, file, hash);
      case C -> new AntlrCParserService().parseFileContent(source, file, hash);
      case CPP -> new AntlrCppParserService().parseFileContent(source, file, hash);
      case CSHARP -> new AntlrCSharpParserService().parseFileContent(source, file, hash);
      case GO -> new AntlrGoParserService().parseFileContent(source, file, hash);
      case JAVASCRIPT, TYPESCRIPT ->
          new AntlrTypeScriptParserService().parseFileContent(source, file, hash);
      case KOTLIN -> new AntlrKotlinParserService().parseFileContent(source, file, hash);
      case PHP -> new AntlrPhpParserService().parseFileContent(source, file, hash);
      case PYTHON -> new AntlrPythonParserService().parseFileContent(source, file, hash);
      case RUST -> new AntlrRustParserService().parseFileContent(source, file, hash);
      case SWIFT -> new AntlrSwiftParserService().parseFileContent(source, file, hash);
    };
  }
}
