package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.Java20Lexer;
import net.explorviz.code.analysis.antlr.generated.Java20Parser;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.listener.JavaFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Java source code.
 */
@ApplicationScoped
public class AntlrParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrParserService.class);

  @ConfigProperty(name = "explorviz.gitanalysis.assume-unresolved-types-from-wildcard-imports")
  /* default */ boolean wildcardImportProperty; // NOCS

  public JavaFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public JavaFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private JavaFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    // Create lexer and parser
    final Java20Lexer lexer = new Java20Lexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final Java20Parser parser = new Java20Parser(tokens);

    // Parse the compilation unit
    final Java20Parser.CompilationUnitContext compilationUnit = parser.compilationUnit();

    // Create Java file data handler
    final JavaFileDataHandler fileDataHandler = new JavaFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    // Create and execute the listener
    final JavaFileDataListener listener = new JavaFileDataListener(fileDataHandler,
        wildcardImportProperty, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, compilationUnit);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
