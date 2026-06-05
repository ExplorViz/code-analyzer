package net.explorviz.code.analysis.handler;

import java.util.stream.Stream;
import net.explorviz.code.analysis.types.FileDescriptor;
import net.explorviz.code.proto.Language;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FallbackFileDataHandlerFactoryTest {

  private static final String CONTENT = "line one\nline two\n";

  static Stream<Arguments> supportedProgrammingLanguages() {
    return Stream.of(
        Arguments.of("App.ts", Language.TYPESCRIPT),
        Arguments.of("App.tsx", Language.TYPESCRIPT),
        Arguments.of("App.js", Language.JAVASCRIPT),
        Arguments.of("App.jsx", Language.JAVASCRIPT),
        Arguments.of("Main.java", Language.JAVA),
        Arguments.of("script.py", Language.PYTHON),
        Arguments.of("main.go", Language.GO),
        Arguments.of("Program.cs", Language.CSHARP),
        Arguments.of("lib.rs", Language.RUST),
        Arguments.of("Main.kt", Language.KOTLIN),
        Arguments.of("build.gradle.kts", Language.KOTLIN),
        Arguments.of("index.php", Language.PHP),
        Arguments.of("View.swift", Language.SWIFT),
        Arguments.of("trace_stack.c", Language.C),
        Arguments.of("kernel/trace/trace_stack.h", Language.C),
        Arguments.of("main.cpp", Language.CPP),
        Arguments.of("main.cxx", Language.CPP),
        Arguments.of("main.cc", Language.CPP),
        Arguments.of("types.hpp", Language.CPP),
        Arguments.of("legacy.hxx", Language.CPP));
  }

  @ParameterizedTest
  @MethodSource("supportedProgrammingLanguages")
  void createsFallbackWithLanguageLocAndSize(final String filePath, final Language language) {
    final FileDescriptor file = new FileDescriptor(ObjectId.zeroId(), filePath,
        filePath);

    final TextFileDataHandler handler = FallbackFileDataHandlerFactory.create(file, CONTENT,
        language);

    Assertions.assertEquals(language, handler.getProtoBufObject().getLanguage());
    Assertions.assertEquals("2.0", handler.getMetricValue("loc"));
    Assertions.assertEquals(String.valueOf((double) CONTENT.length()), handler.getMetricValue("size"));
  }

  @ParameterizedTest
  @MethodSource("supportedProgrammingLanguages")
  void infersLanguageFromFilePath(final String filePath, final Language language) {
    final FileDescriptor file = new FileDescriptor(ObjectId.zeroId(), filePath,
        filePath);

    final TextFileDataHandler handler = FallbackFileDataHandlerFactory.create(file, CONTENT);

    Assertions.assertEquals(language, handler.getProtoBufObject().getLanguage());
    Assertions.assertEquals("2.0", handler.getMetricValue("loc"));
    Assertions.assertEquals(String.valueOf((double) CONTENT.length()), handler.getMetricValue("size"));
  }
}
