package net.explorviz.code.analysis.listener;

import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParserBaseListener;
import net.explorviz.code.analysis.handler.TypeScriptFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR Listener for extracting file data from TypeScript/JavaScript source
 * code.
 */
public class TypeScriptFileDataListener extends TypeScriptParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(TypeScriptFileDataListener.class);

  private final TypeScriptFileDataHandler fileDataHandler;
  private final String fileExtension;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public TypeScriptFileDataListener(final TypeScriptFileDataHandler fileDataHandler,
      final String fileExtension, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.fileExtension = fileExtension;
    this.tokens = tokens;
  }

  @Override
  public void enterProgram(final TypeScriptParser.ProgramContext ctx) {
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
  public void exitProgram(final TypeScriptParser.ProgramContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
  }

  @Override
  public void enterImportStatement(final TypeScriptParser.ImportStatementContext ctx) {
    // Extract import statements
    // Example: import { foo } from 'bar';
    if (ctx.getText() != null) {
      final String importText = ctx.getText();
      fileDataHandler.addImport(importText);
      LOGGER.atTrace()
          .addArgument(importText)
          .log("Import: {}");
    }
  }

  @Override
  public void enterClassDeclaration(final TypeScriptParser.ClassDeclarationContext ctx) {
    // Extract class name
    if (ctx.identifier() != null) {
      final String className = ctx.identifier().getText();
      final String fqn = className; // TODO: Build proper FQN with module/namespace

      fileDataHandler.enterClass(className, fqn);

      LOGGER.atTrace()
          .addArgument(className)
          .log("Class: {}");

      // Calculate class SLOC and LOC
      final int classLoc = calculateLoc(ctx);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LOC, String.valueOf(classLoc));

        if (ctx.classHeritage() != null && ctx.classHeritage().classExtendsClause() != null) {
          final String superClassFqn = ctx.classHeritage().classExtendsClause().typeReference().getText();
          classData.setSuperClass(getClassPathFromFqn(superClassFqn, fileExtension, fileDataHandler.getFileName(),
              fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
        }
      }
    }
  }

  @Override
  public void exitClassDeclaration(final TypeScriptParser.ClassDeclarationContext ctx) {
    // Leave class
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterInterfaceDeclaration(final TypeScriptParser.InterfaceDeclarationContext ctx) {
    // Extract interface name
    if (ctx.identifier() != null) {
      final String interfaceName = ctx.identifier().getText();
      final String fqn = interfaceName; // TODO: Build proper FQN

      fileDataHandler.enterClass(interfaceName, fqn);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.setIsInterface();

        // Calculate interface SLOC and LOC
        final int interfaceLoc = calculateLoc(ctx);
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LOC, String.valueOf(interfaceLoc));

        if (ctx.interfaceExtendsClause() != null) {
          for (final TypeScriptParser.TypeReferenceContext typeRef : ctx.interfaceExtendsClause()
              .classOrInterfaceTypeList().typeReference()) {
            final String superClassFqn = typeRef.getText();
            classData.addImplementedInterface(
                getClassPathFromFqn(superClassFqn, fileExtension, fileDataHandler.getFileName(),
                    fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
          }
        }
      }

      LOGGER.atTrace()
          .addArgument(interfaceName)
          .log("Interface: {}");
    }
  }

  @Override
  public void exitInterfaceDeclaration(final TypeScriptParser.InterfaceDeclarationContext ctx) {
    // Leave interface
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterMethodDeclarationExpression(
      final TypeScriptParser.MethodDeclarationExpressionContext ctx) {
    // Handle class methods: methodName() { ... }
    // This catches methods defined inside classes using the shorthand syntax
    if (ctx.propertyName() != null && fileDataHandler.isInClassContext()) {
      functionCount++;
      final String methodName = ctx.propertyName().getText();
      final String methodFqn = methodName + "#1"; // TODO: Add proper parameter hashing

      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final var methodData = classData.addMethod(methodName, methodFqn, "void"); // TODO: Extract actual return type

        // Set method location
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }

        // Calculate method SLOC and LOC
        final int methodLoc = calculateLoc(ctx);
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(methodLoc));

        LOGGER.atTrace()
            .addArgument(methodName)
            .log("Class method: {}");
      }
    }
  }

  @Override
  public void enterConstructorDeclaration(
      final TypeScriptParser.ConstructorDeclarationContext ctx) {
    // Handle class constructors
    if (fileDataHandler.isInClassContext()) {
      functionCount++;
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final String constructorFqn = "constructor#1"; // TODO: Add proper parameter hashing
        final var methodData = classData.addConstructor("constructor", constructorFqn);

        // Set constructor location
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }

        // Calculate constructor SLOC and LOC
        final int constructorLoc = calculateLoc(ctx);
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(constructorLoc));

        LOGGER.atTrace()
            .log("Constructor detected");
      }
    }
  }

  @Override
  public void enterFunctionDeclaration(final TypeScriptParser.FunctionDeclarationContext ctx) {
    // Extract function name
    if (ctx.identifier() != null) {
      functionCount++;
      final String functionName = ctx.identifier().getText();

      // Check if we're inside a class or this is a global function
      if (fileDataHandler.isInClassContext()) {
        // Function inside a class - treat as a method
        final String functionFqn = functionName + "#1"; // TODO: Add proper parameter hashing

        final var methodData = fileDataHandler.getCurrentClassData()
            .addMethod(functionName, functionFqn, "void"); // TODO: Extract actual return type

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Function inside class: {}");

        // Calculate function SLOC and LOC
        final int functionLoc = calculateLoc(ctx);
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(functionLoc));
      } else {
        // Global function - track it separately!
        final var methodHandler = fileDataHandler.addGlobalFunction(
            functionName,
            "void" // TODO: Extract actual return type
        );

        // Set function location
        if (ctx.start != null && ctx.stop != null) {
          methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }

        // Calculate LOC and SLOC
        final int functionLoc = calculateLoc(ctx);
        methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodHandler.addMetric(LOC, String.valueOf(functionLoc));

        // Check for async
        // TODO: Detect async functions

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Global function: {}");
      }
    }
  }

  @Override
  public void exitFunctionDeclaration(final TypeScriptParser.FunctionDeclarationContext ctx) {
    // Nothing special to do on exit for now
  }

  @Override
  public void enterArrowFunctionDeclaration(
      final TypeScriptParser.ArrowFunctionDeclarationContext ctx) {
    // Handle arrow functions: const foo = () => {}
    // Arrow functions are typically assigned to variables, so we need to extract
    // name

    // For now, we'll try to get the identifier from the parent context (variable
    // declaration)
    String functionName = extractArrowFunctionName(ctx);

    if (functionName != null) {
      functionCount++;
      if (fileDataHandler.isInClassContext()) {
        // Arrow function inside a class (e.g., class field)
        final String functionFqn = functionName + "#1";

        final var methodData = fileDataHandler.getCurrentClassData()
            .addMethod(functionName, functionFqn, "void");

        // Calculate method SLOC and LOC
        final int methodLoc = calculateLoc(ctx);
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(methodLoc));

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Arrow function inside class: {}");
      } else {
        // Global arrow function
        final var methodHandler = fileDataHandler.addGlobalFunction(
            functionName,
            "void");

        // Set function location
        if (ctx.start != null && ctx.stop != null) {
          methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }

        // Calculate SLOC and LOC
        final int functionLoc = calculateLoc(ctx);
        methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodHandler.addMetric(LOC, String.valueOf(functionLoc));

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Global arrow function: {}");
      }
    } else {
      // Anonymous arrow function - count it but don't add as named function
      LOGGER.atTrace().log("Anonymous arrow function detected");
    }
  }

  /**
   * Extract the name of an arrow function from its parent context.
   */
  private String extractArrowFunctionName(
      final TypeScriptParser.ArrowFunctionDeclarationContext ctx) {
    // Simple approach: look for identifiers in parent contexts
    ParserRuleContext parent = ctx.getParent();

    int depth = 0;
    while (parent != null && depth < 5) {
      // Try to find any identifier in the parent context
      String text = parent.getText();
      if (text != null && text.contains("=")) {
        // Likely a variable assignment: const foo = () => {}
        String[] parts = text.split("=");
        if (parts.length > 0) {
          String potentialName = parts[0].trim();
          // Remove keywords like const, let, var
          potentialName = potentialName.replaceAll("^(const|let|var)\\s+", "");
          if (potentialName.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")) {
            return potentialName;
          }
        }
      }
      parent = parent.getParent();
      depth++;
    }

    return null; // Anonymous arrow function
  }

  @Override
  public void enterVariableDeclaration(final TypeScriptParser.VariableDeclarationContext ctx) {
    variableCount++;
  }

  /**
   * Get comment lines of code by counting tokens on the hidden channel. ANTLR
   * places comments on a hidden channel, so we need to extract them from there.
   */
  private int getCloc(final ParserRuleContext ctx) {
    if (ctx == null || tokens == null) {
      return 0;
    }

    int commentLines = 0;

    // Iterate through all tokens to find comments on the hidden channel
    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);

      // Comments are typically on channel 1 (hidden channel)
      if (token.getChannel() != 0) {
        final String tokenText = token.getText();

        if (tokenText != null) {
          // Count lines in single-line comments (//)
          if (tokenText.trim().startsWith("//")) {
            commentLines++;
          } else if (tokenText.trim().startsWith("/*")) {
            // Count the number of newlines in the comment
            final long newlines = tokenText.chars().filter(ch -> ch == '\n').count();
            commentLines += (int) newlines + 1; // +1 for the first line
          }
        }
      }
    }

    return commentLines;
  }
}
