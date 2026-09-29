package net.explorviz.code.analysis.regression;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.stream.Stream;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden-file regression tests across all supported languages.
 *
 * <p>To regenerate expected files after intentional analyzer changes, run with {@code
 * -DupdateGoldenFiles=true}.
 */
@QuarkusTest
public class ParserTestInputRegressionTest {

  @Inject AntlrParserService javaParserService;

  @Inject AntlrTypeScriptParserService typeScriptParserService;

  @Inject AntlrPythonParserService pythonParserService;

  @Inject AntlrGoParserService goParserService;

  @Inject AntlrCSharpParserService csharpParserService;

  @Inject AntlrRustParserService rustParserService;

  @Inject AntlrKotlinParserService kotlinParserService;

  @Inject AntlrPhpParserService phpParserService;

  @Inject AntlrSwiftParserService swiftParserService;

  @Inject AntlrCParserService cParserService;

  @Inject AntlrCppParserService cppParserService;

  static Stream<Arguments> fixtures() {
    return Stream.of(
        Arguments.of("java", "ParserTestInput.java"),
        Arguments.of("javascript", "ParserTestInput.js"),
        Arguments.of("javascript", "ParserTestInput.ts"),
        Arguments.of("javascript", "ParserTestInput.jsx"),
        Arguments.of("javascript", "ParserTestInput.tsx"),
        Arguments.of("python", "ParserTestInput.py"),
        Arguments.of("go", "ParserTestInput.go"),
        Arguments.of("csharp", "ParserTestInput.cs"),
        Arguments.of("rust", "ParserTestInput.rs"),
        Arguments.of("kotlin", "ParserTestInput.kt"),
        Arguments.of("php", "ParserTestInput.php"),
        Arguments.of("swift", "ParserTestInput.swift"),
        Arguments.of("c", "ParserTestInput.c"),
        Arguments.of("cpp", "ParserTestInput.cpp"));
  }

  @ParameterizedTest(name = "{0}/{1}")
  @MethodSource("fixtures")
  void parserTestInputMatchesExpectedJson(final String languageDir, final String fileName)
      throws IOException {
    final String source = GoldenFileRegressionSupport.readFixture(languageDir, fileName);
    final AbstractFileDataHandler handler = parse(languageDir, fileName, source);

    Assertions.assertNotNull(
        handler, "Parser should return a handler for " + languageDir + "/" + fileName);

    final FileData actual = handler.getProtoBufObject();
    GoldenFileRegressionSupport.assertMatchesGolden(
        ParserTestInputRegressionTest.class, languageDir, fileName, actual);
  }

  private AbstractFileDataHandler parse(
      final String languageDir, final String fileName, final String source) {
    final String hash = GoldenFileRegressionSupport.FILE_HASH;
    return switch (languageDir) {
      case "java" -> javaParserService.parseFileContent(source, fileName, hash);
      case "javascript" -> typeScriptParserService.parseFileContent(source, fileName, hash);
      case "python" -> pythonParserService.parseFileContent(source, fileName, hash);
      case "go" -> goParserService.parseFileContent(source, fileName, hash);
      case "csharp" -> csharpParserService.parseFileContent(source, fileName, hash);
      case "rust" -> rustParserService.parseFileContent(source, fileName, hash);
      case "kotlin" -> kotlinParserService.parseFileContent(source, fileName, hash);
      case "php" -> phpParserService.parseFileContent(source, fileName, hash);
      case "swift" -> swiftParserService.parseFileContent(source, fileName, hash);
      case "c" -> cParserService.parseFileContent(source, fileName, hash);
      case "cpp" -> cppParserService.parseFileContent(source, fileName, hash);
      default -> throw new IllegalArgumentException("Unknown language dir: " + languageDir);
    };
  }
}
