package net.explorviz.code.analysis.listener;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommonFileDataListenerTest {

  private final CommonFileDataListener listener = new CommonFileDataListener() {};

  @Test
  void unqualifiedNameResolvesNextToTheCurrentFile() {
    Assertions.assertEquals(
        "src/app/models/Base.ts",
        listener.getClassPathFromFqn("Base", ".ts", "src/app/models/user.ts", "src/app/models"));
    Assertions.assertEquals(
        "src/main/kotlin/com/foo/Base.kt",
        listener.getClassPathFromFqn("Base<T>", ".kt", "src/main/kotlin/com/foo/A.kt", "com.foo"));
    Assertions.assertEquals(
        "lib/Base.py", listener.getClassPathFromFqn("Base", ".py", "lib/child.py", ""));
    Assertions.assertEquals("Base.py", listener.getClassPathFromFqn("Base", ".py", "child.py", ""));
  }

  @Test
  void qualifiedNameResolvesRelativeToTheSourceRoot() {
    Assertions.assertEquals(
        "src/main/java/com/bar/Base.java",
        listener.getClassPathFromFqn(
            "com.bar.Base", ".java", "src/main/java/com/foo/A.java", "com.foo"));
    Assertions.assertEquals(
        "pkg/mod/Base.py", listener.getClassPathFromFqn("mod.Base", ".py", "pkg/child.py", ""));
  }
}
