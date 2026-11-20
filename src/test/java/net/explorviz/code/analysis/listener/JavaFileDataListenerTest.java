package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

public class JavaFileDataListenerTest {

  private static FileData parse(final String source) {
    final JavaFileDataHandler handler =
        new AntlrParserService().parseFileContent(source, "Test.java", "hash");
    assertNotNull(handler);
    return handler.getProtoBufObject();
  }

  private static Map<String, FunctionData> functionsByName(final ClassData classData) {
    return classData.getFunctionsList().stream()
        .collect(Collectors.toMap(FunctionData::getName, Function.identity()));
  }

  @Test
  void genericMethodsAndConstructorsKeepModifiers() {
    final FileData file =
        parse(
            """
            package p;
            import java.util.List;
            class A {
              public static <T> List<T> of(T t) { return null; }
              protected <T> A(T seed) { }
            }
            """);

    final Map<String, FunctionData> functions = functionsByName(file.getClasses(0));
    assertEquals(List.of("public", "static"), functions.get("of").getModifiersList());
    assertEquals(List.of("protected"), functions.get("A").getModifiersList());
  }

  @Test
  void genericInterfaceMethodsAreAnalyzed() {
    final FileData file =
        parse(
            """
            package p;
            interface I {
              <T> T convert(Object o);
              static <T> T identity(T t) { return t; }
              static void plain() { }
            }
            """);

    final Map<String, FunctionData> functions = functionsByName(file.getClasses(0));
    assertEquals(3, functions.size());
    assertEquals(List.of(), functions.get("convert").getModifiersList());
    assertEquals(List.of("static"), functions.get("identity").getModifiersList());
    assertEquals(List.of("static"), functions.get("plain").getModifiersList());
    assertEquals(3.0, file.getMetricsMap().get("functionCount"));
  }

  @Test
  void arraysInsideTypeArgumentsAreKept() {
    final FileData file =
        parse(
            """
            package p;
            import java.util.List;
            import java.util.Map;
            class A {
              List<String[]> a;
              Map<String, byte[]> b;
              String[][] c;
              List<String>[] d;
              int[] e;
            }
            """);

    final List<String> types =
        file.getClasses(0).getFieldsList().stream().map(FieldData::getType).toList();
    assertEquals(
        List.of(
            "java.util.List<String[]>",
            "java.util.Map<String,byte[]>",
            "java.lang.String[][]",
            "java.util.List<String>[]",
            "int[]"),
        types);
  }

  @Test
  void localClassesWithSameNameAreAllKept() {
    final FileData file =
        parse(
            """
            package p;
            class A {
              void first() {
                class Local { void one() { } }
              }
              void second() {
                class Local { void two() { } void three() { } }
              }
            }
            """);

    final List<ClassData> inner = file.getClasses(0).getInnerClassesList();
    assertEquals(2, inner.size());
    assertEquals("Local", inner.get(0).getName());
    assertEquals(List.of("one"), functionsByName(inner.get(0)).keySet().stream().toList());
    assertEquals("Local", inner.get(1).getName());
    assertEquals(2, inner.get(1).getFunctionsCount());
  }

  @Test
  void methodTypeParametersStayUnqualified() {
    final FileData file =
        parse(
            """
            package p;
            import java.util.List;
            class A {
              <T> T id(T value) { return value; }
              <T extends Comparable<T>> List<T> sort(List<T> in, T[] arr) { return in; }
              <T> A(T seed) { }
              T notGeneric() { return null; }
            }
            """);

    final Map<String, FunctionData> functions = functionsByName(file.getClasses(0));
    assertEquals("T", functions.get("id").getReturnType());
    assertEquals("T", functions.get("id").getParameters(0).getType());
    assertEquals("java.util.List<T>", functions.get("sort").getReturnType());
    assertEquals("java.util.List<T>", functions.get("sort").getParameters(0).getType());
    assertEquals("T[]", functions.get("sort").getParameters(1).getType());
    assertEquals("T", functions.get("A").getParameters(0).getType());
    // Outside the generic method's scope, T is an ordinary type of package p.
    assertEquals("p.T", functions.get("notGeneric").getReturnType());
  }

  @Test
  void classTypeParametersStayUnqualifiedInFieldsAndInnerClasses() {
    final FileData file =
        parse(
            """
            package p;
            import java.util.List;
            interface I<E> { E first(List<E> all); }
            record R<A>(A value) { }
            class Box<T> {
              T value;
              List<T> values;
              class Inner { T get() { return value; } }
              static class Nested { T get() { return null; } }
            }
            """);

    final Map<String, ClassData> classes =
        file.getClassesList().stream()
            .collect(Collectors.toMap(ClassData::getName, Function.identity()));
    final FunctionData first = functionsByName(classes.get("I")).get("first");
    assertEquals("E", first.getReturnType());
    assertEquals("java.util.List<E>", first.getParameters(0).getType());
    assertEquals("A", classes.get("R").getFields(0).getType());

    final ClassData box = classes.get("Box");
    assertEquals(
        List.of("T", "java.util.List<T>"),
        box.getFieldsList().stream().map(FieldData::getType).toList());
    final Map<String, ClassData> inner =
        box.getInnerClassesList().stream()
            .collect(Collectors.toMap(ClassData::getName, Function.identity()));
    assertEquals("T", functionsByName(inner.get("Inner")).get("get").getReturnType());
    // Static nested classes cannot see the enclosing class's type variables.
    assertEquals("p.T", functionsByName(inner.get("Nested")).get("get").getReturnType());
  }
}
