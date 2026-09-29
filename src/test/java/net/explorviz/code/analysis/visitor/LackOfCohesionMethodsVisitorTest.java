package net.explorviz.code.analysis.visitor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.Pair;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MetricAppender;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests for the LackOfCohesionMethodsVisitor calculating the LCOM4 metric. */
@QuarkusTest
public class LackOfCohesionMethodsVisitorTest {

  private static final String LCOM4 = "LCOM4";

  @Test()
  void fileDataTest1() throws FileNotFoundException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("LCOM4.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    String path = "src/test/resources/files/LCOM4.java";
    final CompilationUnit compilationUnit = StaticJavaParser.parse(new File(path));
    visitor.visit(compilationUnit, fileDataHandler);
    LackOfCohesionMethodsVisitor lcom4Visitor = new LackOfCohesionMethodsVisitor();
    lcom4Visitor.visit(compilationUnit, new Pair<>(new MetricAppender(fileDataHandler), null));
    FileData data = fileDataHandler.getProtoBufObject();
    List<ClassData> classes = data.getClassesList();

    Assertions.assertEquals(1.0, findClass(classes, "LCOM4").getMetricsMap().get(LCOM4));
    Assertions.assertEquals(1.0, findClass(classes, "LCOM4Class2").getMetricsMap().get(LCOM4));
    Assertions.assertEquals(1.0, findClass(classes, "LCOM4Class3").getMetricsMap().get(LCOM4));
    Assertions.assertEquals(0.0, findClass(classes, "LCOM4Class4").getMetricsMap().get(LCOM4));
    Assertions.assertEquals(1.0, findClass(classes, "LCOM4Class5").getMetricsMap().get(LCOM4));
    Assertions.assertEquals(6.0, findClass(classes, "LCOM4Class6").getMetricsMap().get(LCOM4));
  }

  private ClassData findClass(List<ClassData> classes, String name) {
    return classes.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
  }
}
