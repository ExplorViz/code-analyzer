package net.explorviz.code.analysis.listener;

import net.explorviz.code.analysis.antlr.generated.python.PythonLexer;
import net.explorviz.code.analysis.antlr.generated.python.PythonParser;
import net.explorviz.code.analysis.antlr.generated.python.PythonParserBaseListener;
import net.explorviz.code.analysis.handler.PythonFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR Listener for extracting file data from Python source code.
 */
public class PythonFileDataListener extends PythonParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PythonFileDataListener.class);

  private final PythonFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public PythonFileDataListener(final PythonFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
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

    LOGGER.atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitFile_input(final PythonParser.File_inputContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
  }

  @Override
  public void enterImport_stmt(final PythonParser.Import_stmtContext ctx) {
    // Extract import statements
    if (ctx.getText() != null) {
      final String importText = ctx.getText();
      fileDataHandler.addImport(importText);
      LOGGER.atTrace()
          .addArgument(importText)
          .log("Import: {}");
    }
  }

  @Override
  public void enterClassdef(final PythonParser.ClassdefContext ctx) {
    // Extract class name
    if (ctx.name() != null) {
      final String className = ctx.name().getText();
      final String classFqn = fileDataHandler.getFileName() + ":" + className;

      fileDataHandler.enterClass(className, classFqn);

      LOGGER.atTrace()
          .addArgument(className)
          .log("Class: {}");

      // Calculate class SLOC and LOC
      final int classLoc = calculateLoc(ctx);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LOC, String.valueOf(classLoc));

        // Extract superclasses
        if (ctx.arglist() != null) {
          for (final PythonParser.ArgumentContext argCtx : ctx.arglist().argument()) {
            if (argCtx.ASSIGN() == null && argCtx.comp_for() == null && argCtx.POWER() == null
                && argCtx.STAR() == null) {
              final String superClassFqn = argCtx.getText();
              classData.setSuperClass(getClassPathFromFqn(superClassFqn, ".py", fileDataHandler.getFileName(),
                  fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
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
      final String functionFqn = functionName + "#1"; // TODO: Add proper parameter hashing

      final var methodData = fileDataHandler.getCurrentClassData()
          .addMethod(functionName, functionFqn, "None"); // Python default return is None

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Method: {}");

      // Calculate function SLOC and LOC
      final int functionLoc = calculateLoc(ctx);
      methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      methodData.addMetric(LOC, String.valueOf(functionLoc));

      // Check for async - commented out for now
      // TODO: Add async support to MethodDataHandler if needed
    } else {
      // Global function
      final var funcBuilder = fileDataHandler.addGlobalFunction(
          functionName,
          "None" // TODO: Extract actual return type from type hints
      );

      // Set function location - find actual start/end lines
      int startLine = ctx.start != null ? ctx.start.getLine() : 0;
      int endLine = startLine;

      // Workaround for ANTLR Python grammar issue where ctx.stop includes next
      // function
      // See: https://github.com/antlr/grammars-v4/issues/4153
      if (ctx.suite() != null) {
        final var suite = ctx.suite();

        if (suite.simple_stmt() != null && suite.simple_stmt().stop != null) {
          endLine = suite.simple_stmt().stop.getLine();
        } else {
          // Multi-line function body - find the DEDENT token that marks end of function
          // Python uses INDENT/DEDENT tokens to mark indentation blocks
          if (suite.stop != null) {
            final int suiteStopIndex = suite.stop.getTokenIndex();

            for (int i = suiteStopIndex; i >= 0; i--) {
              final Token token = tokens.get(i);

              // Skip DEDENT, NEWLINE, LINE_BREAK, and COMMENT tokens
              if (token.getType() == PythonLexer.DEDENT
                  || token.getType() == PythonLexer.NEWLINE
                  || token.getType() == PythonLexer.LINE_BREAK
                  || token.getType() == PythonLexer.COMMENT) {
                continue;
              }

              // Found the last actual code token
              endLine = token.getLine();
              break;
            }
          }
        }
      }

      funcBuilder.setLines(startLine, endLine);

      // Calculate function SLOC and LOC using actual start and end lines
      final int functionLoc = (endLine >= startLine) ? (endLine - startLine + 1) : 0;
      funcBuilder.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      funcBuilder.addMetric(LOC, String.valueOf(functionLoc));

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Global function: {}");
    }
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
