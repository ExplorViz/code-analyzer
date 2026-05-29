package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpLexer;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParser;
import net.explorviz.code.analysis.handler.CSharpFileDataHandler;
import net.explorviz.code.analysis.listener.CSharpFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing C# source code.
 */
@ApplicationScoped
public class AntlrCSharpParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrCSharpParserService.class);

  public CSharpFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing C# file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse C# file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public CSharpFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing C# file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read C# file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse C# file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private CSharpFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final CSharpLexer lexer = new CSharpLexer(charStream);
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final CSharpParser parser = new CSharpParser(tokens);

    final ParseTree prog =
        AntlrParserUtils.parseTwoStage(parser, tokens, LOGGER, fileName, parser::prog);

    final CSharpFileDataHandler fileDataHandler = new CSharpFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final CSharpFileDataListener listener = new CSharpFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, prog);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
