package net.explorviz.code.analysis.antlr.generated;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;

/**
 * Base class for the Java parser that provides semantic predicates used by the
 * grammar.
 */
public abstract class JavaParserBase extends Parser {
  protected JavaParserBase(final TokenStream input) {
    super(input);
  }

  public boolean doLastRecordComponent() {
    final ParserRuleContext ctx = getContext();
    if (!(ctx instanceof JavaParser.RecordComponentListContext recordComponentListContext)) {
      return true;
    }

    final var recordComponents = recordComponentListContext.recordComponent();
    if (recordComponents.isEmpty()) {
      return true;
    }

    final int count = recordComponents.size();
    for (int index = 0; index < count; ++index) {
      final var recordComponent = recordComponents.get(index);
      if (recordComponent.ELLIPSIS() != null && index + 1 < count) {
        return false;
      }
    }
    return true;
  }

  public boolean isNotIdentifierAssign() {
    final int nextTokenType = getTokenStream().LA(1);
    switch (nextTokenType) {
      case JavaParser.IDENTIFIER:
      case JavaParser.MODULE:
      case JavaParser.OPEN:
      case JavaParser.REQUIRES:
      case JavaParser.EXPORTS:
      case JavaParser.OPENS:
      case JavaParser.TO:
      case JavaParser.USES:
      case JavaParser.PROVIDES:
      case JavaParser.WHEN:
      case JavaParser.WITH:
      case JavaParser.TRANSITIVE:
      case JavaParser.YIELD:
      case JavaParser.SEALED:
      case JavaParser.PERMITS:
      case JavaParser.RECORD:
      case JavaParser.VAR:
        break;
      default:
        return true;
    }
    return getTokenStream().LA(2) != JavaParser.ASSIGN;
  }
}
