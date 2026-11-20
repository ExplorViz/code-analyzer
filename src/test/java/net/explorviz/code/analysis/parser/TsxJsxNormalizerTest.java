package net.explorviz.code.analysis.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TsxJsxNormalizerTest {

  private static String normalize(final String source) {
    return TsxJsxNormalizer.replaceJsxWithNull(source);
  }

  private static long lineBreaks(final String text) {
    return text.chars().filter(c -> c == '\n').count();
  }

  @Test
  void singleLineElementBecomesNull() {
    assertEquals("const a = null;", normalize("const a = <div className=\"x\">Hi</div>;"));
    assertEquals("return null;", normalize("return <Foo bar={1} />;"));
    assertEquals("const f = null;", normalize("const f = <><b>x</b></>;"));
  }

  @Test
  void multiLineElementKeepsLineStructure() {
    final String source =
        """
        return (
          <div>

            <span>{label}</span>
          </div>
        );
        next();
        """;
    final String normalized = normalize(source);
    assertEquals(lineBreaks(source), lineBreaks(normalized));
    assertEquals(
        """
        return (
          (null

        ,null
        ,null)
        );
        next();
        """,
        normalized);
  }

  @Test
  void quotesInJsxTextArePlainCharacters() {
    final String source = "const a = <p>Don't panic, it's \"fine\"</p>;\nfunction later() {}\n";
    assertEquals("const a = null;\nfunction later() {}\n", normalize(source));
  }

  @Test
  void commentsAndStringsInsideExpressionContainersAreSkipped() {
    final String source =
        """
        const a = <p>
          {/* don't */}
          {
            // won't
            'it\\'s' + `t ${"}"}`
          }
        </p>;
        function later() {}
        """;
    final String normalized = normalize(source);
    assertEquals(lineBreaks(source), lineBreaks(normalized));
    assertEquals(true, normalized.startsWith("const a = (null\n"));
    assertEquals(true, normalized.endsWith(");\nfunction later() {}\n"));
  }

  @Test
  void nestedJsxInExpressionsIsSkipped() {
    assertEquals(
        "const l = null;",
        normalize("const l = <ul>{items.map(i => <li key={i}>Don't {i}</li>)}</ul>;"));
    assertEquals(
        "items.map(item => null);", normalize("items.map(item => <li key={item}>{item}</li>);"));
  }

  @Test
  void genericArrowFunctionsAreNotJsx() {
    final String handle =
        "class S { handle = <K extends keyof S>(key: K) => {\n  return key;\n}; }";
    assertEquals(handle, normalize(handle));
    final String defaulted = "const d = <T = string>(x: T) => x;";
    assertEquals(defaulted, normalize(defaulted));
    final String multi = "const p = <A, B>(a: A, b: B) => a;";
    assertEquals(multi, normalize(multi));
  }

  @Test
  void trailingCommaInTypeParametersIsBlanked() {
    // The grammar has no trailing comma in type parameter lists; replace it keeping columns.
    assertEquals("const id = <T >(x: T) => x;", normalize("const id = <T,>(x: T) => x;"));
  }

  @Test
  void genericFunctionTypeWithoutClosingTagIsLeftUnchanged() {
    final String source =
        "type Fn = <T>(x: T) => T;\nfunction after(a: number) { return a < 2; }\n";
    assertEquals(source, normalize(source));
  }

  @Test
  void commentsStringsRegexAndTemplatesAreNeverTouched() {
    final String source =
        """
        // TODO: <name>
        /* see <div> */
        const usage = "Usage: <file>";
        const single = 'x = <b>';
        const re = /<div>/g;
        const t = `<b>${usage}</b>`;
        function after() { return <i>ok</i>; }
        """;
    final String expected = source.replace("return <i>ok</i>;", "return null;");
    assertEquals(expected, normalize(source));
  }

  @Test
  void comparisonsAndDivisionAreNotJsxOrRegex() {
    final String source = "if (a <b && c > d) { x = a / b / c; }\nconst y = z <w;\n";
    assertEquals(source, normalize(source));
  }

  @Test
  void unterminatedElementLeavesSourceUnchanged() {
    final String source = "const a = <div>\nfunction stillHere() { return 1; }\n";
    assertEquals(source, normalize(source));
  }

  @Test
  void mismatchedClosingTagLeavesSourceUnchanged() {
    final String source = "const a = <div><span></div>;\n";
    assertEquals(source, normalize(source));
  }

  @Test
  void typeArgumentsOnJsxTagsAreSupported() {
    assertEquals("const t = null;", normalize("const t = <DataTable<SubjectDto> rows={rows} />;"));
  }

  @Test
  void manyGenericCandidatesStayFast() {
    final StringBuilder source = new StringBuilder();
    for (int i = 0; i < 3000; i++) {
      source.append("type F").append(i).append(" = <T>(x: T) => T;\n");
    }
    final String input = source.toString();
    assertTimeoutPreemptively(Duration.ofSeconds(20), () -> assertEquals(input, normalize(input)));
  }
}
