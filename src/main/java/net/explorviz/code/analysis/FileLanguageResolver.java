package net.explorviz.code.analysis;

import java.util.Locale;
import net.explorviz.code.proto.Language;

/**
 * Resolves the programming language of a source file from its name/extension.
 */
public final class FileLanguageResolver {

  private FileLanguageResolver() {}

  /**
   * Determines the language for a file based on its extension.
   *
   * @param fileNames the file name or path
   * @return the detected language, or {@link Language#LANGUAGE_UNSPECIFIED} if unknown
   */
  public static Language resolveFromFileName(final String... fileNames) {
    if (fileNames == null) {
      return Language.LANGUAGE_UNSPECIFIED;
    }

    for (final String fileName : fileNames) {
      final Language language = resolveFromSingleFileName(fileName);
      if (language != Language.LANGUAGE_UNSPECIFIED) {
        return language;
      }
    }

    return Language.LANGUAGE_UNSPECIFIED;
  }

  private static Language resolveFromSingleFileName(final String fileName) {
    if (fileName == null) {
      return Language.LANGUAGE_UNSPECIFIED;
    }

    final String lower = fileName.toLowerCase(Locale.ROOT);

    if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
      return Language.TYPESCRIPT;
    }
    if (lower.endsWith(".js") || lower.endsWith(".jsx")) {
      return Language.JAVASCRIPT;
    }
    if (lower.endsWith(".java")) {
      return Language.JAVA;
    }
    if (lower.endsWith(".py")) {
      return Language.PYTHON;
    }
    if (lower.endsWith(".go")) {
      return Language.GO;
    }
    if (lower.endsWith(".cs")) {
      return Language.CSHARP;
    }
    if (lower.endsWith(".rs")) {
      return Language.RUST;
    }
    if (lower.endsWith(".kt") || lower.endsWith(".kts")) {
      return Language.KOTLIN;
    }
    if (lower.endsWith(".php")) {
      return Language.PHP;
    }
    if (lower.endsWith(".swift")) {
      return Language.SWIFT;
    }
    if (lower.endsWith(".c") || lower.endsWith(".h")) {
      return Language.C;
    }
    if (lower.endsWith(".cpp") || lower.endsWith(".cxx") || lower.endsWith(".cc")
        || lower.endsWith(".hpp") || lower.endsWith(".hxx")) {
      return Language.CPP;
    }

    return Language.LANGUAGE_UNSPECIFIED;
  }
}
