package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.php.PhpLexer;
import net.explorviz.code.analysis.antlr.generated.php.PhpParser;
import net.explorviz.code.analysis.antlr.generated.php.PhpParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.handler.PhpFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from PHP source code.
 */
public class PhpFileDataListener extends PhpParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PhpFileDataListener.class);

  private final PhpFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public PhpFileDataListener(final PhpFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterHtmlDocument(final PhpParser.HtmlDocumentContext ctx) {
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
  public void exitHtmlDocument(final PhpParser.HtmlDocumentContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterNamespaceDeclaration(final PhpParser.NamespaceDeclarationContext ctx) {
    if (ctx.namespaceNameList() == null) {
      return;
    }

    final String namespace = normalizeNamespace(ctx.namespaceNameList().getText());
    if (ctx.OpenCurlyBracket() != null) {
      fileDataHandler.enterNamespace(namespace);
    } else {
      fileDataHandler.setPackageName(namespace);
    }
  }

  @Override
  public void exitNamespaceDeclaration(final PhpParser.NamespaceDeclarationContext ctx) {
    if (ctx.namespaceNameList() != null && ctx.OpenCurlyBracket() != null) {
      fileDataHandler.leaveNamespace();
    }
  }

  @Override
  public void enterUseDeclaration(final PhpParser.UseDeclarationContext ctx) {
    if (ctx.useDeclarationContentList() == null) {
      return;
    }

    for (final PhpParser.UseDeclarationContentContext useContent : ctx.useDeclarationContentList()
        .useDeclarationContent()) {
      if (useContent.namespaceNameList() != null) {
        fileDataHandler.addImport(normalizeNamespace(useContent.namespaceNameList().getText()));
      }
    }
  }

  @Override
  public void enterClassDeclaration(final PhpParser.ClassDeclarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String className = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(className);
    fileDataHandler.enterClass(className, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));

    if (ctx.Interface() != null) {
      classData.setIsInterface();
      addInterfaceExtends(classData, ctx.interfaceList());
    } else if (ctx.classEntryType() != null && ctx.classEntryType().Trait() != null) {
      classData.setIsClass();
    } else {
      classData.setIsClass();
      if (ctx.qualifiedStaticTypeRef() != null) {
        addFormattedSuperClass(classData, ctx.qualifiedStaticTypeRef().getText());
      }
      addInterfaceImplements(classData, ctx.interfaceList());
    }
  }

  private void addInterfaceExtends(final ClassDataHandler classData,
      final PhpParser.InterfaceListContext interfaceList) {
    if (interfaceList == null) {
      return;
    }

    for (final PhpParser.QualifiedStaticTypeRefContext typeRef : interfaceList
        .qualifiedStaticTypeRef()) {
      classData.addImplementedInterface(normalizeNamespace(typeRef.getText()));
    }
  }

  private void addInterfaceImplements(final ClassDataHandler classData,
      final PhpParser.InterfaceListContext interfaceList) {
    addInterfaceExtends(classData, interfaceList);
  }

  private void addFormattedSuperClass(final ClassDataHandler classData, final String superClassFqn) {
    final String normalizedFqn = normalizeNamespace(superClassFqn);
    classData.setSuperClass(getClassPathFromFqn(normalizedFqn, ".php", fileDataHandler.getFileName(),
        fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(normalizedFqn));
  }

  @Override
  public void exitClassDeclaration(final PhpParser.ClassDeclarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumDeclaration(final PhpParser.EnumDeclarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String enumName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(enumName);
    fileDataHandler.enterClass(enumName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
      addInterfaceImplements(classData, ctx.interfaceList());
    }
  }

  @Override
  public void exitEnumDeclaration(final PhpParser.EnumDeclarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumItem(final PhpParser.EnumItemContext ctx) {
    if (ctx.Case() == null || ctx.identifier() == null) {
      return;
    }

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.addEnumConstant(ctx.identifier().getText());
    }
  }

  @Override
  public void enterFunctionDeclaration(final PhpParser.FunctionDeclarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    functionCount++;
    final String functionName = ctx.identifier().getText();
    final String returnType = extractFunctionReturnType(ctx);
    final MethodDataHandler methodData = createMethodHandler(functionName, returnType, ctx);
    if (methodData == null) {
      return;
    }

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterClassStatement(final PhpParser.ClassStatementContext ctx) {
    if (ctx.propertyModifiers() != null && ctx.variableInitializer() != null) {
      addPropertyFields(ctx);
      for (final PhpParser.VariableInitializerContext variable : ctx.variableInitializer()) {
        countVariableInitializer(variable);
      }
      return;
    }

    if (ctx.Function_() != null && ctx.identifier() != null) {
      functionCount++;
      final String methodName = ctx.identifier().getText();
      final String returnType = extractReturnType(ctx.returnTypeDecl());
      final MethodDataHandler methodData = createClassMethodHandler(methodName, returnType, ctx);
      if (methodData != null) {
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
      }
    }
  }

  private void addPropertyFields(final PhpParser.ClassStatementContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String fieldType = ctx.typeHint() != null ? ctx.typeHint().getText() : "mixed";
    for (final PhpParser.VariableInitializerContext variable : ctx.variableInitializer()) {
      if (variable.VarName() != null) {
        classData.addField(stripVariableName(variable.VarName().getText()), fieldType,
            new ArrayList<>());
      }
    }
  }

  @Override
  public void enterFormalParameter(final PhpParser.FormalParameterContext ctx) {
    if (ctx.variableInitializer() != null) {
      countVariableInitializer(ctx.variableInitializer());
    }
  }

  @Override
  public void enterGlobalConstantDeclaration(final PhpParser.GlobalConstantDeclarationContext ctx) {
    if (ctx.identifierInitializer() != null) {
      variableCount += ctx.identifierInitializer().size();
    }
  }

  private MethodDataHandler createMethodHandler(final String methodName, final String returnType,
      final PhpParser.FunctionDeclarationContext ctx) {
    if (fileDataHandler.isInClassContext()) {
      return createClassMethodFromParameters(methodName, returnType, ctx.formalParameterList());
    }

    final MethodDataHandler methodData = fileDataHandler.addGlobalFunction(methodName, returnType);
    addParameters(methodData, ctx.formalParameterList());
    return methodData;
  }

  private MethodDataHandler createClassMethodHandler(final String methodName,
      final String returnType, final PhpParser.ClassStatementContext ctx) {
    return createClassMethodFromParameters(methodName, returnType, ctx.formalParameterList());
  }

  private MethodDataHandler createClassMethodFromParameters(final String methodName,
      final String returnType, final PhpParser.FormalParameterListContext parameterList) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return null;
    }

    final MethodDataHandler methodData = classData.addMethod(methodName,
        methodName + "#" + extractParameterTypes(parameterList).hashCode(), returnType);
    addParameters(methodData, parameterList);
    return methodData;
  }

  private String extractFunctionReturnType(final PhpParser.FunctionDeclarationContext ctx) {
    if (ctx.typeHint() != null) {
      return ctx.typeHint().getText();
    }
    return "void";
  }

  private String extractReturnType(final PhpParser.ReturnTypeDeclContext returnTypeDecl) {
    if (returnTypeDecl == null || returnTypeDecl.typeHint() == null) {
      return "void";
    }
    return returnTypeDecl.typeHint().getText();
  }

  private List<String> extractParameterTypes(final PhpParser.FormalParameterListContext parameterList) {
    final List<String> paramTypes = new ArrayList<>();
    if (parameterList == null) {
      return paramTypes;
    }

    for (final PhpParser.FormalParameterContext param : parameterList.formalParameter()) {
      if (param.typeHint() != null) {
        paramTypes.add(param.typeHint().getText());
      }
    }
    return paramTypes;
  }

  private void addParameters(final MethodDataHandler methodData,
      final PhpParser.FormalParameterListContext parameterList) {
    if (parameterList == null) {
      return;
    }

    for (final PhpParser.FormalParameterContext param : parameterList.formalParameter()) {
      final String paramType = param.typeHint() != null ? param.typeHint().getText() : "mixed";
      String paramName = "";
      if (param.variableInitializer() != null && param.variableInitializer().VarName() != null) {
        paramName = stripVariableName(param.variableInitializer().VarName().getText());
      }
      methodData.addParameter(paramName, paramType, new ArrayList<>());
    }
  }

  private void countVariableInitializer(final PhpParser.VariableInitializerContext variable) {
    if (variable.VarName() != null) {
      variableCount++;
    }
  }

  private String stripVariableName(final String varName) {
    if (varName != null && varName.startsWith("$")) {
      return varName.substring(1);
    }
    return varName;
  }

  private String normalizeNamespace(final String namespace) {
    return namespace.replace('\\', '.');
  }

  private int getCloc() {
    if (tokens == null) {
      return 0;
    }

    int commentLines = 0;
    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);
      if (token.getChannel() == PhpLexer.PhpComments) {
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
