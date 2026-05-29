package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.golang.GoLexer;
import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.handler.GoFileDataHandler;
import net.explorviz.code.analysis.listener.GoFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Go source code.
 */
@ApplicationScoped
public class AntlrGoParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrGoParserService.class);

  public GoFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing Go file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse Go file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public GoFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing Go file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read Go file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse Go file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private GoFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final GoLexer lexer = new GoLexer(charStream);
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final GoParser parser = new GoParser(tokens);

    final ParseTree sourceFile =
        AntlrParserUtils.parseTwoStage(parser, tokens, LOGGER, fileName, parser::sourceFile);

    final GoFileDataHandler fileDataHandler = new GoFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final GoFileDataListener listener = new GoFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, sourceFile);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
