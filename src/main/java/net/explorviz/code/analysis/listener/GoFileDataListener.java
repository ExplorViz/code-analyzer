package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.antlr.generated.golang.GoParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.GoFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from Go source code.
 */
public class GoFileDataListener extends GoParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoFileDataListener.class);

  private final GoFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public GoFileDataListener(final GoFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterSourceFile(final GoParser.SourceFileContext ctx) {
    final int sloc = getSloc(tokens);
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    LOGGER.atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitSourceFile(final GoParser.SourceFileContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
  }

  @Override
  public void enterPackageName(final GoParser.PackageNameContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.setPackageName(ctx.identifier().getText());
    }
  }

  @Override
  public void enterImportSpec(final GoParser.ImportSpecContext ctx) {
    if (ctx.importPath() == null || ctx.importPath().string_() == null) {
      return;
    }

    String importPath = ctx.importPath().string_().getText();
    importPath = importPath.replace("\"", "");
    if (!importPath.isEmpty()) {
      fileDataHandler.addImport(importPath);
    }
  }

  @Override
  public void enterTypeDef(final GoParser.TypeDefContext ctx) {
    if (ctx.IDENTIFIER() == null || ctx.type_() == null) {
      return;
    }

    final String typeName = ctx.IDENTIFIER().getText();
    final String fqn = fileDataHandler.buildFqn(typeName);
    fileDataHandler.enterClass(typeName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    if (ctx.type_().typeLit() != null) {
      if (ctx.type_().typeLit().structType() != null) {
        classData.setIsStruct();
      } else if (ctx.type_().typeLit().interfaceType() != null) {
        classData.setIsInterface();
      }
    }

    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void exitTypeDef(final GoParser.TypeDefContext ctx) {
    if (ctx.IDENTIFIER() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterFieldDecl(final GoParser.FieldDeclContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    if (ctx.identifierList() != null && ctx.type_() != null) {
      final String fieldType = ctx.type_().getText();
      for (final var identifier : ctx.identifierList().IDENTIFIER()) {
        classData.addField(identifier.getText(), fieldType, new ArrayList<>());
      }
    }
  }

  @Override
  public void enterFunctionDecl(final GoParser.FunctionDeclContext ctx) {
    if (ctx.IDENTIFIER() == null || ctx.signature() == null) {
      return;
    }

    functionCount++;
    final String functionName = ctx.IDENTIFIER().getText();
    final String returnType = extractReturnType(ctx.signature());
    final List<String> paramTypes = extractParameterTypes(ctx.signature());

    final MethodDataHandler methodHandler =
        fileDataHandler.addGlobalFunction(functionName, returnType);
    addSignatureParameters(methodHandler, ctx.signature());

    if (ctx.start != null && ctx.stop != null) {
      methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodHandler.addMetric(LOC, String.valueOf(calculateLoc(ctx)));

    LOGGER.atTrace()
        .addArgument(functionName)
        .addArgument(paramTypes.size())
        .log("Function: {} ({} params)");
  }

  @Override
  public void enterMethodDecl(final GoParser.MethodDeclContext ctx) {
    if (ctx.IDENTIFIER() == null || ctx.signature() == null || ctx.receiver() == null) {
      return;
    }

    functionCount++;
    final String methodName = ctx.IDENTIFIER().getText();
    final String receiverType = extractReceiverType(ctx.receiver());
    final String fqn = fileDataHandler.buildFqn(receiverType);
    final ClassDataHandler classData = fileDataHandler.getClassData(fqn);

    if (classData == null) {
      LOGGER.atTrace()
          .addArgument(methodName)
          .addArgument(receiverType)
          .log("Method {} on unknown type {}");
      return;
    }

    final String returnType = extractReturnType(ctx.signature());
    final String methodFqn = methodName + "#" + extractParameterTypes(ctx.signature()).hashCode();
    final MethodDataHandler methodData = classData.addMethod(methodName, methodFqn, returnType);
    addSignatureParameters(methodData, ctx.signature());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterVarSpec(final GoParser.VarSpecContext ctx) {
    if (ctx.identifierList() != null) {
      variableCount += ctx.identifierList().IDENTIFIER().size();
    }
  }

  @Override
  public void enterConstSpec(final GoParser.ConstSpecContext ctx) {
    if (ctx.identifierList() != null) {
      variableCount += ctx.identifierList().IDENTIFIER().size();
    }
  }

  private String extractReturnType(final GoParser.SignatureContext signature) {
    if (signature.result() == null) {
      return "void";
    }
    if (signature.result().type_() != null) {
      return signature.result().type_().getText();
    }
    return signature.result().getText();
  }

  private List<String> extractParameterTypes(final GoParser.SignatureContext signature) {
    final List<String> paramTypes = new ArrayList<>();
    if (signature.parameters() == null) {
      return paramTypes;
    }

    for (final GoParser.ParameterDeclContext param : signature.parameters().parameterDecl()) {
      if (param.type_() != null) {
        paramTypes.add(param.type_().getText());
      }
    }
    return paramTypes;
  }

  private void addSignatureParameters(final MethodDataHandler methodData,
      final GoParser.SignatureContext signature) {
    if (signature.parameters() == null) {
      return;
    }

    for (final GoParser.ParameterDeclContext param : signature.parameters().parameterDecl()) {
      final String paramType = param.type_() != null ? param.type_().getText() : "unknown";
      String paramName = "";
      if (param.identifierList() != null && !param.identifierList().IDENTIFIER().isEmpty()) {
        paramName = param.identifierList().IDENTIFIER().get(0).getText();
      }
      methodData.addParameter(paramName, paramType, new ArrayList<>());
    }
  }

  private String extractReceiverType(final GoParser.ReceiverContext receiver) {
    if (receiver.parameters() == null || receiver.parameters().parameterDecl().isEmpty()) {
      return "unknown";
    }

    final GoParser.ParameterDeclContext param = receiver.parameters().parameterDecl().get(0);
    if (param.type_() == null) {
      return "unknown";
    }

    return extractTypeName(param.type_());
  }

  private String extractTypeName(final GoParser.Type_Context typeCtx) {
    if (typeCtx.typeName() != null && typeCtx.typeName().IDENTIFIER() != null) {
      return typeCtx.typeName().IDENTIFIER().getText();
    }
    if (typeCtx.typeLit() != null && typeCtx.typeLit().pointerType() != null) {
      final GoParser.PointerTypeContext pointer = typeCtx.typeLit().pointerType();
      if (pointer.type_() != null) {
        return extractTypeName(pointer.type_());
      }
    }
    return typeCtx.getText().replace("*", "").trim();
  }

  private int getCloc() {
    if (tokens == null) {
      return 0;
    }

    int commentLines = 0;
    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);
      if (token.getChannel() != 0) {
        final String tokenText = token.getText();
        if (tokenText != null) {
          if (tokenText.trim().startsWith("//")) {
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
