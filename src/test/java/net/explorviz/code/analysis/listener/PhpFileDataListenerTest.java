package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.PhpFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrPhpParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

public class PhpFileDataListenerTest {

  private static FileData parse(final String code) {
    final PhpFileDataHandler handler =
        new AntlrPhpParserService().parseFileContent(code, "app/Models/Test.php", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static List<String> functionNames(final ClassData classData) {
    return classData.getFunctionsList().stream().map(FunctionData::getName).toList();
  }

  private static List<String> fieldNames(final ClassData classData) {
    return classData.getFieldsList().stream().map(FieldData::getName).toList();
  }

  @Test
  void unbracedNamespaceSetsPackageName() {
    final FileData fileData =
        parse(
            """
            <?php
            namespace App\\Models;

            class User extends Model {}
            """);

    assertEquals("App.Models", fileData.getPackageName());
    assertEquals("User", fileData.getClasses(0).getName());
    assertEquals(
        List.of("app/Models/Model.php::Model"), fileData.getClasses(0).getSuperclassesList());
  }

  @Test
  void bracedNamespaceKeepsPackageNameAfterBlock() {
    final FileData fileData =
        parse(
            """
            <?php
            namespace App\\Models {
                class User {}
            }
            """);

    assertEquals("App.Models", fileData.getPackageName());
    assertEquals(1, fileData.getClassesCount());
  }

  @Test
  void classMethodReturnTypesAreRead() {
    final FileData fileData =
        parse(
            """
            <?php
            class C {
                public function count(): int { return 1; }
                public function name(): string { return ''; }
                public function maybe(): ?array { return null; }
                public function nothing() {}
            }
            """);

    final List<String> returnTypes =
        fileData.getClasses(0).getFunctionsList().stream()
            .map(FunctionData::getReturnType)
            .toList();
    assertEquals(List.of("int", "string", "array", "void"), returnTypes);
  }

  @Test
  void clocCountsAllCommentStyles() {
    final FileData fileData =
        parse(
            """
            <?php
            // line comment
            # shell comment
            /* block
               comment */
            $a = 1; // trailing comment
            """);

    assertEquals(5.0, fileData.getMetricsMap().get(CommonFileDataListener.CLOC));
  }

  @Test
  void anonymousClassMembersAreNotAttributedToEnclosingClass() {
    final FileData fileData =
        parse(
            """
            <?php
            class Outer {
                function handle(int $a) {}
                function make() {
                    return new class {
                        public $inner;
                        function handle(int $a) {}
                    };
                }
            }
            """);

    assertEquals(1, fileData.getClassesCount());
    final ClassData outer = fileData.getClasses(0);
    assertEquals("Outer", outer.getName());
    assertEquals(List.of(), fieldNames(outer));
    assertEquals(List.of("handle", "make"), functionNames(outer));

    assertEquals(1, outer.getInnerClassesCount());
    final ClassData anonymous = outer.getInnerClasses(0);
    assertEquals(ClassType.ANONYMOUS_CLASS, anonymous.getType());
    assertEquals(List.of("inner"), fieldNames(anonymous));
    assertEquals(List.of("handle"), functionNames(anonymous));
  }

  @Test
  void topLevelAnonymousClassKeepsItsMethods() {
    final FileData fileData =
        parse(
            """
            <?php
            use Illuminate\\Database\\Migrations\\Migration;

            return new class extends Migration {
                public function up(): void {}
                public function down(): void {}
            };
            """);

    assertEquals(1, fileData.getClassesCount());
    final ClassData anonymous = fileData.getClasses(0);
    assertEquals(ClassType.ANONYMOUS_CLASS, anonymous.getType());
    assertEquals(List.of("up", "down"), functionNames(anonymous));
    assertEquals(1, anonymous.getSuperclassesCount());
    assertEquals(2.0, fileData.getMetricsMap().get(CommonFileDataListener.FUNCTION_COUNT));
  }

  @Test
  void sameNamedClassesAreBothKept() {
    final FileData fileData =
        parse(
            """
            <?php
            if (PHP_VERSION_ID >= 80000) {
                class Compat { function first() {} }
            } else {
                class Compat { function second() {} }
            }
            """);

    assertEquals(2, fileData.getClassesCount());
    assertEquals("Compat", fileData.getClasses(0).getName());
    assertEquals(List.of("first"), functionNames(fileData.getClasses(0)));
    assertEquals("Compat", fileData.getClasses(1).getName());
    assertEquals(List.of("second"), functionNames(fileData.getClasses(1)));
    assertEquals(2.0, fileData.getMetricsMap().get(CommonFileDataListener.CLASS_COUNT));
  }
}
