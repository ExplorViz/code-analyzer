package net.explorviz.code.analysis.parser;

/**
 * Replaces JSX elements in TSX source with {@code null} so the TypeScript
 * grammar can parse
 * files without expensive JSX disambiguation. Suffices for structural code
 * analysis.
 */
public final class TsxJsxNormalizer {

  private TsxJsxNormalizer() {
  }

  public static String replaceJsxWithNull(final String source) {
    final StringBuilder out = new StringBuilder(source.length());
    int index = 0;
    while (index < source.length()) {
      final int jsxStart = findJsxStart(source, index);
      if (jsxStart < 0) {
        out.append(source, index, source.length());
        break;
      }
      out.append(source, index, jsxStart);
      out.append("null");
      index = skipJsxElement(source, jsxStart);
    }
    return out.toString();
  }

  private static int findJsxStart(final String source, final int from) {
    for (int i = from; i < source.length(); i++) {
      if (source.charAt(i) == '<' && isJsxStartAt(source, i)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isJsxStartAt(final String source, final int ltIndex) {
    int next = ltIndex + 1;
    if (next >= source.length()) {
      return false;
    }
    final char first = source.charAt(next);
    if (first == '/' || first == '>') {
      return hasJsxPredecessor(source, ltIndex);
    }
    if (!Character.isJavaIdentifierStart(first)) {
      return false;
    }
    return hasJsxPredecessor(source, ltIndex);
  }

  private static boolean hasJsxPredecessor(final String source, final int ltIndex) {
    int index = ltIndex - 1;
    while (index >= 0 && Character.isWhitespace(source.charAt(index))) {
      index--;
    }
    if (index < 0) {
      return false;
    }
    final String trimmed = source.substring(0, ltIndex).trim();
    if (trimmed.endsWith("return") || trimmed.endsWith("throw")) {
      return true;
    }
    final char previous = source.charAt(index);
    if ("=({[,;:?&|+-~!*%/>".indexOf(previous) >= 0) {
      return true;
    }
    if (index > 0) {
      final String twoChars = source.substring(index - 1, index + 1);
      if ("=>".equals(twoChars) || "&&".equals(twoChars) || "||".equals(twoChars)
          || "??".equals(twoChars)) {
        return true;
      }
    }
    return false;
  }

  private static int skipJsxElement(final String source, final int start) {
    int index = start + 1;
    if (index < source.length() && source.charAt(index) == '/') {
      return findChar(source, '>', index) + 1;
    }
    if (index < source.length() && source.charAt(index) == '>') {
      return skipFragment(source, index + 1);
    }
    index = skipTagName(source, index);
    index = skipAttributes(source, index);
    if (index + 1 < source.length() && source.charAt(index) == '/' && source.charAt(index + 1) == '>') {
      return index + 2;
    }
    if (index < source.length() && source.charAt(index) == '>') {
      return skipElementBody(source, index + 1, extractTagName(source, start));
    }
    return index;
  }

  private static int skipFragment(final String source, final int from) {
    int depth = 1;
    int index = from;
    while (index < source.length() && depth > 0) {
      if (source.charAt(index) == '<') {
        if (index + 1 < source.length() && source.charAt(index + 1) == '/') {
          final int close = findChar(source, '>', index);
          if (close < 0) {
            return source.length();
          }
          index = close + 1;
          depth--;
        } else if (index + 1 < source.length() && source.charAt(index + 1) == '>') {
          index += 2;
          depth++;
        } else {
          index = skipJsxElement(source, index);
        }
      } else if (source.charAt(index) == '{') {
        index = skipBalanced(source, index, '{', '}');
      } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
        index = skipQuoted(source, index);
      } else {
        index++;
      }
    }
    return index;
  }

  private static int skipElementBody(final String source, final int from, final String tagName) {
    int index = from;
    final String closeTag = "</" + tagName;
    while (index < source.length()) {
      if (source.charAt(index) == '<') {
        if (index + 1 < source.length() && source.charAt(index + 1) == '/') {
          final int closeEnd = findChar(source, '>', index);
          if (closeEnd < 0) {
            return source.length();
          }
          final String tag = source.substring(index, closeEnd + 1);
          if (tag.startsWith(closeTag)) {
            return closeEnd + 1;
          }
        }
        index = skipJsxElement(source, index);
      } else if (source.charAt(index) == '{') {
        index = skipBalanced(source, index, '{', '}');
      } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
        index = skipQuoted(source, index);
      } else {
        index++;
      }
    }
    return index;
  }

  private static String extractTagName(final String source, final int start) {
    int index = start + 1;
    final StringBuilder name = new StringBuilder();
    while (index < source.length()) {
      final char c = source.charAt(index);
      if (Character.isJavaIdentifierPart(c) || c == '.' || c == '-') {
        name.append(c);
        index++;
      } else {
        break;
      }
    }
    return name.toString();
  }

  private static int skipTagName(final String source, final int from) {
    int index = from;
    while (index < source.length()) {
      final char c = source.charAt(index);
      if (Character.isJavaIdentifierPart(c) || c == '.' || c == '-') {
        index++;
      } else {
        break;
      }
    }
    return index;
  }

  private static int skipAttributes(final String source, int from) {
    while (from < source.length()) {
      from = skipWhitespaceForward(source, from);
      if (from >= source.length()) {
        return from;
      }
      final char c = source.charAt(from);
      if (c == '/' || c == '>') {
        return from;
      }
      if (c == '{') {
        from = skipBalanced(source, from, '{', '}');
        continue;
      }
      from = skipTagName(source, from);
      from = skipWhitespaceForward(source, from);
      if (from < source.length() && source.charAt(from) == '=') {
        from++;
        from = skipWhitespaceForward(source, from);
        if (from < source.length()) {
          final char valueStart = source.charAt(from);
          if (valueStart == '"' || valueStart == '\'') {
            from = skipQuoted(source, from);
          } else if (valueStart == '{') {
            from = skipBalanced(source, from, '{', '}');
          } else {
            from++;
          }
        }
      }
    }
    return from;
  }

  private static int skipWhitespaceForward(final String source, int from) {
    while (from < source.length() && Character.isWhitespace(source.charAt(from))) {
      from++;
    }
    return from;
  }

  private static int skipQuoted(final String source, final int start) {
    final char quote = source.charAt(start);
    int index = start + 1;
    while (index < source.length()) {
      final char c = source.charAt(index);
      if (c == '\\') {
        index += 2;
      } else if (c == quote) {
        return index + 1;
      } else {
        index++;
      }
    }
    return index;
  }

  private static int skipBalanced(final String source, final int start, final char open, final char close) {
    int depth = 0;
    int index = start;
    while (index < source.length()) {
      final char c = source.charAt(index);
      if (c == '"' || c == '\'') {
        index = skipQuoted(source, index);
        continue;
      }
      if (c == '`') {
        index = skipTemplate(source, index);
        continue;
      }
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return index + 1;
        }
      }
      index++;
    }
    return index;
  }

  private static int skipTemplate(final String source, final int start) {
    int index = start + 1;
    while (index < source.length()) {
      final char c = source.charAt(index);
      if (c == '\\') {
        index += 2;
      } else if (c == '`') {
        return index + 1;
      } else if (c == '$' && index + 1 < source.length() && source.charAt(index + 1) == '{') {
        index = skipBalanced(source, index + 1, '{', '}');
      } else {
        index++;
      }
    }
    return index;
  }

  private static int findChar(final String source, final char target, final int from) {
    for (int i = from; i < source.length(); i++) {
      if (source.charAt(i) == target) {
        return i;
      }
    }
    return -1;
  }
}
