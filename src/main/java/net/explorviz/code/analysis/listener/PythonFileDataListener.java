package net.explorviz.code.analysis.listener;

import net.explorviz.code.analysis.antlr.generated.python.PythonLexer;
import net.explorviz.code.analysis.antlr.generated.python.PythonParser;
import net.explorviz.code.analysis.antlr.generated.python.PythonParserBaseListener;
import net.explorviz.code.analysis.handler.PythonFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR Listener for extracting file data from Python source code. */
public class PythonFileDataListener extends PythonParserBaseListener
    implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PythonFileDataListener.class);

  private final PythonFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public PythonFileDataListener(
      final PythonFileDataHandler fileDataHandler, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterFile_input(final PythonParser.File_inputContext ctx) {
    // Calculate total source SLOC and CLOC
    final int sloc = getSloc(tokens);
    final int cloc = getCloc(ctx);

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    LOGGER
        .atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitFile_input(final PythonParser.File_inputContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterImport_stmt(final PythonParser.Import_stmtContext ctx) {
    // Extract import statements
    if (ctx.getText() != null) {
      final String importText = ctx.getText();
      fileDataHandler.addImport(importText);
      LOGGER.atTrace().addArgument(importText).log("Import: {}");
    }
  }

  @Override
  public void enterClassdef(final PythonParser.ClassdefContext ctx) {
    // Extract class name
    if (ctx.name() != null) {
      final String className = ctx.name().getText();
      final String classFqn = fileDataHandler.getFileName() + ":" + className;

      fileDataHandler.enterClass(className, classFqn);

      LOGGER.atTrace().addArgument(className).log("Class: {}");

      // Calculate class SLOC and LOC
      final int classLoc = getEndLine(ctx) - ctx.start.getLine() + 1;
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LINE_COUNT, String.valueOf(classLoc));

        // Extract superclasses
        if (ctx.arglist() != null) {
          for (final PythonParser.ArgumentContext argCtx : ctx.arglist().argument()) {
            if (argCtx.ASSIGN() == null
                && argCtx.comp_for() == null
                && argCtx.POWER() == null
                && argCtx.STAR() == null) {
              final String superClassFqn = argCtx.getText();
              classData.setSuperClass(
                  getClassPathFromFqn(
                          superClassFqn,
                          ".py",
                          fileDataHandler.getFileName(),
                          fileDataHandler.getPackageName())
                      + "::"
                      + getClassNameFromFqn(superClassFqn));
            }
          }
        }
      }
    }
  }

  @Override
  public void exitClassdef(final PythonParser.ClassdefContext ctx) {
    // Leave class
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterFuncdef(final PythonParser.FuncdefContext ctx) {
    if (ctx.name() == null) {
      return;
    }

    functionCount++;
    // Extract function name
    final String functionName = ctx.name().getText();

    // Check if we're inside a class or this is a global function
    if (fileDataHandler.isInClassContext()) {
      // Function inside a class - treat as a method
      // Same-named methods (property setters, @overload stubs) are all kept: addMethod does not
      // replace an existing key.
      final String functionFqn = functionName;

      final var methodData =
          fileDataHandler
              .getCurrentClassData()
              .addMethod(functionName, functionFqn, "None"); // Python default return is None

      LOGGER.atTrace().addArgument(functionName).log("Method: {}");

      final int startLine = ctx.start.getLine();
      final int endLine = getEndLine(ctx);
      methodData.setLines(startLine, endLine);
      methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      methodData.addMetric(LINE_COUNT, String.valueOf(endLine - startLine + 1));

      if (ctx.ASYNC() != null) {
        methodData.addModifier("async");
      }
    } else {
      // Global function
      final var funcBuilder =
          fileDataHandler.addGlobalFunction(
              functionName, "None" // TODO: Extract actual return type from type hints
              );

      if (ctx.ASYNC() != null) {
        funcBuilder.addModifier("async");
      }

      final int startLine = ctx.start.getLine();
      final int endLine = getEndLine(ctx);
      funcBuilder.setLines(startLine, endLine);
      funcBuilder.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      funcBuilder.addMetric(LINE_COUNT, String.valueOf(endLine - startLine + 1));

      LOGGER.atTrace().addArgument(functionName).log("Global function: {}");
    }
  }

  /**
   * Returns the line on which the construct really ends.
   *
   * <p>{@code ctx.stop} of a function or class is the synthetic {@code DEDENT} or {@code
   * LINE_BREAK} token that closes its suite. {@link
   * net.explorviz.code.analysis.antlr.generated.python.PythonLexerBase} stamps these with the
   * position of the next statement, so the last real token (default channel, not synthetic, not
   * whitespace) is used instead. For a token spanning several lines, e.g. a multi-line string, its
   * last line is returned.
   */
  private int getEndLine(final ParserRuleContext ctx) {
    final int startLine = ctx.start.getLine();
    if (ctx.stop == null || tokens == null) {
      return startLine;
    }
    final int startIndex = ctx.start.getTokenIndex();
    for (int i = Math.min(ctx.stop.getTokenIndex(), tokens.size() - 1); i >= startIndex; i--) {
      final Token token = tokens.get(i);
      if (isRealToken(token)) {
        final int newlines = (int) token.getText().chars().filter(ch -> ch == '\n').count();
        return Math.max(startLine, token.getLine() + newlines);
      }
    }
    return startLine;
  }

  private static boolean isRealToken(final Token token) {
    final int type = token.getType();
    if (type == Token.EOF
        || type == PythonLexer.INDENT
        || type == PythonLexer.DEDENT
        || type == PythonLexer.LINE_BREAK
        || token.getChannel() != Token.DEFAULT_CHANNEL) {
      return false;
    }
    final String text = token.getText();
    return text != null && !text.isBlank();
  }

  @Override
  public void enterExpr_stmt(final PythonParser.Expr_stmtContext ctx) {
    // Increment variable count for assignments
    if (ctx.assign_part() != null) {
      if (ctx.assign_part().ASSIGN() != null && !ctx.assign_part().ASSIGN().isEmpty()) {
        variableCount += ctx.assign_part().ASSIGN().size();
      } else if (ctx.assign_part().COLON() != null) {
        variableCount++;
      }
    }
  }

  private int getCloc(final org.antlr.v4.runtime.ParserRuleContext ctx) {
    if (ctx == null || tokens == null) {
      return 0;
    }

    int commentLines = 0;

    for (int i = 0; i < tokens.size(); i++) {
      final Token token = tokens.get(i);

      if (token.getChannel() != 0) {
        final String tokenText = token.getText();
        if (tokenText != null) {
          if (tokenText.trim().startsWith("#")) {
            commentLines++;
          } else if (tokenText.trim().startsWith("/*")) {
            final long newlines = tokenText.chars().filter(ch -> ch == '\n').count();
            commentLines += (int) newlines + 1;
          }
        }
      }
    }

    return commentLines;
  }
}
