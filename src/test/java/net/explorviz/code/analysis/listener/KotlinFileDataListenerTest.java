package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.KotlinFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrKotlinParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

public class KotlinFileDataListenerTest {

  private static FileData parse(final String source) {
    final KotlinFileDataHandler handler =
        new AntlrKotlinParserService().parseFileContent(source, "Test.kt", "hash");
    assertNotNull(handler);
    return handler.getProtoBufObject();
  }

  private static List<String> fieldNames(final ClassData classData) {
    return classData.getFieldsList().stream().map(FieldData::getName).toList();
  }

  @Test
  void onlyClassBodyPropertiesAreFields() {
    final FileData file =
        parse(
            """
            class A {
                val v by lazy { val tmp = load(); tmp }
                var w: Int = 0
                    get() { val g = field; return g }
                init { val n = 1 }
                constructor(x: Int) { val c = x }
                fun f() { val local = 1 }
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("v", "w"), fieldNames(a));
    assertEquals(
        List.of("A", "f"), a.getFunctionsList().stream().map(FunctionData::getName).toList());
  }

  @Test
  void propertiesOfClassBodyParsedAsLambdaAreFields() {
    // The grammar parses this body as a trailing lambda of "Runnable".
    final FileData file =
        parse(
            """
            class A : Runnable {
                val state: Int = 1
                override fun run() { val local = state }
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("state"), fieldNames(a));
    assertEquals(0, a.getSuperclassesCount());
    assertEquals(List.of("Runnable"), a.getImplementedInterfacesList());
  }

  @Test
  void anonymousObjectPropertiesAreNotFieldsOfTheEnclosingClass() {
    final FileData file =
        parse(
            """
            class A {
                private val task = object : Runnable {
                    val inner = 1
                }
            }
            """);

    assertEquals(List.of("task"), fieldNames(file.getClasses(0)));
  }

  @Test
  void extensionPropertyHasDeclaredTypeNotReceiver() {
    final FileData file =
        parse(
            """
            class A {
                val String.last: Char get() = this[length - 1]
                val String.first get(): Char = this[0]
            }
            """);

    final List<FieldData> fields = file.getClasses(0).getFieldsList();
    assertEquals("last", fields.get(0).getName());
    assertEquals("Char", fields.get(0).getType());
    assertEquals("first", fields.get(1).getName());
    assertEquals("Char", fields.get(1).getType());
  }

  @Test
  void onlyConstructorCallNamesSuperclass() {
    final FileData file =
        parse(
            """
            class A : Runnable
            class B : Iface, Base()
            object C : Base(1), Iface
            class D : Iface by impl
            interface E : Iface, Other
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(0, a.getSuperclassesCount());
    assertEquals(List.of("Runnable"), a.getImplementedInterfacesList());

    final ClassData b = file.getClasses(1);
    assertEquals(List.of("Base.kt::Base"), b.getSuperclassesList());
    assertEquals(List.of("Iface"), b.getImplementedInterfacesList());

    final ClassData c = file.getClasses(2);
    assertEquals(List.of("Base.kt::Base"), c.getSuperclassesList());
    assertEquals(List.of("Iface"), c.getImplementedInterfacesList());

    final ClassData d = file.getClasses(3);
    assertEquals(0, d.getSuperclassesCount());
    assertEquals(List.of("Iface"), d.getImplementedInterfacesList());

    final ClassData e = file.getClasses(4);
    assertEquals(0, e.getSuperclassesCount());
    assertEquals(List.of("Iface", "Other"), e.getImplementedInterfacesList());
  }

  @Test
  void bareSuperclassInitializedBySecondaryConstructor() {
    final FileData file =
        parse(
            """
            class A : Base, Iface {
                companion object
                constructor(x: Int) : super(x)
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("Base.kt::Base"), a.getSuperclassesList());
    assertEquals(List.of("Iface"), a.getImplementedInterfacesList());
  }

  @Test
  void typesWithSameFqnAreAllKept() {
    final FileData file =
        parse(
            """
            fun first() {
                class Local(val a: Int) {
                    class Nested(val n: Int)
                }
            }
            fun second() {
                class Local(val b: Int) {
                    fun describe() = b
                }
            }
            """);

    assertEquals(2, file.getClassesCount());
    final ClassData first = file.getClasses(0);
    final ClassData second = file.getClasses(1);
    assertEquals("Local", first.getName());
    assertEquals(List.of("a"), fieldNames(first));
    assertEquals(1, first.getInnerClassesCount());
    assertEquals(List.of("n"), fieldNames(first.getInnerClasses(0)));
    assertEquals(0, first.getFunctionsCount());
    assertEquals("Local", second.getName());
    assertEquals(List.of("b"), fieldNames(second));
    assertEquals(1, second.getFunctionsCount());
    assertEquals(0, second.getInnerClassesCount());
  }
}
