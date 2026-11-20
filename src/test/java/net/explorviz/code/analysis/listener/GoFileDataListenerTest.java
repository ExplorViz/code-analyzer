package net.explorviz.code.analysis.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.explorviz.code.analysis.handler.GoFileDataHandler;
import net.explorviz.code.analysis.parser.AntlrGoParserService;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.FunctionData;
import org.junit.jupiter.api.Test;

public class GoFileDataListenerTest {

  private static FileData parse(final String code) {
    final GoFileDataHandler handler =
        new AntlrGoParserService().parseFileContent(code, "server.go", "hash");
    assertNotNull(handler, "Parser should return a handler");
    return handler.getProtoBufObject();
  }

  private static ClassData findClass(final FileData fileData, final String name) {
    return fileData.getClassesList().stream()
        .filter(c -> c.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static FunctionData findFunction(final List<FunctionData> functions, final String name) {
    return functions.stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
  }

  private static List<String> describe(final FunctionData function) {
    return function.getParametersList().stream().map(p -> p.getName() + ":" + p.getType()).toList();
  }

  @Test
  public void testImportAliasesDoNotChangePackageName() {
    final FileData fileData =
        parse(
            """
            package server

            import (
            	_ "embed"
            	pb "example.com/x/proto"
            )
            import . "fmt"
            """);

    assertEquals("server", fileData.getPackageName());
    assertEquals(List.of("embed", "example.com/x/proto", "fmt"), fileData.getImportNamesList());
  }

  @Test
  public void testMethodDeclaredAboveItsType() {
    final FileData fileData =
        parse(
            """
            package server

            func (s *Server) Start() error {
            	return nil
            }

            type Server struct {
            	port int
            }

            func (s Server) Port() int {
            	return s.port
            }
            """);

    final ClassData server = findClass(fileData, "Server");
    assertEquals(
        List.of("Start", "Port"),
        server.getFunctionsList().stream().map(FunctionData::getName).toList());
    final FunctionData start = server.getFunctions(0);
    assertEquals(3, start.getStartLine());
    assertEquals(5, start.getEndLine());
    assertEquals(0, fileData.getFunctionsCount());
    assertEquals(2.0, fileData.getMetricsOrThrow(CommonFileDataListener.FUNCTION_COUNT));
  }

  @Test
  public void testMethodOfTypeInAnotherFileIsExportedAsQualifiedFunction() {
    final FileData fileData =
        parse(
            """
            package server

            func (s *Server) Stop(force bool) error {
            	return nil
            }
            """);

    assertEquals(0, fileData.getClassesCount());
    assertEquals(1, fileData.getFunctionsCount());
    final FunctionData stop = fileData.getFunctions(0);
    assertEquals("Server.Stop", stop.getName());
    assertEquals("error", stop.getReturnType());
    assertEquals(List.of("force:bool"), describe(stop));
    assertEquals(3, stop.getStartLine());
    assertEquals(5, stop.getEndLine());
  }

  @Test
  public void testParametersSharingATypeAndVariadicParameters() {
    final FileData fileData =
        parse(
            """
            package server

            type Calc struct{}

            func add(a, b int, s string) int {
            	return a + b + len(s)
            }

            func (c Calc) sum(prefix string, values ...int) int {
            	return len(values)
            }

            func unnamed(int, string) {}
            """);

    assertEquals(
        List.of("a:int", "b:int", "s:string"),
        describe(findFunction(fileData.getFunctionsList(), "add")));
    assertEquals(
        List.of(":int", ":string"), describe(findFunction(fileData.getFunctionsList(), "unnamed")));
    assertEquals(
        List.of("prefix:string", "values:...int"),
        describe(findFunction(findClass(fileData, "Calc").getFunctionsList(), "sum")));
  }
}
