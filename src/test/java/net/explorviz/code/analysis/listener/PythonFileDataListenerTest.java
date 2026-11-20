package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.PythonFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrPythonParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

public class PythonFileDataListenerTest {

  @Test
  public void testMultipleFunctionsLineNumbers() {
    // Python file with 3 functions and comments between them
    final String pythonCode =
        """
        # This is a comment
        def first_function():
            print("hello")
            return 1

        # Comment before next function
        def second_function():
            data = get_data()
            return data

        # Comment before third function
        def third_function():
            pass
        """;

    final AntlrPythonParserService parser = new AntlrPythonParserService();
    final PythonFileDataHandler handler = parser.parseFileContent(pythonCode, "test.py", "test123");

    assertNotNull(handler, "Parser should return a handler");

    final FileData fileData = handler.getProtoBufObject();

    assertEquals(3, fileData.getFunctionsCount(), "Should have 3 global functions");

    FunctionData firstFunc = fileData.getFunctions(0);
    assertEquals("first_function", firstFunc.getName());
    assertEquals(2, firstFunc.getStartLine(), "first_function should start at line 2");
    assertEquals(
        4,
        firstFunc.getEndLine(),
        "first_function should end at line 4 (skipping blank line 5 and comment on line 6)");

    FunctionData secondFunc = fileData.getFunctions(1);
    assertEquals("second_function", secondFunc.getName());
    assertEquals(7, secondFunc.getStartLine(), "second_function should start at line 7");
    assertEquals(
        9,
        secondFunc.getEndLine(),
        "second_function should end at line 9 (skipping blank line 10 and comment on line 11)");

    FunctionData thirdFunc = fileData.getFunctions(2);
    assertEquals("third_function", thirdFunc.getName());
    assertEquals(12, thirdFunc.getStartLine(), "third_function should start at line 12");
    assertEquals(13, thirdFunc.getEndLine(), "third_function should end at line 13");
  }

  private static FileData parse(final String code) {
    final PythonFileDataHandler handler =
        new AntlrPythonParserService().parseFileContent(code, "test.py", "test123");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static List<String> lines(final List<FunctionData> functions) {
    return functions.stream()
        .map(f -> f.getName() + ":" + f.getStartLine() + "-" + f.getEndLine())
        .toList();
  }

  @Test
  public void testMethodAndClassEndLines() {
    final FileData fileData =
        parse(
            """
            class A:
                def first(self):
                    return 1

                def second(self):
                    x = 1
                    return x
                    # trailing comment

                def doc(self):
                    return \"\"\"a
            b\"\"\"

            VALUE = 1
            """);

    final ClassData classA = fileData.getClasses(0);
    assertEquals(List.of("first:2-3", "second:5-7", "doc:10-12"), lines(classA.getFunctionsList()));
    assertEquals(2.0, classA.getFunctions(0).getMetricsOrThrow(CommonFileDataListener.LINE_COUNT));
    assertEquals(12.0, classA.getMetricsOrThrow(CommonFileDataListener.LINE_COUNT));
  }

  @Test
  public void testGlobalFunctionEndLines() {
    final FileData fileData =
        parse(
            """
            def one(): return 1
            VALUE = one()


            def two():
                pass
                # trailing comment
               \s
            def three(): pass
            """);

    assertEquals(List.of("one:1-1", "two:5-6", "three:9-9"), lines(fileData.getFunctionsList()));
    assertEquals(
        1.0, fileData.getFunctions(0).getMetricsOrThrow(CommonFileDataListener.LINE_COUNT));
  }

  @Test
  public void testSameNamedMethodsAreKept() {
    final FileData fileData =
        parse(
            """
            class A:
                @property
                def x(self):
                    return self._x

                @x.setter
                def x(self, value):
                    self._x = value
            """);

    assertEquals(List.of("x:3-4", "x:7-8"), lines(fileData.getClasses(0).getFunctionsList()));
  }

  @Test
  public void testRedefinedClassesAreBothKept() {
    final FileData fileData =
        parse(
            """
            if FLAG:
                class A:
                    def first(self):
                        pass

                    class Meta:
                        def inner_first(self):
                            pass
            else:
                class A:
                    def second(self):
                        pass

                    class Meta:
                        def inner_second(self):
                            pass
            """);

    assertEquals(2, fileData.getClassesCount());
    final ClassData first = fileData.getClasses(0);
    final ClassData second = fileData.getClasses(1);
    assertEquals("A", first.getName());
    assertEquals("A", second.getName());
    assertEquals(List.of("first:3-4"), lines(first.getFunctionsList()));
    assertEquals(List.of("second:11-12"), lines(second.getFunctionsList()));
    assertEquals("Meta", first.getInnerClasses(0).getName());
    assertEquals(List.of("inner_first:7-8"), lines(first.getInnerClasses(0).getFunctionsList()));
    assertEquals(
        List.of("inner_second:15-16"), lines(second.getInnerClasses(0).getFunctionsList()));
    assertEquals(4.0, fileData.getMetricsOrThrow(CommonFileDataListener.CLASS_COUNT));
  }
}
