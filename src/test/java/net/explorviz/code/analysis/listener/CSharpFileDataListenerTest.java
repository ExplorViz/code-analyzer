package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.CSharpFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrCSharpParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Test;

public class CSharpFileDataListenerTest {

  private static FileData parse(final String code) {
    final CSharpFileDataHandler handler =
        new AntlrCSharpParserService().parseFileContent(code, "Test.cs", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  @Test
  public void keepsTypesWithTheSameName() {
    final FileData fileData =
        parse(
            """
            namespace A
            {
                class Result { int code; int Code() { return code; } }
                class Result<T>
                {
                    T value;
                    T Value() { return value; }
                    class Inner { void InInner() { } }
                }
            }
            """);

    assertEquals(2, fileData.getClassesCount());
    final ClassData first = fileData.getClasses(0);
    final ClassData second = fileData.getClasses(1);
    assertEquals("Result", first.getName());
    assertEquals("Result", second.getName());
    assertEquals("code", first.getFields(0).getName());
    assertEquals(List.of("Code"), first.getFunctionsList().stream().map(f -> f.getName()).toList());
    assertEquals("value", second.getFields(0).getName());
    assertEquals(
        List.of("Value"), second.getFunctionsList().stream().map(f -> f.getName()).toList());
    assertEquals(1, second.getInnerClassesCount());
    assertEquals("InInner", second.getInnerClasses(0).getFunctions(0).getName());
    assertEquals(0, first.getInnerClassesCount());
  }

  @Test
  public void keepsParamsArrayParameters() {
    final FileData fileData =
        parse(
            """
            class Logger
            {
                void Log() { }
                void Log(params object[] args) { }
                void Log(string format, params object[] args) { }
            }
            """);

    final List<FunctionData> functions = fileData.getClasses(0).getFunctionsList();
    assertEquals(3, functions.size());
    assertEquals(0, functions.get(0).getParametersCount());

    final ParameterData args = functions.get(1).getParameters(0);
    assertEquals("args", args.getName());
    assertEquals("object[]", args.getType());
    assertEquals(List.of("params"), args.getModifiersList());

    assertEquals(2, functions.get(2).getParametersCount());
    assertEquals("format", functions.get(2).getParameters(0).getName());
    assertEquals("args", functions.get(2).getParameters(1).getName());
  }

  @Test
  public void countsCommentLines() {
    final FileData fileData =
        parse(
            """
            // line comment
            /* block
               comment */
            class C
            {
                int x; // trailing comment
                /// doc comment
                void M() { }
            }
            """);

    assertEquals(5.0, fileData.getMetricsOrThrow("cloc"));
    assertEquals(5.0, fileData.getMetricsOrThrow("sloc"));
  }

  @Test
  public void keepsNamespaceAsPackage() {
    assertEquals("A.B", parse("namespace A.B { class C { } }").getPackageName());
    assertEquals("A.B", parse("namespace A { namespace B { class C { } } }").getPackageName());
    assertEquals("A", parse("namespace A { class C { } } namespace D { }").getPackageName());
    assertEquals("A", parse("namespace A { }").getPackageName());
    assertFalse(parse("class C { }").hasPackageName());
  }
}
