package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinLexer;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.handler.KotlinFileDataHandler;
import net.explorviz.code.analysis.listener.KotlinFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing Kotlin source code.
 */
@ApplicationScoped
public class AntlrKotlinParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrKotlinParserService.class);

  public KotlinFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing Kotlin file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent, fileName);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse Kotlin file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public KotlinFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing Kotlin file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read Kotlin file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse Kotlin file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private KotlinFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    final KotlinLexer lexer = new KotlinLexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final KotlinParser parser = new KotlinParser(tokens);

    final boolean isScript = fileName.toLowerCase().endsWith(".kts");
    final ParseTree tree = isScript ? parser.script() : parser.kotlinFile();

    final KotlinFileDataHandler fileDataHandler = new KotlinFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final KotlinFileDataListener listener = new KotlinFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, tree);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
