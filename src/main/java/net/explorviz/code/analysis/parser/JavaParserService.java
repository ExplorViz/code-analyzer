package net.explorviz.code.analysis.parser;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Pair;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import net.explorviz.code.analysis.exceptions.DebugFileWriter;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MetricAppender;
import net.explorviz.code.analysis.visitor.CyclomaticComplexityVisitor;
import net.explorviz.code.analysis.visitor.FileDataVisitor;
import net.explorviz.code.analysis.visitor.LackOfCohesionMethodsVisitor;
import net.explorviz.code.analysis.visitor.NestedBlockDepthVisitor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parser Object loads and parses .java files. */
@ApplicationScoped
public class JavaParserService {

  public static final Logger LOGGER = LoggerFactory.getLogger(JavaParserService.class);
  private static final String CRASHED_FILES_PATH = "/logs/crashedfiles/";

  @ConfigProperty(name = "explorviz.gitanalysis.save-crashed_files")
  /* default */ boolean saveCrashedFilesProperty;

  @ConfigProperty(name = "explorviz.gitanalysis.assume-unresolved-types-from-wildcard-imports")
  /* default */ boolean wildcardImportProperty;

  private List<String> sourcePaths;
  private JavaSymbolSolver javaSymbolSolver;
  private CombinedTypeSolver combinedTypeSolver;
  private ReflectionTypeSolver reflectionTypeSolver;

  /**
   * Creates a new JavaParserService with only the reflectionTypeSolver, call {@link
   * JavaParserService#reset(List)} to add JavaParserTypeSolvers and check in the given paths.
   */
  public JavaParserService() {
    combinedTypeSolver = new CombinedTypeSolver();
    reflectionTypeSolver = new ReflectionTypeSolver(false);
    combinedTypeSolver.add(reflectionTypeSolver);
    javaSymbolSolver = new JavaSymbolSolver(combinedTypeSolver);
  }

  /**
   * Creates a new JavaParserService with the needed TypeSolvers.
   *
   * @param sourcePaths the list of paths to the source code in the repository
   */
  public JavaParserService(final List<String> sourcePaths) {
    this.sourcePaths = sourcePaths;
    combinedTypeSolver = new CombinedTypeSolver();
    reflectionTypeSolver = new ReflectionTypeSolver(false);
    combinedTypeSolver.add(reflectionTypeSolver);
    for (final String path : sourcePaths) {
      combinedTypeSolver.add(new JavaParserTypeSolver(Path.of(path)));
    }
    javaSymbolSolver = new JavaSymbolSolver(combinedTypeSolver);
  }

  /**
   * Creates a new JavaParserService with the needed TypeSolvers.
   *
   * @param sourcePath the path to the source code in the repository
   */
  public JavaParserService(final String sourcePath) {
    this(Collections.singletonList(sourcePath));
  }

  private JavaFileDataHandler parse(
      final CompilationUnit compilationUnit,
      final String fileName,
      final boolean calculateMetrics) {
    final JavaFileDataHandler data = new JavaFileDataHandler(fileName);
    final FileDataVisitor fileDataVisitor;
    fileDataVisitor = new FileDataVisitor(Optional.of(combinedTypeSolver), wildcardImportProperty);
    fileDataVisitor.visit(compilationUnit, data);
    if (calculateMetrics) {
      calculateMetrics(data, compilationUnit, fileName);
    }
    return data;
  }

  /**
   * Resets the state of the JavaParserService, all cached values are cleared and the parser can be
   * reused for another task.
   */
  public void reset() {
    combinedTypeSolver = new CombinedTypeSolver();
    reflectionTypeSolver = new ReflectionTypeSolver(false);
    combinedTypeSolver.add(reflectionTypeSolver);
    for (final String path : this.sourcePaths) {
      combinedTypeSolver.add(new JavaParserTypeSolver(path));
    }
    javaSymbolSolver = new JavaSymbolSolver(combinedTypeSolver);
  }

  /**
   * Resets the JavaParserService but also resets the paths to the source code.
   *
   * @param sourcePaths the paths to the source folders
   */
  public void reset(final List<String> sourcePaths) {
    this.sourcePaths = sourcePaths;
    reset();
  }

  private JavaFileDataHandler parseAny(
      final String fileContent,
      final String fileName,
      final Path path,
      final boolean calculateMetrics,
      final String fileHash)
      throws IOException {
    // ToDo: Make this configurable
    StaticJavaParser.getParserConfiguration().setLanguageLevel(LanguageLevel.JAVA_21);
    StaticJavaParser.getParserConfiguration().setSymbolResolver(this.javaSymbolSolver);
    final CompilationUnit compilationUnit;
    try {
      if (path == null) {
        LOGGER.trace("Parse file content for {}", fileName);
        compilationUnit = StaticJavaParser.parse(fileContent);
      } else {
        LOGGER.trace("Parse compilation unit for {}", fileName);
        compilationUnit = StaticJavaParser.parse(path);
      }
    } catch (ParseProblemException e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Catched Javaparser exception, can't handle this, skipping file: " + fileName);
        LOGGER.error(e.getMessage(), e);
      }
      return null;
    }

    try {
      final JavaFileDataHandler dataHandler = parse(compilationUnit, fileName, calculateMetrics);
      dataHandler.setFileHash(fileHash);
      return dataHandler;
    } catch (NoSuchElementException e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(
            "NoSuchElementException in :" + fileName + System.lineSeparator() + e.getMessage());
      }
    } catch (NoSuchFieldError e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("NoSuchFieldError in " + fileName + System.lineSeparator() + e.getMessage());
      }
    } catch (NoSuchMethodError e) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("NoSuchMethodError in " + fileName + System.lineSeparator() + e.getMessage());
      }
    } catch (Exception | Error e) {
      if (LOGGER.isErrorEnabled()) {

        LOGGER.error("Catched unknown exception in file " + fileName + System.lineSeparator() + e);
      }
    }
    return null;
  }

  /**
   * Parses file content.
   *
   * @param fileContent stringified java file
   */
  public JavaFileDataHandler parseFileContent(
      final String fileContent,
      final String fileName,
      final boolean calculateMetrics,
      final String fileHash) {
    try {
      return parseAny(fileContent, fileName, null, calculateMetrics, fileHash);
    } catch (IOException e) {
      // omit the IO Exception as the function can throw the exception only if path is
      // not null
      return null;
    }
  }

  /**
   * Parses a file completely.
   *
   * @throws IOException Gets thrown if the file is not reachable
   */
  public JavaFileDataHandler parseFile(
      final String pathToFile, final boolean calculateMetrics, final String fileHash)
      throws IOException {
    final Path path = Path.of(pathToFile);
    return parseAny("", path.getFileName().toString(), path, calculateMetrics, fileHash);
  }

  private void calculateMetrics(
      final JavaFileDataHandler data,
      final CompilationUnit compilationUnit,
      final String fileName) {
    try {
      final Pair<MetricAppender, Object> pair = new Pair<>(new MetricAppender(data), new Object());
      new CyclomaticComplexityVisitor().visit(compilationUnit, pair);
    } catch (Exception e) {
      // Catch everything and proceed, as these are only the metrics, the analysis has
      // to continue
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Unable to create cyclomatic complexity metric for File: " + fileName);
        LOGGER.error(e.getMessage(), e);
        if (saveCrashedFilesProperty) {
          DebugFileWriter.saveDebugFile(CRASHED_FILES_PATH, compilationUnit.toString(), fileName);
        }
      }
    }
    try {
      new NestedBlockDepthVisitor()
          .visit(compilationUnit, new Pair<>(new MetricAppender(data), null));
    } catch (Exception e) {
      // Catch everything and proceed, as these are only the metrics, the analysis has
      // to continue
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Unable to create nested block depth metric for File: " + fileName);
        LOGGER.error(e.getMessage(), e);
        if (saveCrashedFilesProperty) {
          DebugFileWriter.saveDebugFile(CRASHED_FILES_PATH, compilationUnit.toString(), fileName);
        }
      }
    }
    try {
      new LackOfCohesionMethodsVisitor()
          .visit(compilationUnit, new Pair<>(new MetricAppender(data), null));
    } catch (Exception e) {
      // Catch everything and proceed, as these are only the metrics, the analysis has
      // to continue
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Unable to create LCOM4 metric for File: " + fileName);
        LOGGER.error(e.getMessage(), e);
        if (saveCrashedFilesProperty) {
          DebugFileWriter.saveDebugFile(CRASHED_FILES_PATH, compilationUnit.toString(), fileName);
        }
      }
    }
  }
}
