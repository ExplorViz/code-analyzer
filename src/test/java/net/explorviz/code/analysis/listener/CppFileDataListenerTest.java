package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.explorviz.code.analysis.handler.CppFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrCppParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Test;

public class CppFileDataListenerTest {

  private static FileData parse(final String source) {
    final CppFileDataHandler handler =
        new AntlrCppParserService().parseFileContent(source, "test.cpp", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static ClassData findClass(final FileData fileData, final String name) {
    return fileData.getClassesList().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("class " + name + " not found"));
  }

  private static List<FunctionData> functionsNamed(final ClassData classData, final String name) {
    return classData.getFunctionsList().stream().filter(f -> f.getName().equals(name)).toList();
  }

  private static FunctionData functionNamed(final ClassData classData, final String name) {
    final List<FunctionData> functions = functionsNamed(classData, name);
    assertEquals(1, functions.size(), "expected exactly one function " + name);
    return functions.get(0);
  }

  private static List<String> parameterTypes(final FunctionData function) {
    return function.getParametersList().stream().map(ParameterData::getType).toList();
  }

  private static double metric(final FileData fileData, final String name) {
    return fileData.getMetricsOrThrow(name);
  }

  @Test
  void constructorAndDestructorDeclarationsAreKept() {
    final FileData fileData =
        parse(
            """
            class Widget {
            public:
              Widget();
              explicit Widget(int size);
              ~Widget();
            };
            """);

    final ClassData widget = findClass(fileData, "Widget");
    final List<FunctionData> constructors = functionsNamed(widget, "Widget");
    assertEquals(2, constructors.size());
    assertTrue(constructors.stream().allMatch(FunctionData::getIsConstructor));
    assertEquals(List.of(), parameterTypes(constructors.get(0)));
    assertEquals(List.of("int"), parameterTypes(constructors.get(1)));
    assertTrue(constructors.get(1).getModifiersList().contains("explicit"));

    final FunctionData destructor = functionNamed(widget, "~Widget");
    assertFalse(destructor.getIsConstructor());
    assertEquals("void", destructor.getReturnType());
    assertEquals(3.0, metric(fileData, "functionCount"));
  }

  @Test
  void pureSpecifierIsDetectedFromGrammarNotFromDefaultArguments() {
    final FileData fileData =
        parse(
            """
            class Shape {
            public:
              virtual void draw() = 0;
              virtual Shape* clone() const = 0;
              void resize(int n = 0);
            };
            """);

    final ClassData shape = findClass(fileData, "Shape");
    assertEquals(List.of("virtual", "pure"), functionNamed(shape, "draw").getModifiersList());
    assertEquals(List.of("virtual", "pure"), functionNamed(shape, "clone").getModifiersList());
    assertEquals("Shape*", functionNamed(shape, "clone").getReturnType());
    assertEquals(List.of(), functionNamed(shape, "resize").getModifiersList());
  }

  @Test
  void overloadedDeclarationsAreKeptAndMergedWithOutOfLineDefinitions() {
    final FileData fileData =
        parse(
            """
            class Widget {
            public:
              Widget(int size);
              virtual void draw();
              void draw(int times);
              ~Widget();
            };

            Widget::Widget(int size) {}

            void Widget::draw(int times) {
              for (int i = 0; i < times; i++) {}
            }

            Widget::~Widget() {}
            """);

    final ClassData widget = findClass(fileData, "Widget");
    final List<FunctionData> draws = functionsNamed(widget, "draw");
    assertEquals(2, draws.size());
    assertEquals(List.of(), parameterTypes(draws.get(0)));
    assertEquals(List.of("virtual"), draws.get(0).getModifiersList());
    assertEquals(List.of("int"), parameterTypes(draws.get(1)));
    // The definition replaced the declaration
    assertEquals(11, draws.get(1).getStartLine());
    assertEquals(13, draws.get(1).getEndLine());

    final FunctionData constructor = functionNamed(widget, "Widget");
    assertTrue(constructor.getIsConstructor());
    assertEquals(9, constructor.getStartLine());

    final FunctionData destructor = functionNamed(widget, "~Widget");
    assertEquals("void", destructor.getReturnType());
    assertEquals(15, destructor.getStartLine());

    assertEquals(0, fileData.getFunctionsCount(), "definitions must not become global functions");
    assertEquals(4.0, metric(fileData, "functionCount"));
  }

  @Test
  void outOfLineDefinitionOfUnknownClassStaysGlobal() {
    final FileData fileData = parse("void Other::run(int x) {}\n");

    assertEquals(1, fileData.getFunctionsCount());
    assertEquals("Other::run", fileData.getFunctions(0).getName());
    assertEquals(List.of("int"), parameterTypes(fileData.getFunctions(0)));
  }

  @Test
  void trailingReturnTypes() {
    final FileData fileData =
        parse(
            """
            auto compute(int a, int b) -> int {
              return a + b;
            }

            class Box {
            public:
              auto size() const -> int;
            };
            """);

    final FunctionData compute = fileData.getFunctions(0);
    assertEquals("compute", compute.getName());
    assertEquals("int", compute.getReturnType());
    assertEquals(List.of("int", "int"), parameterTypes(compute));

    final ClassData box = findClass(fileData, "Box");
    assertEquals(0, box.getFieldsCount());
    assertEquals("int", functionNamed(box, "size").getReturnType());
  }

  @Test
  void anonymousEnumDoesNotLeakIntoEnclosingClass() {
    final FileData fileData =
        parse(
            """
            class Widget {
              enum { kMax = 10 };
              enum Mode { kOn, kOff };
            };
            """);

    final ClassData widget = findClass(fileData, "Widget");
    assertEquals(List.of(), widget.getEnumValuesList());
    assertEquals(1, widget.getInnerClassesCount());
    assertEquals(List.of("kOn", "kOff"), widget.getInnerClasses(0).getEnumValuesList());
  }

  @Test
  void pointerReturnAndFieldTypesKeepPointer() {
    final FileData fileData =
        parse(
            """
            char* name() { return 0; }

            class Holder {
              const char* label;
              int& ref();
            };
            """);

    assertEquals("char*", fileData.getFunctions(0).getReturnType());
    final ClassData holder = findClass(fileData, "Holder");
    assertEquals("char*", holder.getFields(0).getType());
    assertEquals("int&", functionNamed(holder, "ref").getReturnType());
  }

  @Test
  void unnamedPointerParametersKeepPointer() {
    final FileData fileData = parse("void take(const char*, int&) {}\n");

    assertEquals(List.of("char*", "int&"), parameterTypes(fileData.getFunctions(0)));
  }

  @Test
  void variableCountIgnoresPrototypesAndTypedefs() {
    final FileData fileData =
        parse(
            """
            int helper();
            typedef int Id;
            int (*callback)(int);
            int counter = 0, other;
            """);

    assertEquals(3.0, metric(fileData, "variableCount"));
  }

  @Test
  void packageIsOutermostFirstNamespace() {
    final FileData fileData =
        parse(
            """
            namespace foo {
            namespace detail {
            int helper();
            }
            class Widget {};
            }
            namespace bar {
            class Other {};
            }
            """);

    assertEquals("foo", fileData.getPackageName());
  }

  @Test
  void decltypeDestructorDoesNotFail() {
    final FileData fileData =
        parse(
            """
            struct S {
              ~decltype(auto)();
            };
            S::~decltype(auto)() {}
            """);

    final ClassData s = findClass(fileData, "S");
    assertEquals(
        List.of("~decltype(auto)"), s.getFunctionsList().stream().map(f -> f.getName()).toList());
  }

  @Test
  void classesWithTheSameNameAreBothKept() {
    final FileData fileData =
        parse(
            """
            #if USE_FAST
            class Widget {
              int fast;
              void a();
              class Inner { int x; };
            };
            #else
            class Widget {
              int slow;
              void b();
            };
            #endif
            """);

    assertEquals(2, fileData.getClassesCount());
    final ClassData first = fileData.getClasses(0);
    final ClassData second = fileData.getClasses(1);
    assertEquals("Widget", first.getName());
    assertEquals("Widget", second.getName());
    assertEquals("fast", first.getFields(0).getName());
    assertEquals("a", first.getFunctions(0).getName());
    assertEquals("Inner", first.getInnerClasses(0).getName());
    assertEquals("slow", second.getFields(0).getName());
    assertEquals("b", second.getFunctions(0).getName());
    assertEquals(0, second.getInnerClassesCount());
  }
}
