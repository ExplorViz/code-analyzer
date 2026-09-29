package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.explorviz.code.analysis.handler.PythonFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrPythonParserService;
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
}
