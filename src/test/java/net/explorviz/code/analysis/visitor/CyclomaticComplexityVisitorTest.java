package net.explorviz.code.analysis.visitor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.Pair;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.util.Optional;
import net.explorviz.code.analysis.TestResources;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MetricAppender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Testing the CyclomaticComplexityVisitor. */
@QuarkusTest
public class CyclomaticComplexityVisitorTest {

  @Test()
  void cyclomaticComplexityTest() throws IOException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("Nested.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    final CompilationUnit compilationUnit = TestResources.parseJavaFixture("/files/Nested.java");
    visitor.visit(compilationUnit, fileDataHandler);
    CyclomaticComplexityVisitor cyclomaticComplexityVisitor = new CyclomaticComplexityVisitor();
    MetricAppender appender = new MetricAppender(fileDataHandler);
    cyclomaticComplexityVisitor.visit(compilationUnit, new Pair<>(appender, null));

    Assertions.assertEquals(
        "6.0",
        fileDataHandler
            .getClassData("com.easy.life.Nested")
            .getMethod("com.easy.life.Nested.heavyNested#1")
            .getMetricValue("cyclomatic_complexity"));
    Assertions.assertEquals(
        "2.0",
        fileDataHandler
            .getClassData("com.easy.life.Nested")
            .getMethod("com.easy.life.Nested.heavyNested2#1980e")
            .getMetricValue("cyclomatic_complexity"));
    Assertions.assertEquals(
        "4.0",
        fileDataHandler
            .getClassData("com.easy.life.Nested")
            .getMetricValue("cyclomatic_complexity_weighted"));
  }
}
