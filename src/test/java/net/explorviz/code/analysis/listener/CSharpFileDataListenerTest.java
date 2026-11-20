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

  @Test
  public void keepsRefOutInAndThisParameterModifiers() {
    final FileData fileData =
        parse(
            """
            static class Ext
            {
                static int Twice(this int value) { return value * 2; }
                static void Swap(ref int a, ref int b) { }
                static bool TryParse(string text, out int result) { result = 0; return true; }
                static int Sum(in int a, int b) { return a + b; }
                static void Fill(this ref int target) { }
            }
            """);

    final List<FunctionData> functions = fileData.getClasses(0).getFunctionsList();
    assertEquals(5, functions.size());
    assertEquals(List.of("this"), functions.get(0).getParameters(0).getModifiersList());
    assertEquals("int", functions.get(0).getParameters(0).getType());
    assertEquals(List.of("ref"), functions.get(1).getParameters(0).getModifiersList());
    assertEquals(List.of("ref"), functions.get(1).getParameters(1).getModifiersList());
    assertEquals(List.of(), functions.get(2).getParameters(0).getModifiersList());
    assertEquals(List.of("out"), functions.get(2).getParameters(1).getModifiersList());
    assertEquals(List.of("in"), functions.get(3).getParameters(0).getModifiersList());
    assertEquals(List.of(), functions.get(3).getParameters(1).getModifiersList());
    assertEquals(List.of("this", "ref"), functions.get(4).getParameters(0).getModifiersList());
  }

  @Test
  public void keepsOverloadsThatDifferOnlyInRefModifier() {
    final FileData fileData =
        parse(
            """
            class C
            {
                void M(int x) { }
                void M(ref int x) { }
                C(int x) { }
                C(out int x) { x = 0; }
            }
            """);

    final List<FunctionData> functions = fileData.getClasses(0).getFunctionsList();
    assertEquals(4, functions.size());
    final List<List<String>> modifiers =
        functions.stream().map(f -> List.copyOf(f.getParameters(0).getModifiersList())).toList();
    assertEquals(List.of(List.of(), List.of("ref"), List.of(), List.of("out")), modifiers);
  }
}
