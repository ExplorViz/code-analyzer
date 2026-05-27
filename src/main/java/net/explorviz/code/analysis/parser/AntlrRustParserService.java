package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.rust.RustLexer;
import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.handler.RustFileDataHandler;
import net.explorviz.code.analysis.listener.RustFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Rust source code.
 */
@ApplicationScoped
public class AntlrRustParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrRustParserService.class);

  public RustFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing Rust file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse Rust file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public RustFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing Rust file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read Rust file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse Rust file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private RustFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final RustLexer lexer = new RustLexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final RustParser parser = new RustParser(tokens);

    final RustParser.CrateContext crate = parser.crate();

    final RustFileDataHandler fileDataHandler = new RustFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final RustFileDataListener listener = new RustFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, crate);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
