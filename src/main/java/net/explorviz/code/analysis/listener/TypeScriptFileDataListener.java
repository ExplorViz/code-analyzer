package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParser;
import net.explorviz.code.analysis.antlr.generated.typescript.TypeScriptParserBaseListener;
import net.explorviz.code.analysis.handler.MethodDataHandler;
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
  private static final String DEFAULT_RETURN_TYPE = "void";
  private static final String UNTYPED_PARAMETER = "any";

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
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterImportStatement(final TypeScriptParser.ImportStatementContext ctx) {
    if (ctx.getText() != null) {
      final String importText = ctx.getText();
      fileDataHandler.addImport(importText);
      LOGGER.atTrace()
          .addArgument(importText)
          .log("Import: {}");
    }
  }

  @Override
  public void enterNamespaceDeclaration(final TypeScriptParser.NamespaceDeclarationContext ctx) {
    if (ctx.namespaceName() != null) {
      final String namespaceName = ctx.namespaceName().getText();
      fileDataHandler.enterNamespace(namespaceName);
      LOGGER.atTrace()
          .addArgument(namespaceName)
          .log("Namespace: {}");
    }
  }

  @Override
  public void exitNamespaceDeclaration(final TypeScriptParser.NamespaceDeclarationContext ctx) {
    fileDataHandler.leaveNamespace();
  }

  @Override
  public void enterClassDeclaration(final TypeScriptParser.ClassDeclarationContext ctx) {
    if (ctx.identifier() != null) {
      final String className = ctx.identifier().getText();
      final String fqn = fileDataHandler.buildFqn(className);

      fileDataHandler.enterClass(className, fqn);

      LOGGER.atTrace()
          .addArgument(className)
          .log("Class: {}");

      final int classLoc = calculateLoc(ctx);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LINE_COUNT, String.valueOf(classLoc));

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
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterInterfaceDeclaration(final TypeScriptParser.InterfaceDeclarationContext ctx) {
    if (ctx.identifier() != null) {
      final String interfaceName = ctx.identifier().getText();
      final String fqn = fileDataHandler.buildFqn(interfaceName);

      fileDataHandler.enterClass(interfaceName, fqn);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.setIsInterface();

        final int interfaceLoc = calculateLoc(ctx);
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LINE_COUNT, String.valueOf(interfaceLoc));

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
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterMethodDeclarationExpression(
      final TypeScriptParser.MethodDeclarationExpressionContext ctx) {
    if (ctx.classElementName() == null || !fileDataHandler.isInClassContext()) {
      return;
    }

    functionCount++;
    final String methodName = ctx.classElementName().getText();
    final List<ParameterInfo> parameters = extractParameters(ctx.callSignature());
    final String methodFqn = fileDataHandler.buildMethodFqn(methodName, extractParameterTypes(parameters));
    final String returnType = extractReturnType(ctx.callSignature());
    final boolean async = isAsync(ctx.propertyMemberBase());

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      final MethodDataHandler methodData = classData.addMethod(methodName, methodFqn, returnType);
      populateMethod(methodData, ctx, parameters, async);

      LOGGER.atTrace()
          .addArgument(methodName)
          .log("Class method: {}");
    }
  }

  @Override
  public void enterConstructorDeclaration(
      final TypeScriptParser.ConstructorDeclarationContext ctx) {
    if (!fileDataHandler.isInClassContext()) {
      return;
    }

    functionCount++;
    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      final List<ParameterInfo> parameters = extractParameters(ctx.formalParameterList());
      final String constructorFqn = fileDataHandler.buildMethodFqn("constructor", extractParameterTypes(parameters));
      final MethodDataHandler methodData = classData.addConstructor("constructor", constructorFqn);
      populateMethod(methodData, ctx, parameters, false);

      LOGGER.atTrace()
          .log("Constructor detected");
    }
  }

  @Override
  public void enterFunctionDeclaration(final TypeScriptParser.FunctionDeclarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    functionCount++;
    final String functionName = ctx.identifier().getText();
    final List<ParameterInfo> parameters = extractParameters(ctx.formalParameterList());
    final String returnType = extractReturnType(ctx.typeAnnotation());
    final boolean async = ctx.Async() != null;

    if (fileDataHandler.isInClassContext()) {
      final String functionFqn = fileDataHandler.buildMethodFqn(functionName, extractParameterTypes(parameters));
      final var methodData = fileDataHandler.getCurrentClassData()
          .addMethod(functionName, functionFqn, returnType);
      populateMethod(methodData, ctx, parameters, async);

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Function inside class: {}");
    } else {
      final MethodDataHandler methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);
      populateMethod(methodHandler, ctx, parameters, async);

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Global function: {}");
    }
  }

  @Override
  public void exitFunctionDeclaration(final TypeScriptParser.FunctionDeclarationContext ctx) {
    // Nothing special to do on exit for now
  }

  @Override
  public void enterArrowFunctionDeclaration(
      final TypeScriptParser.ArrowFunctionDeclarationContext ctx) {
    final String functionName = extractArrowFunctionName(ctx);
    if (functionName == null) {
      LOGGER.atTrace().log("Anonymous arrow function detected");
      return;
    }

    functionCount++;
    final List<ParameterInfo> parameters = extractParameters(ctx.arrowFunctionParameters());
    final String returnType = extractReturnType(ctx.typeAnnotation());
    final boolean async = ctx.Async() != null;

    if (fileDataHandler.isInClassContext()) {
      final String functionFqn = fileDataHandler.buildMethodFqn(functionName, extractParameterTypes(parameters));
      final var methodData = fileDataHandler.getCurrentClassData()
          .addMethod(functionName, functionFqn, returnType);
      populateMethod(methodData, ctx, parameters, async);

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Arrow function inside class: {}");
    } else {
      final MethodDataHandler methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);
      populateMethod(methodHandler, ctx, parameters, async);

      LOGGER.atTrace()
          .addArgument(functionName)
          .log("Global arrow function: {}");
    }
  }

  @Override
  public void enterVariableDeclaration(final TypeScriptParser.VariableDeclarationContext ctx) {
    variableCount++;
  }

  private void populateMethod(final MethodDataHandler methodData, final ParserRuleContext ctx,
      final List<ParameterInfo> parameters, final boolean async) {
    for (final ParameterInfo parameter : parameters) {
      methodData.addParameter(parameter.name(), parameter.type(), List.of());
    }
    if (async) {
      methodData.addModifier("async");
    }
    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    final int methodLoc = calculateLoc(ctx);
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(methodLoc));
  }

  private List<ParameterInfo> extractParameters(final TypeScriptParser.CallSignatureContext callSignature) {
    if (callSignature == null || callSignature.parameterList() == null) {
      return List.of();
    }
    return extractParameters(callSignature.parameterList());
  }

  private List<ParameterInfo> extractParameters(
      final TypeScriptParser.ArrowFunctionParametersContext arrowParameters) {
    if (arrowParameters == null) {
      return List.of();
    }
    if (arrowParameters.formalParameterList() != null) {
      return extractParameters(arrowParameters.formalParameterList());
    }
    if (arrowParameters.propertyName() != null) {
      return List.of(new ParameterInfo(arrowParameters.propertyName().getText(), UNTYPED_PARAMETER));
    }
    return List.of();
  }

  private List<ParameterInfo> extractParameters(
      final TypeScriptParser.FormalParameterListContext formalParameterList) {
    final List<ParameterInfo> parameters = new ArrayList<>();
    if (formalParameterList == null) {
      return parameters;
    }

    for (final TypeScriptParser.FormalParameterArgContext arg : formalParameterList.formalParameterArg()) {
      parameters.add(new ParameterInfo(extractAssignableName(arg.assignable()),
          extractTypeFromAnnotation(arg.typeAnnotation())));
    }

    if (formalParameterList.lastFormalParameterArg() != null) {
      final TypeScriptParser.LastFormalParameterArgContext restArg = formalParameterList.lastFormalParameterArg();
      final String restName = restArg.identifier() != null ? restArg.identifier().getText() : "rest";
      parameters.add(new ParameterInfo(restName, extractTypeFromAnnotation(restArg.typeAnnotation(), "...")));
    }

    return parameters;
  }

  private List<ParameterInfo> extractParameters(final TypeScriptParser.ParameterListContext parameterList) {
    final List<ParameterInfo> parameters = new ArrayList<>();
    if (parameterList == null) {
      return parameters;
    }

    if (parameterList.restParameter() != null) {
      final TypeScriptParser.RestParameterContext rest = parameterList.restParameter();
      parameters.add(new ParameterInfo("rest", extractTypeFromAnnotation(rest.typeAnnotation(), "...")));
      return parameters;
    }

    for (final TypeScriptParser.ParameterContext parameter : parameterList.parameter()) {
      if (parameter.requiredParameter() != null) {
        parameters.add(extractRequiredParameter(parameter.requiredParameter()));
      } else if (parameter.optionalParameter() != null) {
        parameters.add(extractOptionalParameter(parameter.optionalParameter()));
      }
    }

    return parameters;
  }

  private ParameterInfo extractRequiredParameter(
      final TypeScriptParser.RequiredParameterContext requiredParameter) {
    return new ParameterInfo(extractIdentifierOrPatternName(requiredParameter.identifierOrPattern()),
        extractTypeFromAnnotation(requiredParameter.typeAnnotation()));
  }

  private ParameterInfo extractOptionalParameter(
      final TypeScriptParser.OptionalParameterContext optionalParameter) {
    final TypeScriptParser.IdentifierOrPatternContext pattern = optionalParameter.identifierOrPattern();
    return new ParameterInfo(extractIdentifierOrPatternName(pattern),
        extractTypeFromAnnotation(optionalParameter.typeAnnotation()));
  }

  private List<String> extractParameterTypes(final List<ParameterInfo> parameters) {
    return parameters.stream().map(ParameterInfo::type).toList();
  }

  private String extractReturnType(final TypeScriptParser.CallSignatureContext callSignature) {
    if (callSignature == null) {
      return DEFAULT_RETURN_TYPE;
    }
    return extractReturnType(callSignature.typeAnnotation());
  }

  private String extractReturnType(final TypeScriptParser.TypeAnnotationContext typeAnnotation) {
    if (typeAnnotation == null || typeAnnotation.type_() == null) {
      return DEFAULT_RETURN_TYPE;
    }
    return normalizeTypeName(typeAnnotation.type_().getText());
  }

  private String extractTypeFromAnnotation(final TypeScriptParser.TypeAnnotationContext typeAnnotation) {
    return extractTypeFromAnnotation(typeAnnotation, UNTYPED_PARAMETER);
  }

  private String extractTypeFromAnnotation(final TypeScriptParser.TypeAnnotationContext typeAnnotation,
      final String defaultType) {
    if (typeAnnotation == null || typeAnnotation.type_() == null) {
      return defaultType;
    }
    return normalizeTypeName(typeAnnotation.type_().getText());
  }

  private String extractAssignableName(final TypeScriptParser.AssignableContext assignable) {
    if (assignable == null) {
      return "param";
    }
    if (assignable.identifier() != null) {
      return assignable.identifier().getText();
    }
    return shortenPattern(assignable.getText());
  }

  private String extractIdentifierOrPatternName(
      final TypeScriptParser.IdentifierOrPatternContext identifierOrPattern) {
    if (identifierOrPattern == null) {
      return "param";
    }
    if (identifierOrPattern.identifierName() != null) {
      return identifierOrPattern.identifierName().getText();
    }
    return shortenPattern(identifierOrPattern.getText());
  }

  private String shortenPattern(final String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      return "param";
    }
    if (pattern.length() > 40) {
      return pattern.substring(0, 37) + "...";
    }
    return pattern;
  }

  private String normalizeTypeName(final String typeName) {
    if (typeName == null || typeName.isBlank()) {
      return DEFAULT_RETURN_TYPE;
    }
    return typeName.replaceAll("\\s+", " ").trim();
  }

  private boolean isAsync(final TypeScriptParser.PropertyMemberBaseContext propertyMemberBase) {
    return propertyMemberBase != null && propertyMemberBase.Async() != null;
  }

  /**
   * Extract the name of an arrow function from its parent context.
   */
  private String extractArrowFunctionName(
      final TypeScriptParser.ArrowFunctionDeclarationContext ctx) {
    ParserRuleContext parent = ctx.getParent();

    int depth = 0;
    while (parent != null && depth < 5) {
      if (parent instanceof TypeScriptParser.VariableDeclarationContext variableDeclaration) {
        if (variableDeclaration.identifierOrKeyWord() != null) {
          return variableDeclaration.identifierOrKeyWord().getText();
        }
      }
      if (parent instanceof TypeScriptParser.PropertyDeclarationExpressionContext propertyDeclaration) {
        if (propertyDeclaration.classElementName() != null) {
          return propertyDeclaration.classElementName().getText();
        }
      }

      final String text = parent.getText();
      if (text != null && text.contains("=")) {
        final String[] parts = text.split("=", 2);
        String potentialName = parts[0].trim();
        potentialName = potentialName.replaceAll("^(const|let|var)\\s+", "");
        if (potentialName.matches("[a-zA-Z_$#][a-zA-Z0-9_$#]*")) {
          return potentialName;
        }
      }
      parent = parent.getParent();
      depth++;
    }

    return null;
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

  private record ParameterInfo(String name, String type) {}
}
