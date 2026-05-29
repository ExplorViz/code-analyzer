package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5Lexer;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5Parser;
import net.explorviz.code.analysis.handler.SwiftFileDataHandler;
import net.explorviz.code.analysis.listener.SwiftFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Swift source code.
 */
@ApplicationScoped
public class AntlrSwiftParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrSwiftParserService.class);

  public SwiftFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing Swift file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse Swift file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public SwiftFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing Swift file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read Swift file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse Swift file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private SwiftFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final Swift5Lexer lexer = new Swift5Lexer(charStream);
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final Swift5Parser parser = new Swift5Parser(tokens);

    final ParseTree topLevel =
        AntlrParserUtils.parseTwoStage(parser, tokens, LOGGER, fileName, parser::top_level);

    final SwiftFileDataHandler fileDataHandler = new SwiftFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final SwiftFileDataListener listener = new SwiftFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, topLevel);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
