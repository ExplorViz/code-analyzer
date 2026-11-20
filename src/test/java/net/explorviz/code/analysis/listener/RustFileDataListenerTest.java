package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.RustFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrRustParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;
import org.junit.jupiter.api.Test;

public class RustFileDataListenerTest {

  private static FileData parse(final String code) {
    final RustFileDataHandler handler =
        new AntlrRustParserService().parseFileContent(code, "test.rs", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static List<String> functionNames(final List<FunctionData> functions) {
    return functions.stream().map(FunctionData::getName).toList();
  }

  @Test
  public void keepsTypesWithTheSameName() {
    final FileData fileData =
        parse(
            """
            #[cfg(unix)]
            struct Handle { fd: i32 }
            #[cfg(windows)]
            struct Handle { h: usize }
            """);

    assertEquals(2, fileData.getClassesCount());
    assertEquals("Handle", fileData.getClasses(0).getName());
    assertEquals("fd", fileData.getClasses(0).getFields(0).getName());
    assertEquals("Handle", fileData.getClasses(1).getName());
    assertEquals("h", fileData.getClasses(1).getFields(0).getName());
  }

  @Test
  public void attachesImplsOnGenericTypesRegardlessOfOrder() {
    final FileData fileData =
        parse(
            """
            fn free() {}
            impl<T> Wrapper<T> {
                fn new(value: T) -> Self { Wrapper { value } }
                fn get(&self) -> &T { &self.value }
            }
            struct Wrapper<T> { value: T }
            impl<T> std::fmt::Display for Wrapper<T> {
                fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result { Ok(()) }
            }
            impl<T> std::fmt::Debug for &Wrapper<T> {
                fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result { Ok(()) }
            }
            impl Other for Vec<u8> { fn other(&self) {} }
            fn last() {}
            """);

    final ClassData wrapper = fileData.getClasses(0);
    assertEquals("Wrapper", wrapper.getName());
    assertEquals(List.of("new", "get", "fmt", "fmt"), functionNames(wrapper.getFunctionsList()));
    assertEquals(
        List.of("std::fmt::Display", "std::fmt::Debug"), wrapper.getImplementedInterfacesList());
    assertEquals(List.of("free", "other", "last"), functionNames(fileData.getFunctionsList()));
    assertEquals(7.0, fileData.getMetricsOrThrow("functionCount"));
  }

  @Test
  public void separatesParameterNamesAndTypes() {
    final FileData fileData =
        parse("fn f(mut b: u8, ref r: &'a str, (x, y): (i32, i32), out: &mut dyn Write) {}");

    final List<ParameterData> parameters = fileData.getFunctions(0).getParametersList();
    assertEquals("b", parameters.get(0).getName());
    assertEquals("u8", parameters.get(0).getType());
    assertEquals(List.of("mut"), parameters.get(0).getModifiersList());
    assertEquals("r", parameters.get(1).getName());
    assertEquals("&'a str", parameters.get(1).getType());
    assertEquals(List.of("ref"), parameters.get(1).getModifiersList());
    assertEquals("(x,y)", parameters.get(2).getName());
    assertEquals("(i32,i32)", parameters.get(2).getType());
    assertEquals("out", parameters.get(3).getName());
    assertEquals("&mut dyn Write", parameters.get(3).getType());
    assertEquals(0, parameters.get(3).getModifiersCount());
  }

  @Test
  public void keepsModuleAsPackage() {
    assertEquals("a::b", parse("mod a { mod b { struct S {} } }").getPackageName());
    assertEquals("a", parse("mod a { struct S {} } mod c {}").getPackageName());
    assertEquals("a", parse("mod a {}").getPackageName());
    assertFalse(parse("struct S {} mod tests { fn t() {} }").hasPackageName());
    assertFalse(parse("mod a; mod b;").hasPackageName());
  }
}
