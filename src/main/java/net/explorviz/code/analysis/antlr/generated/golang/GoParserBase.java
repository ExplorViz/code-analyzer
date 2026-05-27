package net.explorviz.code.analysis.antlr.generated.golang;

import java.util.HashSet;
import java.util.Set;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * All parser methods that used in grammar (p, prev, notLineTerminator, etc.)
 * should start with lower case char similar to parser rules.
 */
public abstract class GoParserBase extends Parser {
  private static boolean debug = false;
  private Set<String> table = new HashSet<>();

  protected GoParserBase(final TokenStream input) {
    super(input);
    final String cmdLine = System.getProperty("sun.java.command");
    final String[] args = cmdLine != null ? cmdLine.split("\\s+") : new String[0];
    debug = hasArg(args, "--debug");
  }

  private static boolean hasArg(final String[] args, final String arg) {
    for (final String a : args) {
      if (a.toLowerCase().contains(arg.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  protected void myreset() {
    table = new HashSet<>();
  }

  /**
   * Returns true if the current Token is a closing bracket (")" or "}")
   */
  protected boolean closingBracket() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final var la = stream.LT(1);
    return la.getType() == GoLexer.R_PAREN || la.getType() == GoLexer.R_CURLY
        || la.getType() == Token.EOF;
  }

  protected boolean isNotReceive() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final var la = stream.LT(2);
    return la.getType() != GoLexer.RECEIVE;
  }

  public void addImportSpec() {
    if (!(this._ctx instanceof GoParser.ImportSpecContext)) {
      return;
    }
    final GoParser.ImportSpecContext importSpec = (GoParser.ImportSpecContext) this._ctx;
    final GoParser.PackageNameContext packageName = importSpec.packageName();
    if (packageName != null) {
      final String name = packageName.getText();
      if (debug) {
        System.out.println("Entering " + name);
      }
      table.add(name);
      return;
    }
    final GoParser.ImportPathContext importPath = importSpec.importPath();
    if (importPath == null) {
      return;
    }
    String name = importPath.getText();
    if (debug) {
      System.out.println("import path " + name);
    }
    name = name.replace("\"", "");
    if (name.isEmpty()) {
      return;
    }
    name = name.replace("\\", "/");
    final String[] pathArr = name.split("/");
    if (pathArr.length == 0) {
      return;
    }
    String lastComponent = pathArr[pathArr.length - 1];
    if (lastComponent.isEmpty()) {
      return;
    }
    if (lastComponent.equals(".") || lastComponent.equals("..")) {
      return;
    }
    final String[] fileArr = lastComponent.split("\\.");
    if (fileArr.length == 0) {
      table.add(lastComponent);
      if (debug) {
        System.out.println("Entering " + lastComponent);
      }
      return;
    }
    String fileName = fileArr[fileArr.length - 1];
    if (fileName.isEmpty()) {
      fileName = lastComponent;
    }
    if (debug) {
      System.out.println("Entering " + fileName);
    }
    table.add(fileName);
  }

  public boolean isOperand() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final var la = stream.LT(1);
    if ("err".equals(la.getText())) {
      return true;
    }
    boolean result = true;
    if (la.getType() != GoParser.IDENTIFIER) {
      if (debug) {
        System.out.println("isOperand Returning " + result + " for " + la);
      }
      return result;
    }
    result = table.contains(la.getText());
    final Token la2 = stream.LT(2);
    if (la2.getType() != GoParser.DOT) {
      result = true;
      if (debug) {
        System.out.println("isOperand Returning " + result + " for " + la);
      }
      return result;
    }
    final Token la3 = stream.LT(3);
    if (la3.getType() == GoParser.L_PAREN) {
      result = true;
      if (debug) {
        System.out.println("isOperand Returning " + result + " for " + la);
      }
      return result;
    }
    if (debug) {
      System.out.println("isOperand Returning " + result + " for " + la);
    }
    return result;
  }

  public boolean isMethodExpr() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final Token la = stream.LT(1);
    boolean result = true;

    if (la.getType() == GoParser.STAR) {
      if (debug) {
        System.out.println("isMethodExpr Returning " + result + " for " + la);
      }
      return result;
    }

    if (la.getType() != GoParser.IDENTIFIER) {
      result = false;
      if (debug) {
        System.out.println("isMethodExpr Returning " + result + " for " + la);
      }
      return result;
    }

    result = !table.contains(la.getText());
    if (debug) {
      System.out.println("isMethodExpr Returning " + result + " for " + la);
    }
    return result;
  }

  protected boolean isConversion() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final var la = stream.LT(1);
    final var result = la.getType() != GoLexer.IDENTIFIER;
    if (debug) {
      System.out.println("isConversion Returning " + result + " for " + la);
    }
    return result;
  }

  private static final Set<String> BUILTIN_TYPE_FUNCTIONS = Set.of("make", "new");

  public boolean isTypeArgument() {
    final BufferedTokenStream stream = (BufferedTokenStream) _input;
    final Token funcToken = stream.LT(-2);
    if (funcToken == null || funcToken.getType() != GoParser.IDENTIFIER) {
      if (debug) {
        System.out.println("isTypeArgument Returning false - no identifier before (");
      }
      return false;
    }
    final boolean result = BUILTIN_TYPE_FUNCTIONS.contains(funcToken.getText());
    if (debug) {
      System.out.println("isTypeArgument Returning " + result + " for " + funcToken.getText());
    }
    return result;
  }

  public boolean isExpressionArgument() {
    final boolean result = !isTypeArgument();
    if (debug) {
      System.out.println("isExpressionArgument Returning " + result);
    }
    return result;
  }
}
