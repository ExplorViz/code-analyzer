package net.explorviz.code.analysis.metrics.java;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Assertions;

/** Runs the Java analysis on source snippets. */
final class JavaMetricsTestSupport {

  private JavaMetricsTestSupport() {}

  static FileData parse(final String source) {
    final JavaFileDataHandler handler =
        new AntlrParserService().parseFileContent(source, "Test.java", "hash");
    Assertions.assertNotNull(handler);
    return handler.getProtoBufObject();
  }

  /** Returns a metric of a method with the given statements. */
  static double ofBody(final String metric, final String statements) {
    final FileData file = parse(toClass(statements));
    return file.getClasses(0).getFunctions(0).getMetricsOrThrow(metric);
  }

  /** Returns the complexities that Checkstyle reports for a method with the given statements. */
  static CheckstyleReference checkstyleOfBody(final String statements)
      throws IOException, CheckstyleException {
    final Path file = Files.createTempFile("snippet", ".java");
    try {
      return CheckstyleReference.of(Files.writeString(file, toClass(statements)));
    } finally {
      Files.delete(file);
    }
  }

  private static String toClass(final String statements) {
    return "class A { Object m(Object o, int i) { " + statements + " } }";
  }

  /** Returns a metric of the first class of the given source. */
  static double ofClass(final String metric, final String source) {
    return parse(source).getClasses(0).getMetricsOrThrow(metric);
  }

  static ClassData findClass(final List<ClassData> classes, final String name) {
    return classes.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
  }
}
