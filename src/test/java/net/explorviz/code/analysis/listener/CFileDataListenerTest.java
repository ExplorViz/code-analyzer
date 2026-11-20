package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.CFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrCParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Test;

public class CFileDataListenerTest {

  private static FileData parse(final String source) {
    final CFileDataHandler handler =
        new AntlrCParserService().parseFileContent(source, "test.c", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static FunctionData function(final FileData fileData, final String name) {
    return fileData.getFunctionsList().stream()
        .filter(f -> f.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("function " + name + " not found"));
  }

  private static List<String> parameterTypes(final FunctionData function) {
    return function.getParametersList().stream().map(ParameterData::getType).toList();
  }

  @Test
  void voidParameterListMeansNoParameters() {
    final FileData fileData =
        parse(
            """
            void reset(void) {}
            void legacy() {}
            int add(int a, int b) { return a + b; }
            """);

    assertEquals(0, function(fileData, "reset").getParametersCount());
    assertEquals(0, function(fileData, "legacy").getParametersCount());
    assertEquals(List.of("int", "int"), parameterTypes(function(fileData, "add")));
  }

  @Test
  void structTypesAreSpaced() {
    final FileData fileData =
        parse(
            """
            struct Node {
              struct Node *next;
              unsigned long count;
            };
            struct Node make_node(struct Node *prev) { struct Node n; n.next = prev; return n; }
            """);

    final ClassData node = fileData.getClasses(0);
    assertEquals(
        List.of("struct Node*", "unsigned long"),
        node.getFieldsList().stream().map(FieldData::getType).toList());
    final FunctionData makeNode = function(fileData, "make_node");
    assertEquals("struct Node", makeNode.getReturnType());
    assertEquals(List.of("struct Node*"), parameterTypes(makeNode));
  }

  @Test
  void pointersAreKept() {
    final FileData fileData =
        parse(
            """
            struct Holder {
              char *p;
              char **pp;
            };
            char *name_of(int id) { return 0; }
            int count(const char *, int) { return 0; }
            """);

    final ClassData holder = fileData.getClasses(0);
    assertEquals(
        List.of("char*", "char**"),
        holder.getFieldsList().stream().map(FieldData::getType).toList());
    assertEquals("char*", function(fileData, "name_of").getReturnType());
    assertEquals(List.of("char*", "int"), parameterTypes(function(fileData, "count")));
  }

  @Test
  void variableCountIgnoresPrototypesAndTypedefs() {
    final FileData fileData =
        parse(
            """
            int add(int, int);
            void reset(void);
            typedef struct { int x; int y; } Point;
            int (*handler)(int);
            int counter = 0, other;
            """);

    assertEquals(3.0, fileData.getMetricsOrThrow("variableCount"));
  }

  @Test
  void anonymousEnumDoesNotLeakIntoStruct() {
    final FileData fileData =
        parse(
            """
            struct Config {
              enum { MODE_A, MODE_B } mode;
              enum Level { LOW, HIGH } level;
            };
            """);

    final ClassData config = fileData.getClasses(0);
    assertEquals(List.of(), config.getEnumValuesList());
    assertEquals(1, config.getInnerClassesCount());
    assertEquals(List.of("LOW", "HIGH"), config.getInnerClasses(0).getEnumValuesList());
  }

  @Test
  void typesWithTheSameNameAreBothKept() {
    final FileData fileData =
        parse(
            """
            #ifdef USE_FAST
            struct Buffer { int fast; };
            #else
            struct Buffer { int slow; };
            #endif
            """);

    assertEquals(2, fileData.getClassesCount());
    assertEquals("Buffer", fileData.getClasses(0).getName());
    assertEquals("fast", fileData.getClasses(0).getFields(0).getName());
    assertEquals("Buffer", fileData.getClasses(1).getName());
    assertEquals("slow", fileData.getClasses(1).getFields(0).getName());
  }

  @Test
  void anonymousMemberTypesOwnTheirFields() {
    final FileData fileData =
        parse(
            """
            struct S {
                int a;
                struct { int x; int y; } pos;
                union { int i; float f; };
                struct { struct { int z; } deep; } outer;
            };
            """);

    assertEquals(1, fileData.getClassesCount());
    final ClassData s = fileData.getClasses(0);
    // Members of the unnamed union are accessed as s.i / s.f, so they belong to S.
    assertEquals(
        List.of("a", "pos", "i", "f", "outer"),
        s.getFieldsList().stream().map(FieldData::getName).toList());
    assertEquals("struct <anonymous>", s.getFields(1).getType());
    assertEquals("struct <anonymous>", s.getFields(4).getType());

    assertEquals(2, s.getInnerClassesCount());
    final ClassData pos = s.getInnerClasses(0);
    assertEquals("<anonymous>", pos.getName());
    assertEquals(ClassType.ANONYMOUS_CLASS, pos.getType());
    assertEquals(List.of("struct"), pos.getModifiersList());
    assertEquals(List.of("x", "y"), pos.getFieldsList().stream().map(FieldData::getName).toList());

    final ClassData outer = s.getInnerClasses(1);
    assertEquals(List.of("deep"), outer.getFieldsList().stream().map(FieldData::getName).toList());
    assertEquals(1, outer.getInnerClassesCount());
    assertEquals("z", outer.getInnerClasses(0).getFields(0).getName());
  }
}
