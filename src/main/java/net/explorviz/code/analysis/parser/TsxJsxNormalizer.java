package net.explorviz.code.analysis.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Replaces JSX elements in TSX/JSX source with {@code null} so the TypeScript grammar can parse
 * files without expensive JSX disambiguation. Suffices for structural code analysis.
 *
 * <p>The normalizer is a small lexer-aware scanner: comments, string literals, template literals
 * and regular expression literals are copied verbatim and never inspected for JSX. A {@code <} is
 * only considered as a JSX start in expression position, TypeScript generic parameter lists (e.g.
 * {@code <T,>(x: T) => x}) are not treated as JSX, and an element whose end cannot be found is left
 * untouched. Replacements keep the line structure of the replaced element so that line numbers and
 * SLOC of the surrounding code stay correct: a single-line element becomes {@code null}, a
 * multi-line element becomes a parenthesised comma expression with one {@code null} per non-blank
 * line, e.g. {@code (null\n,null\n,null)}.
 */
public final class TsxJsxNormalizer {

  private static final int FAILED = -1;

  /** Guards the recursive descent against stack overflows on pathological input. */
  private static final int MAX_NESTING_DEPTH = 200;

  /** Characters after which a {@code <} or {@code /} starts an expression operand. */
  private static final String EXPRESSION_START_PUNCTUATORS = "=({[,;:?&|!~+-*%^<>";

  /** Keywords after which a {@code <} starts a JSX element. */
  private static final Set<String> JSX_PRECEDING_KEYWORDS =
      Set.of("return", "throw", "yield", "await", "default", "case");

  /** Keywords after which a {@code /} starts a regular expression literal. */
  private static final Set<String> REGEX_PRECEDING_KEYWORDS =
      Set.of(
          "return",
          "throw",
          "yield",
          "await",
          "default",
          "case",
          "typeof",
          "instanceof",
          "in",
          "of",
          "new",
          "delete",
          "void",
          "do",
          "else");

  private final String source;
  private final int length;
  private final Map<Integer, Integer> elementEndCache = new HashMap<>();
  private int nestingDepth;

  private TsxJsxNormalizer(final String source) {
    this.source = source;
    this.length = source.length();
  }

  public static String replaceJsxWithNull(final String source) {
    if (source == null || source.indexOf('<') < 0) {
      return source;
    }
    return new TsxJsxNormalizer(source).normalize();
  }

  private String normalize() {
    final StringBuilder out = new StringBuilder(length);
    int copyFrom = 0;
    int index = 0;
    int prevSignificant = -1;
    while (index < length) {
      final char c = source.charAt(index);
      if (Character.isWhitespace(c)) {
        index++;
        continue;
      }
      final int skipped = skipNonCodeToken(index, prevSignificant);
      if (skipped != FAILED) {
        if (!isComment(index)) {
          prevSignificant = skipped - 1;
        }
        index = skipped;
        continue;
      }
      if (c == '<' && isJsxCandidate(index, prevSignificant)) {
        final int end = skipJsxElement(index);
        if (end != FAILED) {
          out.append(source, copyFrom, index);
          appendReplacement(out, index, end);
          copyFrom = end;
          index = end;
          prevSignificant = end - 1;
          continue;
        }
      }
      if (c == '<') {
        final int comma = trailingTypeParameterComma(index);
        if (comma >= 0) {
          // `<T,>` is the TSX spelling of a generic arrow; the grammar has no trailing comma.
          out.append(source, copyFrom, comma).append(' ');
          copyFrom = comma + 1;
        }
      }
      prevSignificant = index;
      index++;
    }
    out.append(source, copyFrom, length);
    return out.toString();
  }

  /** Returns the index of the comma in {@code <T,>}, or -1 if {@code ltIndex} is not that form. */
  private int trailingTypeParameterComma(final int ltIndex) {
    if (ltIndex + 1 >= length || !Character.isJavaIdentifierStart(source.charAt(ltIndex + 1))) {
      return -1;
    }
    final int comma = skipWhitespace(skipIdentifier(ltIndex + 1));
    if (comma >= length || source.charAt(comma) != ',') {
      return -1;
    }
    final int close = skipWhitespace(comma + 1);
    return close < length && source.charAt(close) == '>' ? comma : -1;
  }

  /** Emits {@code null} for the range, keeping every line break and one token per code line. */
  private void appendReplacement(final StringBuilder out, final int start, final int end) {
    final int firstLineBreak = indexOfLineBreak(start, end);
    if (firstLineBreak < 0) {
      out.append("null");
      return;
    }
    out.append("(null");
    boolean lineHasContent = false;
    for (int i = firstLineBreak; i < end; i++) {
      final char c = source.charAt(i);
      if (c == '\n' || c == '\r') {
        out.append(c);
        lineHasContent = false;
      } else if (!lineHasContent && !Character.isWhitespace(c)) {
        out.append(",null");
        lineHasContent = true;
      }
    }
    out.append(')');
  }

  private int indexOfLineBreak(final int start, final int end) {
    for (int i = start; i < end; i++) {
      final char c = source.charAt(i);
      if (c == '\n' || c == '\r') {
        return i;
      }
    }
    return -1;
  }

  // ---------------------------------------------------------------------------------------------
  // Code scanning
  // ---------------------------------------------------------------------------------------------

  /**
   * If {@code index} starts a comment, string, template or regex literal, returns the index after
   * it; otherwise {@link #FAILED}.
   */
  private int skipNonCodeToken(final int index, final int prevSignificant) {
    final char c = source.charAt(index);
    final char next = index + 1 < length ? source.charAt(index + 1) : '\0';
    if (c == '/' && next == '/') {
      return skipLineComment(index);
    }
    if (c == '/' && next == '*') {
      return skipBlockComment(index);
    }
    if (c == '"' || c == '\'') {
      return skipStringLiteral(index);
    }
    if (c == '`') {
      return skipTemplate(index);
    }
    if (c == '/' && isRegexAllowed(prevSignificant)) {
      return skipRegex(index);
    }
    return FAILED;
  }

  private boolean isComment(final int index) {
    return source.charAt(index) == '/'
        && index + 1 < length
        && (source.charAt(index + 1) == '/' || source.charAt(index + 1) == '*');
  }

  /**
   * Skips code starting right after an opening {@code {} up to and including the matching {@code
   * }}. Strings, templates, comments, regex literals and nested JSX are handled.
   *
   * @return the index after the closing brace, or {@link #FAILED} if it does not exist
   */
  private int skipCodeBlock(final int from) {
    int depth = 1;
    int index = from;
    int prevSignificant = from - 1;
    while (index < length) {
      final char c = source.charAt(index);
      if (Character.isWhitespace(c)) {
        index++;
        continue;
      }
      final int skipped = skipNonCodeToken(index, prevSignificant);
      if (skipped != FAILED) {
        if (!isComment(index)) {
          prevSignificant = skipped - 1;
        }
        index = skipped;
        continue;
      }
      if (c == '<' && isJsxCandidate(index, prevSignificant)) {
        final int end = skipJsxElement(index);
        if (end != FAILED) {
          prevSignificant = end - 1;
          index = end;
          continue;
        }
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return index + 1;
        }
      }
      prevSignificant = index;
      index++;
    }
    return FAILED;
  }

  private int skipLineComment(final int start) {
    int index = start + 2;
    while (index < length && source.charAt(index) != '\n' && source.charAt(index) != '\r') {
      index++;
    }
    return index;
  }

  private int skipBlockComment(final int start) {
    final int end = source.indexOf("*/", start + 2);
    return end < 0 ? length : end + 2;
  }

  /** Skips a JS string literal; an unescaped line break terminates it (unterminated literal). */
  private int skipStringLiteral(final int start) {
    final char quote = source.charAt(start);
    int index = start + 1;
    while (index < length) {
      final char c = source.charAt(index);
      if (c == '\\') {
        index += 2;
      } else if (c == quote) {
        return index + 1;
      } else if (c == '\n' || c == '\r') {
        return index;
      } else {
        index++;
      }
    }
    return length;
  }

  private int skipTemplate(final int start) {
    int index = start + 1;
    while (index < length) {
      final char c = source.charAt(index);
      if (c == '\\') {
        index += 2;
      } else if (c == '`') {
        return index + 1;
      } else if (c == '$' && index + 1 < length && source.charAt(index + 1) == '{') {
        final int end = skipCodeBlock(index + 2);
        if (end == FAILED) {
          return length;
        }
        index = end;
      } else {
        index++;
      }
    }
    return length;
  }

  /** Skips a regex literal, or returns {@link #FAILED} if none ends on the same line. */
  private int skipRegex(final int start) {
    int index = start + 1;
    boolean inClass = false;
    while (index < length) {
      final char c = source.charAt(index);
      if (c == '\n' || c == '\r') {
        return FAILED;
      }
      if (c == '\\') {
        index += 2;
        continue;
      }
      if (c == '[') {
        inClass = true;
      } else if (c == ']') {
        inClass = false;
      } else if (c == '/' && !inClass) {
        index++;
        while (index < length && Character.isJavaIdentifierPart(source.charAt(index))) {
          index++;
        }
        return index;
      }
      index++;
    }
    return FAILED;
  }

  private boolean isRegexAllowed(final int prevSignificant) {
    if (prevSignificant < 0) {
      return true;
    }
    final char previous = source.charAt(prevSignificant);
    if (EXPRESSION_START_PUNCTUATORS.indexOf(previous) >= 0 || previous == '}') {
      return true;
    }
    return REGEX_PRECEDING_KEYWORDS.contains(wordEndingAt(prevSignificant));
  }

  /** Returns the identifier ending at {@code index}, or an empty string. */
  private String wordEndingAt(final int index) {
    if (!Character.isJavaIdentifierPart(source.charAt(index))) {
      return "";
    }
    int start = index;
    while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) {
      start--;
    }
    if (start > 0 && source.charAt(start - 1) == '.') {
      return "";
    }
    return source.substring(start, index + 1);
  }

  // ---------------------------------------------------------------------------------------------
  // JSX detection
  // ---------------------------------------------------------------------------------------------

  private boolean isJsxCandidate(final int ltIndex, final int prevSignificant) {
    if (ltIndex + 1 >= length || prevSignificant < 0) {
      return false;
    }
    final char first = source.charAt(ltIndex + 1);
    if (first != '>' && !Character.isJavaIdentifierStart(first)) {
      return false;
    }
    if (!hasJsxPredecessor(prevSignificant)) {
      return false;
    }
    return first == '>' || !looksLikeTypeParameters(ltIndex);
  }

  private boolean hasJsxPredecessor(final int prevSignificant) {
    final char previous = source.charAt(prevSignificant);
    if (previous == '<') {
      // `<<` is a shift operator, not JSX.
      return false;
    }
    if (EXPRESSION_START_PUNCTUATORS.indexOf(previous) >= 0) {
      return true;
    }
    return JSX_PRECEDING_KEYWORDS.contains(wordEndingAt(prevSignificant));
  }

  /**
   * Detects TypeScript generic parameter lists that cannot be JSX: {@code <T,>}, {@code <T extends
   * U>} and {@code <T = U>}.
   */
  private boolean looksLikeTypeParameters(final int ltIndex) {
    int index = skipIdentifier(ltIndex + 1);
    final int afterName = index;
    index = skipWhitespace(index);
    if (index >= length) {
      return false;
    }
    final char c = source.charAt(index);
    if (c == ',' || (c == '=' && (index + 1 >= length || source.charAt(index + 1) != '='))) {
      return true;
    }
    if (index > afterName && source.startsWith("extends", index)) {
      final int afterKeyword = index + "extends".length();
      return afterKeyword < length && Character.isWhitespace(source.charAt(afterKeyword));
    }
    return false;
  }

  /**
   * Skips a JSX element or fragment starting at {@code ltIndex}.
   *
   * @return the index after the element, or {@link #FAILED} if it is not a complete JSX element
   */
  private int skipJsxElement(final int ltIndex) {
    final Integer cached = elementEndCache.get(ltIndex);
    if (cached != null) {
      return cached;
    }
    if (nestingDepth >= MAX_NESTING_DEPTH) {
      // Not cached: the result depends on the depth at which the element is reached.
      return FAILED;
    }
    nestingDepth++;
    try {
      final int end = scanJsxElement(ltIndex);
      elementEndCache.put(ltIndex, end);
      return end;
    } finally {
      nestingDepth--;
    }
  }

  private int scanJsxElement(final int ltIndex) {
    int index = ltIndex + 1;
    if (index < length && source.charAt(index) == '>') {
      return skipChildren(index + 1, "");
    }
    final int nameEnd = skipTagName(index);
    if (nameEnd == index) {
      return FAILED;
    }
    final String tagName = source.substring(index, nameEnd);
    index = skipJsxTrivia(nameEnd);
    if (index < length && source.charAt(index) == '<') {
      index = skipTypeArguments(index);
      if (index == FAILED) {
        return FAILED;
      }
    }
    while (true) {
      index = skipJsxTrivia(index);
      if (index >= length) {
        return FAILED;
      }
      final char c = source.charAt(index);
      if (c == '/') {
        return index + 1 < length && source.charAt(index + 1) == '>' ? index + 2 : FAILED;
      }
      if (c == '>') {
        return skipChildren(index + 1, tagName);
      }
      if (c == '{') {
        index = skipCodeBlock(index + 1);
      } else if (Character.isJavaIdentifierStart(c)) {
        index = skipAttribute(index);
      } else {
        return FAILED;
      }
      if (index == FAILED) {
        return FAILED;
      }
    }
  }

  private int skipAttribute(final int start) {
    int index = skipTagName(start);
    final int afterName = skipJsxTrivia(index);
    if (afterName >= length || source.charAt(afterName) != '=') {
      return index;
    }
    index = skipJsxTrivia(afterName + 1);
    if (index >= length) {
      return FAILED;
    }
    final char c = source.charAt(index);
    if (c == '"' || c == '\'') {
      // JSX attribute strings have no escape sequences and may span lines.
      final int close = source.indexOf(c, index + 1);
      return close < 0 ? FAILED : close + 1;
    }
    if (c == '{') {
      return skipCodeBlock(index + 1);
    }
    if (c == '<') {
      return skipJsxElement(index);
    }
    return FAILED;
  }

  /**
   * Skips JSX children (text, expression containers and nested elements) up to and including the
   * closing tag of {@code tagName} ({@code ""} for a fragment). Quotes in JSX text are plain
   * characters.
   */
  private int skipChildren(final int from, final String tagName) {
    int index = from;
    while (index < length) {
      final char c = source.charAt(index);
      if (c == '<') {
        final int afterLt = skipJsxTrivia(index + 1);
        if (afterLt < length && source.charAt(afterLt) == '/') {
          return skipClosingTag(afterLt + 1, tagName);
        }
        index = skipJsxElement(index);
        if (index == FAILED) {
          return FAILED;
        }
      } else if (c == '{') {
        index = skipCodeBlock(index + 1);
        if (index == FAILED) {
          return FAILED;
        }
      } else {
        index++;
      }
    }
    return FAILED;
  }

  private int skipClosingTag(final int from, final String tagName) {
    int index = skipJsxTrivia(from);
    final int nameEnd = skipTagName(index);
    if (!tagName.equals(source.substring(index, nameEnd))) {
      return FAILED;
    }
    index = skipJsxTrivia(nameEnd);
    return index < length && source.charAt(index) == '>' ? index + 1 : FAILED;
  }

  /** Skips TypeScript type arguments on a JSX tag, e.g. {@code <DataTable<SubjectDto>}. */
  private int skipTypeArguments(final int start) {
    int depth = 0;
    int index = start;
    while (index < length) {
      final char c = source.charAt(index);
      if (c == '"' || c == '\'') {
        index = skipStringLiteral(index);
        continue;
      }
      if (c == '=' && index + 1 < length && source.charAt(index + 1) == '>') {
        index += 2;
        continue;
      }
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
        if (depth == 0) {
          return index + 1;
        }
      } else if (c == ';' || c == '}') {
        return FAILED;
      }
      index++;
    }
    return FAILED;
  }

  private int skipTagName(final int from) {
    int index = from;
    while (index < length) {
      final char c = source.charAt(index);
      if (Character.isJavaIdentifierPart(c) || c == '.' || c == '-' || c == ':') {
        index++;
      } else {
        break;
      }
    }
    return index;
  }

  private int skipIdentifier(final int from) {
    int index = from;
    while (index < length && Character.isJavaIdentifierPart(source.charAt(index))) {
      index++;
    }
    return index;
  }

  private int skipWhitespace(final int from) {
    int index = from;
    while (index < length && Character.isWhitespace(source.charAt(index))) {
      index++;
    }
    return index;
  }

  /** Skips whitespace and comments inside a JSX tag. */
  private int skipJsxTrivia(final int from) {
    int index = skipWhitespace(from);
    while (index + 1 < length && source.charAt(index) == '/') {
      final char next = source.charAt(index + 1);
      if (next == '/') {
        index = skipWhitespace(skipLineComment(index));
      } else if (next == '*') {
        index = skipWhitespace(skipBlockComment(index));
      } else {
        break;
      }
    }
    return index;
  }
}
