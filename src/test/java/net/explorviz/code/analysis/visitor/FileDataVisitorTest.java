package net.explorviz.code.analysis.visitor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import io.quarkus.test.junit.QuarkusTest;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests for the FileDataVisitor. */
@QuarkusTest
public class FileDataVisitorTest {

  @Test()
  void fileDataTest1() throws FileNotFoundException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("Nested.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    String path = "src/test/resources/files/Nested.java";
    final CompilationUnit compilationUnit = StaticJavaParser.parse(new File(path));
    visitor.visit(compilationUnit, fileDataHandler);
    FileData data = fileDataHandler.getProtoBufObject();
    Assertions.assertEquals("com.easy.life", data.getPackageName());
    Assertions.assertEquals(0, data.getImportNamesCount());
    Assertions.assertEquals(1, data.getClassesCount());
    ClassData clazz = findClass(data.getClassesList(), "Nested");
    Assertions.assertTrue(containsFunction(clazz.getFunctionsList(), "heavyNested"));
    Assertions.assertTrue(containsFunction(clazz.getFunctionsList(), "heavyNested2"));
    Assertions.assertEquals(
        "int",
        findFunction(clazz.getFunctionsList(), "heavyNested2")
            .getParametersList()
            .get(0)
            .getType());
  }

  @Test()
  void fileDataTest2() throws FileNotFoundException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("Happy.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    String path = "src/test/resources/files/Happy.java";
    final CompilationUnit compilationUnit = StaticJavaParser.parse(new File(path));
    visitor.visit(compilationUnit, fileDataHandler);
    FileData data = fileDataHandler.getProtoBufObject();
    Assertions.assertEquals("com.easy.life", data.getPackageName());
    Assertions.assertEquals(4, data.getImportNamesCount());
    Assertions.assertEquals(1, data.getClassesCount());
    ClassData clazz = findClass(data.getClassesList(), "Happy");
    Assertions.assertTrue(containsFunction(clazz.getFunctionsList(), "fromArrayToList"));
    Assertions.assertEquals(
        "java.util.ArrayList<T>",
        findFunction(clazz.getFunctionsList(), "fromArrayToList").getReturnType());
  }

  @Test()
  void fileDataEnumTest() throws FileNotFoundException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("ColorParam.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    String path = "src/test/resources/files/ColorParam.java";
    final CompilationUnit compilationUnit = StaticJavaParser.parse(new File(path));
    visitor.visit(compilationUnit, fileDataHandler);
    FileData data = fileDataHandler.getProtoBufObject();
    Assertions.assertEquals("net.sourceforge.plantuml", data.getPackageName());
    ClassData clazz = findClass(data.getClassesList(), "ColorParam");
    Assertions.assertSame(clazz.getType(), ClassType.ENUM);
    Assertions.assertTrue(contains(clazz.getFieldsList(), "isBackground"));
    Assertions.assertTrue(containsConstant(clazz.getEnumValuesList(), "classArrow"));
    Assertions.assertTrue(containsConstant(clazz.getEnumValuesList(), "noteBorder"));
  }

  @Test()
  void fileDataAnnotationTest() throws FileNotFoundException {
    JavaFileDataHandler fileDataHandler = new JavaFileDataHandler("SimpleJdbcClinic.java");
    FileDataVisitor visitor = new FileDataVisitor(Optional.empty(), false);
    String path = "src/test/resources/files/SimpleJdbcClinic.java";
    StaticJavaParser.getParserConfiguration()
        .setSymbolResolver(new JavaSymbolSolver(new ReflectionTypeSolver(false)));
    final CompilationUnit compilationUnit = StaticJavaParser.parse(new File(path));
    visitor.visit(compilationUnit, fileDataHandler);
    FileData data = fileDataHandler.getProtoBufObject();
    ClassData clazz = findClass(data.getClassesList(), "SimpleJdbcClinic");
    Assertions.assertTrue(containsConstant(clazz.getAnnotationsList(), "Service"));
    FunctionData method = findFunction(clazz.getFunctionsList(), "init");

    Assertions.assertTrue(containsConstant(method.getAnnotationsList(), "Autowired"));
  }

  private ClassData findClass(List<ClassData> classes, String name) {
    return classes.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
  }

  private FunctionData findFunction(List<FunctionData> functions, String name) {
    return functions.stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
  }

  private boolean containsFunction(List<FunctionData> functions, String name) {
    return functions.stream().anyMatch(f -> f.getName().equals(name));
  }

  private boolean contains(final List<FieldData> fields, final String value) {
    for (final FieldData field : fields) {
      if (field.getName().equals(value)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsConstant(final List<String> list, final String value) {
    for (final String entry : list) {
      if (entry.equals(value)) {
        return true;
      }
    }
    return false;
  }
}
