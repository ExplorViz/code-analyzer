package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParser;
import net.explorviz.code.analysis.antlr.generated.kotlin.KotlinParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.KotlinFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from Kotlin source code.
 */
public class KotlinFileDataListener extends KotlinParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(KotlinFileDataListener.class);

  private final KotlinFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public KotlinFileDataListener(final KotlinFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterKotlinFile(final KotlinParser.KotlinFileContext ctx) {
    addFileMetrics();
  }

  @Override
  public void enterScript(final KotlinParser.ScriptContext ctx) {
    addFileMetrics();
  }

  @Override
  public void exitKotlinFile(final KotlinParser.KotlinFileContext ctx) {
    addFunctionAndVariableMetrics();
  }

  @Override
  public void exitScript(final KotlinParser.ScriptContext ctx) {
    addFunctionAndVariableMetrics();
  }

  private void addFileMetrics() {
    final int sloc = getSloc(tokens);
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    LOGGER.atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  private void addFunctionAndVariableMetrics() {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterPackageHeader(final KotlinParser.PackageHeaderContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.setPackageName(ctx.identifier().getText());
    }
  }

  @Override
  public void enterImportHeader(final KotlinParser.ImportHeaderContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    String importName = ctx.identifier().getText();
    if (ctx.MULT() != null) {
      importName += ".*";
    }
    fileDataHandler.addImport(importName);
  }

  @Override
  public void enterClassDeclaration(final KotlinParser.ClassDeclarationContext ctx) {
    if (ctx.simpleIdentifier() == null) {
      return;
    }

    final String className = ctx.simpleIdentifier().getText();
    final String fqn = fileDataHandler.buildFqn(className);
    fileDataHandler.enterClass(className, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));

    if (ctx.INTERFACE() != null) {
      classData.setIsInterface();
    } else if (ctx.enumClassBody() != null) {
      classData.setIsEnum();
    } else {
      classData.setIsClass();
    }

    addDelegationRelations(classData, ctx);
  }

  private void addDelegationRelations(final ClassDataHandler classData,
      final KotlinParser.ClassDeclarationContext ctx) {
    if (ctx.delegationSpecifiers() == null) {
      return;
    }

    final boolean isInterface = ctx.INTERFACE() != null;
    final List<KotlinParser.DelegationSpecifierContext> specifiers =
        ctx.delegationSpecifiers().delegationSpecifier();

    for (int i = 0; i < specifiers.size(); i++) {
      final String typeName = extractDelegationType(specifiers.get(i));
      if (typeName == null) {
        continue;
      }

      if (isInterface || i > 0) {
        classData.addImplementedInterface(typeName);
      } else {
        addFormattedSuperClass(classData, typeName);
      }
    }
  }

  private String extractDelegationType(final KotlinParser.DelegationSpecifierContext specifier) {
    if (specifier.userType() != null) {
      return specifier.userType().getText();
    }
    if (specifier.constructorInvocation() != null
        && specifier.constructorInvocation().userType() != null) {
      return specifier.constructorInvocation().userType().getText();
    }
    return null;
  }

  private void addFormattedSuperClass(final ClassDataHandler classData, final String superClassFqn) {
    classData.setSuperClass(getClassPathFromFqn(superClassFqn, ".kt", fileDataHandler.getFileName(),
        fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
  }

  @Override
  public void exitClassDeclaration(final KotlinParser.ClassDeclarationContext ctx) {
    if (ctx.simpleIdentifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterObjectDeclaration(final KotlinParser.ObjectDeclarationContext ctx) {
    if (ctx.simpleIdentifier() == null) {
      return;
    }

    final String objectName = ctx.simpleIdentifier().getText();
    final String fqn = fileDataHandler.buildFqn(objectName);
    fileDataHandler.enterClass(objectName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsClass();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
      addObjectDelegationRelations(classData, ctx);
    }
  }

  private void addObjectDelegationRelations(final ClassDataHandler classData,
      final KotlinParser.ObjectDeclarationContext ctx) {
    if (ctx.delegationSpecifiers() == null) {
      return;
    }

    final List<KotlinParser.DelegationSpecifierContext> specifiers =
        ctx.delegationSpecifiers().delegationSpecifier();

    for (int i = 0; i < specifiers.size(); i++) {
      final String typeName = extractDelegationType(specifiers.get(i));
      if (typeName == null) {
        continue;
      }

      if (i == 0) {
        addFormattedSuperClass(classData, typeName);
      } else {
        classData.addImplementedInterface(typeName);
      }
    }
  }

  @Override
  public void exitObjectDeclaration(final KotlinParser.ObjectDeclarationContext ctx) {
    if (ctx.simpleIdentifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterCompanionObject(final KotlinParser.CompanionObjectContext ctx) {
    final String companionName = ctx.simpleIdentifier() != null
        ? ctx.simpleIdentifier().getText()
        : "Companion";
    final String fqn = fileDataHandler.buildFqn(companionName);
    fileDataHandler.enterClass(companionName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsClass();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitCompanionObject(final KotlinParser.CompanionObjectContext ctx) {
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterEnumEntry(final KotlinParser.EnumEntryContext ctx) {
    if (ctx.simpleIdentifier() == null) {
      return;
    }

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.addEnumConstant(ctx.simpleIdentifier().getText());
    }
  }

  @Override
  public void enterPropertyDeclaration(final KotlinParser.PropertyDeclarationContext ctx) {
    if (ctx.variableDeclaration() != null) {
      countVariableDeclaration(ctx.variableDeclaration());
      addPropertyField(ctx);
    }

    if (ctx.multiVariableDeclaration() != null) {
      for (final KotlinParser.VariableDeclarationContext variableDecl : ctx.multiVariableDeclaration()
          .variableDeclaration()) {
        countVariableDeclaration(variableDecl);
      }
    }
  }

  private void countVariableDeclaration(final KotlinParser.VariableDeclarationContext variableDecl) {
    if (variableDecl.simpleIdentifier() != null) {
      variableCount++;
    }
  }

  private void addPropertyField(final KotlinParser.PropertyDeclarationContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null || ctx.variableDeclaration() == null) {
      return;
    }

    final String fieldType = ctx.type() != null
        ? ctx.type().getText()
        : ctx.variableDeclaration().type() != null
            ? ctx.variableDeclaration().type().getText()
            : "unknown";
    classData.addField(ctx.variableDeclaration().simpleIdentifier().getText(), fieldType,
        new ArrayList<>());
  }

  @Override
  public void enterClassParameter(final KotlinParser.ClassParameterContext ctx) {
    if (ctx.VAL() == null && ctx.VAR() == null) {
      return;
    }

    if (ctx.simpleIdentifier() != null) {
      variableCount++;

      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final String fieldType = ctx.type() != null ? ctx.type().getText() : "unknown";
        classData.addField(ctx.simpleIdentifier().getText(), fieldType, new ArrayList<>());
      }
    }
  }

  @Override
  public void enterFunctionDeclaration(final KotlinParser.FunctionDeclarationContext ctx) {
    functionCount++;

    final String functionName = extractFunctionName(ctx);
    if (functionName == null) {
      return;
    }

    final String returnType = extractReturnType(ctx);
    final MethodDataHandler methodData = createMethodHandler(functionName, returnType, ctx);
    if (methodData == null) {
      return;
    }

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterSecondaryConstructor(final KotlinParser.SecondaryConstructorContext ctx) {
    functionCount++;

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String className = classData.getProtoBufObject().getName();
    final MethodDataHandler methodData = classData.addConstructor(className,
        className + "#" + extractParameterTypes(ctx).hashCode());
    addParameters(methodData, ctx.functionValueParameters());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
  }

  private String extractReturnType(final KotlinParser.FunctionDeclarationContext ctx) {
    if (ctx.COLON() != null && !ctx.type().isEmpty()) {
      return ctx.type(ctx.type().size() - 1).getText();
    }
    return "Unit";
  }

  private String extractFunctionName(final KotlinParser.FunctionDeclarationContext ctx) {
    if (ctx.identifier() != null) {
      return ctx.identifier().getText();
    }
    return null;
  }

  private MethodDataHandler createMethodHandler(final String methodName, final String returnType,
      final KotlinParser.FunctionDeclarationContext ctx) {
    if (fileDataHandler.isInClassContext()) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData == null) {
        return null;
      }
      final MethodDataHandler methodData = classData.addMethod(methodName,
          methodName + "#" + extractParameterTypes(ctx).hashCode(), returnType);
      addParameters(methodData, ctx.functionValueParameters());
      return methodData;
    }

    final MethodDataHandler methodData = fileDataHandler.addGlobalFunction(methodName, returnType);
    addParameters(methodData, ctx.functionValueParameters());
    return methodData;
  }

  private List<String> extractParameterTypes(final KotlinParser.FunctionDeclarationContext ctx) {
    return extractParameterTypes(ctx.functionValueParameters());
  }

  private List<String> extractParameterTypes(final KotlinParser.SecondaryConstructorContext ctx) {
    return extractParameterTypes(ctx.functionValueParameters());
  }

  private List<String> extractParameterTypes(
      final KotlinParser.FunctionValueParametersContext parameters) {
    final List<String> paramTypes = new ArrayList<>();
    if (parameters == null) {
      return paramTypes;
    }

    for (final KotlinParser.FunctionValueParameterContext param : parameters.functionValueParameter()) {
      if (param.parameter() != null && param.parameter().type() != null) {
        paramTypes.add(param.parameter().type().getText());
      }
    }
    return paramTypes;
  }

  private void addParameters(final MethodDataHandler methodData,
      final KotlinParser.FunctionValueParametersContext parameters) {
    if (parameters == null) {
      return;
    }

    for (final KotlinParser.FunctionValueParameterContext param : parameters.functionValueParameter()) {
      if (param.parameter() == null) {
        continue;
      }

      final String paramType = param.parameter().type() != null
          ? param.parameter().type().getText()
          : "unknown";
      final String paramName = param.parameter().simpleIdentifier() != null
          ? param.parameter().simpleIdentifier().getText()
          : "";
      methodData.addParameter(paramName, paramType, new ArrayList<>());
    }
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
