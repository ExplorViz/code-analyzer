package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.PythonLexer;
import net.explorviz.code.analysis.antlr.generated.PythonParser;
import net.explorviz.code.analysis.handler.PythonFileDataHandler;
import net.explorviz.code.analysis.listener.PythonFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Python source code.
 */
@ApplicationScoped
public class AntlrPythonParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrPythonParserService.class);

  @ConfigProperty(name = "explorviz.gitanalysis.assume-unresolved-types-from-wildcard-imports")
  /* default */ boolean wildcardImportProperty; // NOCS

  public PythonFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing Python file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse Python file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public PythonFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing Python file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read Python file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse Python file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private PythonFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    // Create lexer and parser
    final PythonLexer lexer = new PythonLexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final PythonParser parser = new PythonParser(tokens);

    // Parse the file
    final PythonParser.File_inputContext fileInput = parser.file_input();

    // Create Python file data handler
    final PythonFileDataHandler fileDataHandler = new PythonFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    // Create and execute the listener (pass token stream for DEDENT detection)
    final PythonFileDataListener listener = new PythonFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, fileInput);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
