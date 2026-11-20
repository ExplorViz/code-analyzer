package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.TypeScriptFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrTypeScriptParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

class TypeScriptFileDataListenerTest {

  private static FileData parse(final String source, final String fileName) {
    final TypeScriptFileDataHandler handler =
        new AntlrTypeScriptParserService().parseFileContent(source, fileName, "hash");
    assertNotNull(handler);
    return handler.getProtoBufObject();
  }

  private static List<String> functionNames(final FileData fileData) {
    return fileData.getFunctionsList().stream().map(FunctionData::getName).toList();
  }

  @Test
  void arrowFunctionsAreNamedAfterTheirBindingNotTheirParameter() {
    final String source =
        """
        const double = x => x * 2;
        const doubleAsync = async x => x * 2;
        let assigned;
        assigned = y => y;
        function process(items, cb = () => {}) {
          items.forEach(item => {
            cb(item);
          });
          return items.map(v => v);
        }
        const config = { handler: z => z };
        class Store {
          handle = key => key;
        }
        """;
    for (final String fileName : List.of("a.js", "a.ts")) {
      final FileData fileData = parse(source, fileName);
      assertEquals(
          List.of("double", "doubleAsync", "assigned", "process"),
          functionNames(fileData),
          fileName);
      final ClassData store = fileData.getClasses(0);
      assertEquals("Store", store.getName());
      assertEquals(
          List.of("handle"), store.getFunctionsList().stream().map(FunctionData::getName).toList());
    }
  }

  @Test
  void typesWithTheSameFqnAreAllKept() {
    final String source =
        """
        function first() {
          class Local {
            a(): number { return 1; }
          }
        }
        function second() {
          class Local {
            b(): number { return 2; }
            c(): number { return 3; }
          }
        }
        """;
    final FileData fileData = parse(source, "a.ts");
    assertEquals(2, fileData.getClassesCount());
    assertEquals("Local", fileData.getClasses(0).getName());
    assertEquals("Local", fileData.getClasses(1).getName());
    assertEquals(1, fileData.getClasses(0).getFunctionsCount());
    assertEquals("a", fileData.getClasses(0).getFunctions(0).getName());
    assertEquals(2, fileData.getClasses(1).getFunctionsCount());
    assertEquals(2.0, fileData.getMetricsOrThrow("classCount"));
  }

  @Test
  void multiLineJsxKeepsLaterLineNumbers() {
    final String source =
        """
        export function View() {
          return (
            <div>
              <p>Don't panic</p>
            </div>
          );
        }

        export function after() {
          return 1;
        }
        """;
    final FileData fileData = parse(source, "a.tsx");
    final FunctionData view = fileData.getFunctions(0);
    assertEquals(1, view.getStartLine());
    assertEquals(7, view.getEndLine());
    assertEquals(7.0, view.getMetricsOrThrow("sloc"));
    final FunctionData after = fileData.getFunctions(1);
    assertEquals("after", after.getName());
    assertEquals(9, after.getStartLine());
    assertEquals(11, after.getEndLine());
  }
}
