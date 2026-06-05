package net.explorviz.code.analysis;

import java.util.stream.Stream;
import net.explorviz.code.proto.Language;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FileLanguageResolverTest {

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
  void resolvesSupportedLanguagesFromExtension(final String filePath, final Language language) {
    Assertions.assertEquals(language, FileLanguageResolver.resolveFromFileName(filePath));
  }

  @Test
  void prefersFileNameOverReportedPathWhenBothProvided() {
    Assertions.assertEquals(Language.JAVA,
        FileLanguageResolver.resolveFromFileName("Main.java", "src/unknown.bin"));
  }

  @Test
  void fallsBackToReportedPathWhenFileNameHasNoExtension() {
    Assertions.assertEquals(Language.PYTHON,
        FileLanguageResolver.resolveFromFileName("script", "src/script.py"));
  }

  @Test
  void returnsUnspecifiedForUnknownExtension() {
    Assertions.assertEquals(Language.LANGUAGE_UNSPECIFIED,
        FileLanguageResolver.resolveFromFileName("archive.zip"));
    Assertions.assertEquals(Language.LANGUAGE_UNSPECIFIED,
        FileLanguageResolver.resolveFromFileName((String[]) null));
  }
}
