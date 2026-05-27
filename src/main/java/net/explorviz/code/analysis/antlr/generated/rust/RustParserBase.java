package net.explorviz.code.analysis.antlr.generated.rust;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;

public abstract class RustParserBase extends Parser {
  public RustParserBase(final TokenStream input) {
    super(input);
  }

  public boolean NextGT() {
    return _input.LA(1) == RustParser.GT;
  }

  public boolean NextLT() {
    return _input.LA(1) == RustParser.LT;
  }
}
