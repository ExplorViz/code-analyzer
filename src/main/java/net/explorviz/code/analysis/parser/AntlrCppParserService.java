package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import net.explorviz.code.analysis.antlr.generated.CPP14Lexer;
import net.explorviz.code.analysis.antlr.generated.CPP14Parser;
import net.explorviz.code.analysis.handler.CppFileDataHandler;
import net.explorviz.code.analysis.listener.CppFileDataListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR-based parser service for analyzing C/C++ source code.
 */
@ApplicationScoped
public class AntlrCppParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(AntlrCppParserService.class);

  public CppFileDataHandler parseFileContent(final String fileContent, final String fileName,
      final String fileHash) {
    try {
      LOGGER.trace("Parsing C/C++ file content for {}", fileName);
      final CharStream charStream = CharStreams.fromString(fileContent);
      return parse(charStream, fileName, fileHash);
    } catch (Exception e) {
      LOGGER.error("Failed to parse C/C++ file content for {}: {}", fileName, e.getMessage(), e);
      return null;
    }
  }

  public CppFileDataHandler parseFile(final String pathToFile, final String fileHash)
      throws IOException {
    try {
      final Path path = Path.of(pathToFile);
      LOGGER.trace("Parsing C/C++ file for {}", pathToFile);
      final CharStream charStream = CharStreams.fromPath(path);
      return parse(charStream, path.getFileName().toString(), fileHash);
    } catch (IOException e) {
      LOGGER.error("Failed to read C/C++ file {}: {}", pathToFile, e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error("Failed to parse C/C++ file {}: {}", pathToFile, e.getMessage(), e);
      return null;
    }
  }

  private CppFileDataHandler parse(final CharStream charStream, final String fileName,
      final String fileHash) {
    // Create lexer and parser
    final CPP14Lexer lexer = new CPP14Lexer(charStream);
    final CommonTokenStream tokens = new CommonTokenStream(lexer);
    final CPP14Parser parser = new CPP14Parser(tokens);

    // Parse the translation unit (entry point for C/C++)
    final CPP14Parser.TranslationUnitContext translationUnit = parser.translationUnit();

    // Create C/C++ file data handler
    final CppFileDataHandler fileDataHandler = new CppFileDataHandler(fileName);
    fileDataHandler.setFileHash(fileHash);

    // Create and execute the listener
    final CppFileDataListener listener = new CppFileDataListener(fileDataHandler, tokens);
    final ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, translationUnit);

    return fileDataHandler;
  }

  public void reset() {
    LOGGER.trace("Reset called..");
  }
}
