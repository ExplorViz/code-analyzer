package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptLexer;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.handler.TypeScriptFileDataHandler;
import net.explorviz.code.analysis.listener.TypeScriptFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR-based parser service for analyzing TypeScript/JavaScript source code. */
@ApplicationScoped
public class AntlrTypeScriptParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrTypeScriptParserService.class);

  @Inject AntlrParseSettings parseSettings = new AntlrParseSettings();

  public TypeScriptFileDataHandler parseFileContent(
      final String fileContent, final String fileName, final String fileHash) {
    try {
      LOGGER.trace("Parsing TS/JS file content for {}", fileName);
      final String normalizedContent = normalizeTsx(fileContent, fileName);
      final CharStream charStream = CharStreams.fromString(normalizedContent);
      final String extension = getFileExtension(fileName);
      return parse(charStream, fileName, fileHash, extension);
    } catch (Exception e) {
      LOGGER.warn("Failed to parse TS/JS file content for {}: {}", fileName, e.getMessage());
      return null;
    }
  }

  public TypeScriptFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing TS/JS file for {}", pathToFile);
      final String fileContent = java.nio.file.Files.readString(path);
      return parseFileContent(fileContent, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read TS/JS file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.warn("Failed to parse TS/JS file {}: {}", pathToFile, e.getMessage());
      return null;
    }
  }

  private TypeScriptFileDataHandler parse(
      final CharStream charStream,
      final String fileName,
      final String fileHash,
      final String extension) {
    final TypeScriptLexer lexer = new TypeScriptLexer(charStream);
    AntlrParserUtils.configureLexer(lexer);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final TypeScriptParser parser = new TypeScriptParser(tokens);
    // Plain JS has no generics / `as` / non-null assertions; disabling those alts avoids
    // catastrophic LL prediction on config-style object literals (e.g. webpack.*.js).
    final boolean javaScript = ".js".equals(extension) || ".jsx".equals(extension);
    parser.setJavaScriptMode(javaScript);

    final ParseTree program =
        AntlrParserUtils.parseTwoStage(
            parser, tokens, LOGGER, fileName, parseSettings.parseTimeoutMs(), parser::program);

    final TypeScriptFileDataHandler fileDataHandler = new TypeScriptFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    final TypeScriptFileDataListener listener =
        new TypeScriptFileDataListener(fileDataHandler, extension, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, program);

    return fileDataHandler;
  }

  private String getFileExtension(final String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "";
    }
    final String base = fileName.replace('\\', '/');
    final int slash = base.lastIndexOf('/');
    final String simple = slash >= 0 ? base.substring(slash + 1) : base;
    final int lastDot = simple.lastIndexOf('.');
    return lastDot > 0 ? simple.substring(lastDot) : "";
  }

  private static String normalizeTsx(final String fileContent, final String fileName) {
    if (fileName == null) {
      return fileContent;
    }
    final String lower = fileName.toLowerCase();
    if (lower.endsWith(".tsx") || lower.endsWith(".jsx")) {
      return TsxJsxNormalizer.replaceJsxWithNull(fileContent);
    }
    return fileContent;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
