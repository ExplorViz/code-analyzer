package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinLexer;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.handler.KotlinFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrKotlinParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Test;

public class KotlinFileDataListenerTest {

  private static FileData parse(final String source) {
    final KotlinFileDataHandler handler =
        new AntlrKotlinParserService().parseFileContent(source, "Test.kt", "hash");
    assertNotNull(handler);
    return handler.getProtoBufObject();
  }

  private static List<String> functionNames(final ClassData classData) {
    return classData.getFunctionsList().stream().map(FunctionData::getName).toList();
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
  void classBodyAfterBareSupertypeIsClassBody() {
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

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("task"), fieldNames(a));
    assertEquals(List.of("inner"), fieldNames(a.getInnerClasses(0)));
  }

  @Test
  void secondaryConstructorAndInitAfterBareSupertypeAreKept() {
    final FileData file =
        parse(
            """
            class A : Iface {
                val label: String
                init { val local = 1; label = "x" }
                constructor(x: Int) : this(x.toString())
                constructor(s: String) { label = s }
                fun f() = label
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("label"), fieldNames(a));
    assertEquals(List.of("A", "A", "f"), functionNames(a));
    assertEquals(2, a.getFunctionsList().stream().filter(FunctionData::getIsConstructor).count());
    assertEquals(0, a.getSuperclassesCount());
    assertEquals(List.of("Iface"), a.getImplementedInterfacesList());
    assertEquals(3.0, file.getMetricsMap().get("functionCount"));
  }

  @Test
  void classBodyAfterExplicitDelegationIsClassBody() {
    final FileData file =
        parse(
            """
            class A(impl: Iface) : Iface by impl {
                val extra: Int
                init { extra = 1 }
                constructor() : this(Impl())
                fun g() = extra
            }
            class B : Base(), Iface by Impl(1).wrapped {
                constructor(x: Int) : super(x)
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("extra"), fieldNames(a));
    assertEquals(List.of("A", "g"), functionNames(a));
    assertEquals(0, a.getSuperclassesCount());
    assertEquals(List.of("Iface"), a.getImplementedInterfacesList());

    final ClassData b = file.getClasses(1);
    assertEquals(List.of("B"), functionNames(b));
    assertEquals(List.of("Base.kt::Base"), b.getSuperclassesList());
    assertEquals(List.of("Iface"), b.getImplementedInterfacesList());
  }

  @Test
  void objectLiteralInMethodIsNestedAnonymousClass() {
    final FileData file =
        parse(
            """
            class A {
                fun create(): Iface {
                    return object : Base(1), Iface {
                        val state = 0
                        override fun run() {}
                        fun helper(): Int = state
                    }
                }
                fun other() = 1
            }
            """);

    assertEquals(1, file.getClassesCount());
    final ClassData a = file.getClasses(0);
    assertEquals(List.of("create", "other"), functionNames(a));
    assertEquals(List.of(), fieldNames(a));
    assertEquals(1, a.getInnerClassesCount());

    final ClassData anonymous = a.getInnerClasses(0);
    assertEquals("Anonymous$2", anonymous.getName());
    assertEquals(ClassType.ANONYMOUS_CLASS, anonymous.getType());
    assertEquals(List.of("Base.kt::Base"), anonymous.getSuperclassesList());
    assertEquals(List.of("Iface"), anonymous.getImplementedInterfacesList());
    assertEquals(List.of("state"), fieldNames(anonymous));
    assertEquals(List.of("run", "helper"), functionNames(anonymous));
    assertEquals(4.0, file.getMetricsMap().get("functionCount"));
  }

  @Test
  void topLevelObjectLiteralIsRootAnonymousClass() {
    final FileData file =
        parse(
            """
            val task = object : Runnable {
                override fun run() {}
            }
            fun make() = object {
                fun hello() = "hi"
            }
            """);

    assertEquals(2, file.getClassesCount());
    assertEquals(
        List.of("make"), file.getFunctionsList().stream().map(FunctionData::getName).toList());

    final ClassData first = file.getClasses(0);
    assertEquals("Anonymous$1", first.getName());
    assertEquals(ClassType.ANONYMOUS_CLASS, first.getType());
    assertEquals(List.of("Runnable"), first.getImplementedInterfacesList());
    assertEquals(List.of("run"), functionNames(first));

    final ClassData second = file.getClasses(1);
    assertEquals("Anonymous$2", second.getName());
    assertEquals(List.of("hello"), functionNames(second));
    assertEquals(3.0, file.getMetricsMap().get("functionCount"));
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

  @Test
  void localFunctionsAreNotMethodsOrGlobalFunctions() {
    final FileData file =
        parse(
            """
            class A {
                val prop: Int
                    get() {
                        fun getterHelper() = 1
                        return getterHelper()
                    }
                val lazyProp by lazy {
                    fun lambdaHelper() = 2
                    lambdaHelper()
                }
                init {
                    fun initHelper() {}
                }
                constructor(x: Int) {
                    fun ctorHelper() {}
                }
                fun m(): Int {
                    fun helper(): Int {
                        fun deeper() = 3
                        return deeper()
                    }
                    return helper()
                }
                fun n() = run { fun inLambda() = 4; inLambda() }
            }
            fun outer() {
                fun inner() {}
                class Local {
                    fun method() {
                        fun localHelper() {}
                    }
                }
                val anon = object : Runnable {
                    override fun run() {
                        fun runHelper() {}
                    }
                }
            }
            """);

    final ClassData a = file.getClasses(0);
    assertEquals(List.of("A", "m", "n"), functionNames(a));
    final FunctionData m = a.getFunctions(1);
    assertEquals(7.0, m.getMetricsMap().get("lineCount"));
    assertEquals(
        List.of("outer"), file.getFunctionsList().stream().map(FunctionData::getName).toList());
    final ClassData local = file.getClasses(1);
    assertEquals("Local", local.getName());
    assertEquals(List.of("method"), functionNames(local));
    final ClassData anonymous = file.getClasses(2);
    assertEquals(ClassType.ANONYMOUS_CLASS, anonymous.getType());
    assertEquals(List.of("run"), functionNames(anonymous));
    // A (constructor), m, n, outer, Local.method and run; local functions are not counted
    assertEquals(6.0, file.getMetricsMap().get("functionCount"));
  }

  private static KotlinParser.KotlinFileContext parseTree(
      final String source, final List<String> errors) {
    final KotlinLexer lexer = new KotlinLexer(CharStreams.fromString(source));
    final KotlinParser parser = new KotlinParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(
        new BaseErrorListener() {
          @Override
          public void syntaxError(
              final Recognizer<?, ?> recognizer,
              final Object offendingSymbol,
              final int line,
              final int charPositionInLine,
              final String msg,
              final RecognitionException e) {
            errors.add(line + ":" + charPositionInLine + " " + msg);
          }
        });
    return parser.kotlinFile();
  }

  @Test
  void delegationExpressionsAllowOperatorsAndKeepClassBody() {
    final List<String> delegates =
        List.of(
            "a ?: b",
            "a\n    ?: b",
            "if (x) a else b",
            "if (x) { a } else { b }",
            "when (x) { 1 -> a\n else -> b }",
            "a as Iface",
            "a as? Iface ?: b",
            "(a ?: b)",
            "(list.map { it }.first())",
            "a.b?.c!!",
            "a && b || c",
            "x + y * z",
            "a to b",
            "a[0]",
            "make<Iface>(1)",
            "-a",
            "Impl::create",
            "object : Iface {\n        fun inner() = 1\n    }",
            "try { a } catch (e: Exception) { b }",
            "throw IllegalStateException()");
    for (final String delegate : delegates) {
      final String source =
          "class A : Iface by "
              + delegate
              + " {\n    fun f() = 1\n}\n"
              + "class B<T> : Iface by "
              + delegate
              + " where T : Any {\n    fun g() = 2\n}\n";
      final List<String> errors = new ArrayList<>();
      final KotlinParser.KotlinFileContext tree = parseTree(source, errors);
      assertEquals(List.of(), errors, delegate);
      for (final KotlinParser.TopLevelObjectContext object : tree.topLevelObject()) {
        final KotlinParser.ClassDeclarationContext declaration = object.classDeclaration();
        assertNotNull(declaration.classBody(), delegate);
        assertEquals(1, declaration.classBody().classMemberDeclaration().size(), delegate);
      }

      final FileData file = parse(source);
      assertEquals(List.of("f"), functionNames(file.getClasses(0)), delegate);
      assertEquals(List.of("Iface"), file.getClasses(0).getImplementedInterfacesList(), delegate);
      assertEquals(List.of("g"), functionNames(file.getClasses(1)), delegate);
    }
  }
}
