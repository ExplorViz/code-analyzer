package net.explorviz.code.analysis.antlr.generated.rust;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

public abstract class RustLexerBase extends Lexer {
  private Token lt1;
  private Token lt2;

  public RustLexerBase(final CharStream input) {
    super(input);
  }

  @Override
  public Token nextToken() {
    final Token next = super.nextToken();

    if (next.getChannel() == Token.DEFAULT_CHANNEL) {
      this.lt2 = this.lt1;
      this.lt1 = next;
    }

    return next;
  }

  public boolean SOF() {
    return _input.LA(-1) <= 0;
  }

  public boolean FloatDotPossible() {
    final int next = _input.LA(1);
    if (next == '.' || next == '_') {
      return false;
    }
    if (next == 'f') {
      if (_input.LA(2) == '3' && _input.LA(3) == '2') {
        return true;
      }
      if (_input.LA(2) == '6' && _input.LA(3) == '4') {
        return true;
      }
      return false;
    }
    if (next >= 'a' && next <= 'z') {
      return false;
    }
    if (next >= 'A' && next <= 'Z') {
      return false;
    }
    return true;
  }

  public boolean FloatLiteralPossible() {
    if (this.lt1 == null || this.lt2 == null) {
      return true;
    }
    if (this.lt1.getType() != RustLexer.DOT) {
      return true;
    }
    switch (this.lt2.getType()) {
      case RustLexer.CHAR_LITERAL:
      case RustLexer.STRING_LITERAL:
      case RustLexer.RAW_STRING_LITERAL:
      case RustLexer.BYTE_LITERAL:
      case RustLexer.BYTE_STRING_LITERAL:
      case RustLexer.RAW_BYTE_STRING_LITERAL:
      case RustLexer.INTEGER_LITERAL:
      case RustLexer.DEC_LITERAL:
      case RustLexer.HEX_LITERAL:
      case RustLexer.OCT_LITERAL:
      case RustLexer.BIN_LITERAL:
      case RustLexer.KW_SUPER:
      case RustLexer.KW_SELFVALUE:
      case RustLexer.KW_SELFTYPE:
      case RustLexer.KW_CRATE:
      case RustLexer.KW_DOLLARCRATE:
      case RustLexer.GT:
      case RustLexer.RCURLYBRACE:
      case RustLexer.RSQUAREBRACKET:
      case RustLexer.RPAREN:
      case RustLexer.KW_AWAIT:
      case RustLexer.NON_KEYWORD_IDENTIFIER:
      case RustLexer.RAW_IDENTIFIER:
      case RustLexer.KW_MACRORULES:
        return false;
      default:
        return true;
    }
  }
}
