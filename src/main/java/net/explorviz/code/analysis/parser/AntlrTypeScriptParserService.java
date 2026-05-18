package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.TypeScriptLexer;
import net.explorviz.code.analysis.antlr.generated.TypeScriptParser;
import net.explorviz.code.analysis.handler.TypeScriptFileDataHandler;
import net.explorviz.code.analysis.listener.TypeScriptFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing TypeScript/JavaScript source code.
 */
@ApplicationScoped
public class AntlrTypeScriptParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrTypeScriptParserService.class);

  public TypeScriptFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing TS/JS file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent);
      final String extension = getFileExtension(fileName);
      return parse(charStream, fileName, fileHash, extension);
    } catch (Exception e) {
      LOGGER.error("Failed to parse TS/JS file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public TypeScriptFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing TS/JS file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      final String fileName = path.getFileName().toString();
      final String extension = getFileExtension(fileName);
      return parse(charStream, fileName, fileHash, extension);
    } catch (IOException e) {
      LOGGER.error("Failed to read TS/JS file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse TS/JS file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private TypeScriptFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash, final String extension) {
    // Create lexer and parser
    final TypeScriptLexer lexer = new TypeScriptLexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TypeScriptParser parser = new TypeScriptParser(tokens);

    // Parse the program (entry point for TS/JS)
    final TypeScriptParser.ProgramContext program = parser.program();

    // Create TypeScript file data handler
    final TypeScriptFileDataHandler fileDataHandler = new TypeScriptFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    // Create and execute the listener
    final TypeScriptFileDataListener listener = new TypeScriptFileDataListener(
        fileDataHandler,
        extension,
        tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, program);

    return fileDataHandler;
  }

  private String getFileExtension(final String fileName) {
    final int lastDot = fileName.lastIndexOf('.');
    return lastDot > 0 ? fileName.substring(lastDot) : "";
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
