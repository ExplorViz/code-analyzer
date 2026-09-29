package net.explorviz.code.analysis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;

/** Loads version-controlled files from {@code src/test/resources} via the test classpath. */
public final class TestResources {

  private TestResources() {}

  public static String readUtf8(final String classpathResource) throws IOException {
    try (InputStream in = TestResources.class.getResourceAsStream(classpathResource)) {
      Assertions.assertNotNull(in, "Missing classpath resource: " + classpathResource);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  public static CompilationUnit parseJavaFixture(final String classpathResource)
      throws IOException {
    return StaticJavaParser.parse(readUtf8(classpathResource));
  }
}
