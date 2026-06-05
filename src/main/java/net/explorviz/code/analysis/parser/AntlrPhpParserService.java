package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.php.PhpLexer;
import net.explorviz.code.analysis.antlr.generated.php.PhpParser;
import net.explorviz.code.analysis.handler.PhpFileDataHandler;
import net.explorviz.code.analysis.listener.PhpFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing PHP source code.
 */
@ApplicationScoped
public class AntlrPhpParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrPhpParserService.class);

  public PhpFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing PHP file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.warn("Failed to parse PHP file content for {}: {}", fileName, e.getMessage());
      return null;
    }
  }

  public PhpFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing PHP file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read PHP file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse PHP file {}: {}", pathToFile, e.getMessage());
      return null;
    }
  }

  private PhpFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final PhpLexer lexer = new PhpLexer(charStream);
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final PhpParser parser = new PhpParser(tokens);

    final ParseTree document =
        AntlrParserUtils.parseTwoStage(parser, tokens, LOGGER, fileName, parser::htmlDocument);

    final PhpFileDataHandler fileDataHandler = new PhpFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final PhpFileDataListener listener = new PhpFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, document);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
