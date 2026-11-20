package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.explorviz.code.analysis.handler.SwiftFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrSwiftParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Test;

public class SwiftFileDataListenerTest {

  private static SwiftFileDataHandler parseHandler(final String code) {
    final SwiftFileDataHandler handler =
        new AntlrSwiftParserService().parseFileContent(code, "Test.swift", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler;
  }

  private static FileData parse(final String code) {
    return parseHandler(code).getProtoBufObject();
  }

  private static List<String> functionNames(final ClassData classData) {
    return classData.getFunctionsList().stream().map(FunctionData::getName).toList();
  }

  private static List<String> parameterNames(final FunctionData function) {
    return function.getParametersList().stream().map(ParameterData::getName).toList();
  }

  @Test
  void onlyTypeMembersBecomeFieldsWithTheirAnnotatedType() {
    final FileData fileData =
        parse(
            """
            class C {
                var count: Int = 0
                let name: String = ""
                var inferred = 1.0
                func f() {
                    let tmp = 1
                    var local: Int = 2
                }
                init() {
                    let other = 3
                }
            }
            struct S {
                let id: UInt64
            }
            """);

    final List<String> fields =
        fileData.getClasses(0).getFieldsList().stream()
            .map(field -> field.getName() + ":" + field.getType())
            .toList();
    assertEquals(List.of("count:Int", "name:String", "inferred:unknown"), fields);

    final List<String> structFields =
        fileData.getClasses(1).getFieldsList().stream()
            .map(field -> field.getName() + ":" + field.getType())
            .toList();
    assertEquals(List.of("id:UInt64"), structFields);
  }

  @Test
  void overloadsDifferingOnlyInArgumentLabelsAreDistinguished() {
    final SwiftFileDataHandler handler =
        parseHandler(
            """
            class Box {
                init(name: String) {}
                init(id: String) {}
                func set(width: Int) {}
                func set(height: Int) {}
                func move(_ x: Int) {}
                func move(by x: Int) {}
            }
            """);
    final ClassData box = handler.getProtoBufObject().getClasses(0);

    assertEquals(List.of("Box", "Box", "set", "set", "move", "move"), functionNames(box));
    assertEquals(List.of("name"), parameterNames(box.getFunctions(0)));
    assertEquals(List.of("id"), parameterNames(box.getFunctions(1)));
    assertEquals(List.of("width"), parameterNames(box.getFunctions(2)));
    assertEquals(List.of("height"), parameterNames(box.getFunctions(3)));

    // The method keys carry the selector including the argument labels.
    final String dump = handler.toString();
    for (final String key :
        List.of(
            "Box(name:)#",
            "Box(id:)#",
            "set(width:)#",
            "set(height:)#",
            "move(_:)#",
            "move(by:)#")) {
      assertTrue(dump.contains(key), "Missing method key " + key + " in " + dump);
    }
  }

  @Test
  void sameNamedTypesAreBothKept() {
    final FileData fileData =
        parse(
            """
            class Twin { func first() {} }
            class Twin { func second() {} }
            """);

    assertEquals(2, fileData.getClassesCount());
    assertEquals(List.of("first"), functionNames(fileData.getClasses(0)));
    assertEquals(List.of("second"), functionNames(fileData.getClasses(1)));
    assertEquals(2.0, fileData.getMetricsMap().get(CommonFileDataListener.CLASS_COUNT));
  }
}
