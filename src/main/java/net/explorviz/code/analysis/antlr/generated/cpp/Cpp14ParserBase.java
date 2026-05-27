package net.explorviz.code.analysis.antlr.generated.cpp;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

/**
 * Base class for the CPP14 parser that provides semantic predicates.
 */
public abstract class Cpp14ParserBase extends Parser {
  protected Cpp14ParserBase(final TokenStream input) {
    super(input);
  }

  protected boolean isPureSpecifierAllowed() {
    try {
      final var x = this._ctx; // memberDeclarator
      final var c = x.getChild(0).getChild(0);
      final var c2 = c.getChild(0);
      final var p = c2.getChild(1);
      if (p == null) {
        return false;
      }
      return (p instanceof CPP14Parser.ParametersAndQualifiersContext);
    } catch (final Exception e) {
      // Expected if tree structure differs
    }
    return false;
  }
}