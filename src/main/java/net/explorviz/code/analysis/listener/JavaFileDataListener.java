package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserBaseListener;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.types.Verification;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR Listener-based implementation for extracting file data from Java source code.
 */
public class JavaFileDataListener extends JavaParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaFileDataListener.class);

  private final JavaFileDataHandler fileDataHandler;
  private final boolean wildcardImportProperty;
  private int wildcardImportCount;
  private String wildcardImport;
  private String currentPackage = "";
  private final org.antlr.v4.runtime.CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public JavaFileDataListener(final JavaFileDataHandler fileDataHandler,
      final boolean wildcardImportProperty,
      final org.antlr.v4.runtime.CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.wildcardImportProperty = wildcardImportProperty;
    this.wildcardImportCount = 0;
    this.wildcardImport = null;
    this.tokens = tokens;
  }

  @Override
  public void enterCompilationUnit(final JavaParser.CompilationUnitContext ctx) {
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
  public void exitCompilationUnit(final JavaParser.CompilationUnitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterPackageDeclaration(final JavaParser.PackageDeclarationContext ctx) {
    if (ctx.qualifiedName() != null) {
      currentPackage = getQualifiedName(ctx.qualifiedName());
      fileDataHandler.setPackageName(currentPackage);
    }
  }

  @Override
  public void enterImportDeclaration(final JavaParser.ImportDeclarationContext ctx) {
    if (ctx.qualifiedName() == null) {
      return;
    }

    String importName = getQualifiedName(ctx.qualifiedName());
    if (ctx.MUL() != null) {
      importName += ".*";
      if (wildcardImportCount == 0 && wildcardImport == null) {
        wildcardImport = getQualifiedName(ctx.qualifiedName());
      }
      wildcardImportCount++;
    }

    fileDataHandler.addImport(importName);
  }

  @Override
  public void enterClassDeclaration(final JavaParser.ClassDeclarationContext ctx) {
    final String className = ctx.identifier().getText();
    final String fqn = buildFqn(className);

    fileDataHandler.enterClass(className, fqn);

    final List<? extends ParserRuleContext> typeModifiers = getTypeModifiers(ctx);
    final boolean isAbstract = hasModifier(typeModifiers, "abstract");
    if (isAbstract) {
      fileDataHandler.getCurrentClassData().setIsAbstractClass();
    } else {
      fileDataHandler.getCurrentClassData().setIsClass();
    }

    addModifiers(typeModifiers);
    fileDataHandler.getCurrentClassData().addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    fileDataHandler.getCurrentClassData().addMetric(LOC, String.valueOf(getLoc(ctx)));

    if (ctx.typeType() != null) {
      final String superClassFqn = resolveTypeName(ctx.typeType().getText());
      fileDataHandler.getCurrentClassData()
          .setSuperClass(getClassPathFromFqn(superClassFqn, ".java", fileDataHandler.getFileName(),
              fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
    }

    if (ctx.IMPLEMENTS() != null && !ctx.typeList().isEmpty()) {
      for (final JavaParser.TypeTypeContext typeCtx : ctx.typeList(0).typeType()) {
        fileDataHandler.getCurrentClassData().addImplementedInterface(resolveTypeName(typeCtx.getText()));
      }
    }
  }

  @Override
  public void exitClassDeclaration(final JavaParser.ClassDeclarationContext ctx) {
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext ctx) {
    final String interfaceName = ctx.identifier().getText();
    final String fqn = buildFqn(interfaceName);

    fileDataHandler.enterClass(interfaceName, fqn);
    fileDataHandler.getCurrentClassData().setIsInterface();

    addModifiers(getTypeModifiers(ctx));
    fileDataHandler.getCurrentClassData().addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    fileDataHandler.getCurrentClassData().addMetric(LOC, String.valueOf(getLoc(ctx)));

    if (ctx.EXTENDS() != null && !ctx.typeList().isEmpty()) {
      for (final JavaParser.TypeTypeContext typeCtx : ctx.typeList(0).typeType()) {
        fileDataHandler.getCurrentClassData().addImplementedInterface(resolveTypeName(typeCtx.getText()));
      }
    }
  }

  @Override
  public void exitInterfaceDeclaration(final JavaParser.InterfaceDeclarationContext ctx) {
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterEnumDeclaration(final JavaParser.EnumDeclarationContext ctx) {
    final String enumName = ctx.identifier().getText();
    final String fqn = buildFqn(enumName);

    fileDataHandler.enterClass(enumName, fqn);
    fileDataHandler.getCurrentClassData().setIsEnum();

    addModifiers(getTypeModifiers(ctx));
    fileDataHandler.getCurrentClassData().addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    fileDataHandler.getCurrentClassData().addMetric(LOC, String.valueOf(getLoc(ctx)));
  }

  @Override
  public void exitEnumDeclaration(final JavaParser.EnumDeclarationContext ctx) {
    fileDataHandler.leaveClass();
  }

  @Override
  public void enterEnumConstant(final JavaParser.EnumConstantContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.getCurrentClassData().addEnumConstant(ctx.identifier().getText());
    }
  }

  @Override
  public void enterFieldDeclaration(final JavaParser.FieldDeclarationContext ctx) {
    if (ctx.variableDeclarators() == null || ctx.typeType() == null) {
      return;
    }

    final String fieldType = resolveTypeName(ctx.typeType().getText());
    final List<String> modifiers = extractMemberModifiers(ctx);

    for (final JavaParser.VariableDeclaratorContext varCtx : ctx.variableDeclarators().variableDeclarator()) {
      final String fieldName = varCtx.variableDeclaratorId().identifier().getText();
      final String fieldFqn = fileDataHandler.getCurrentClassFqn() + "." + fieldName;
      fileDataHandler.enterMethod(fieldFqn);
      fileDataHandler.getCurrentClassData().addField(fieldName, fieldType, modifiers);
      fileDataHandler.leaveMethod();
      variableCount++;
    }
  }

  @Override
  public void enterMethodDeclaration(final JavaParser.MethodDeclarationContext ctx) {
    if (ctx.identifier() == null || ctx.formalParameters() == null) {
      return;
    }

    final String methodName = ctx.identifier().getText();
    final List<String> parameterTypes = extractParameterTypes(ctx.formalParameters());
    final String parameterHash = Verification.parameterHash(parameterTypes);
    final String methodFqn = fileDataHandler.getCurrentClassFqn() + "." + methodName
        + "#" + parameterHash;

    fileDataHandler.enterMethod(methodFqn);
    functionCount++;

    String returnType = "void";
    if (ctx.typeTypeOrVoid() != null) {
      returnType = resolveTypeName(ctx.typeTypeOrVoid().getText());
    }

    final MethodDataHandler methodData = fileDataHandler.getCurrentClassData()
        .addMethod(methodName, methodFqn, returnType);

    for (final String modifier : extractMemberModifiers(ctx)) {
      methodData.addModifier(modifier);
    }

    addParameters(methodData, ctx.formalParameters());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }

    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LOC, String.valueOf(getLoc(ctx)));
  }

  @Override
  public void exitMethodDeclaration(final JavaParser.MethodDeclarationContext ctx) {
    fileDataHandler.leaveMethod();
  }

  @Override
  public void enterInterfaceMethodDeclaration(
      final JavaParser.InterfaceMethodDeclarationContext ctx) {
    if (ctx.interfaceCommonBodyDeclaration() == null) {
      return;
    }

    final JavaParser.InterfaceCommonBodyDeclarationContext body = ctx.interfaceCommonBodyDeclaration();
    if (body.identifier() == null || body.formalParameters() == null) {
      return;
    }

    final String methodName = body.identifier().getText();
    final List<String> parameterTypes = extractParameterTypes(body.formalParameters());
    final String parameterHash = Verification.parameterHash(parameterTypes);
    final String methodFqn = fileDataHandler.getCurrentClassFqn() + "." + methodName
        + "#" + parameterHash;

    fileDataHandler.enterMethod(methodFqn);
    functionCount++;

    String returnType = "void";
    if (body.typeTypeOrVoid() != null) {
      returnType = resolveTypeName(body.typeTypeOrVoid().getText());
    }

    final MethodDataHandler methodData = fileDataHandler.getCurrentClassData()
        .addMethod(methodName, methodFqn, returnType);

    for (final String modifier : extractInterfaceMethodModifiers(ctx)) {
      methodData.addModifier(modifier);
    }

    addParameters(methodData, body.formalParameters());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }

    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LOC, String.valueOf(getLoc(ctx)));
  }

  @Override
  public void exitInterfaceMethodDeclaration(
      final JavaParser.InterfaceMethodDeclarationContext ctx) {
    fileDataHandler.leaveMethod();
  }

  @Override
  public void enterConstructorDeclaration(final JavaParser.ConstructorDeclarationContext ctx) {
    if (ctx.identifier() == null || ctx.formalParameters() == null) {
      return;
    }

    final String constructorName = ctx.identifier().getText();
    final List<String> parameterTypes = extractParameterTypes(ctx.formalParameters());
    final String parameterHash = Verification.parameterHash(parameterTypes);
    final String constructorFqn = fileDataHandler.getCurrentClassFqn() + "." + constructorName
        + "#" + parameterHash;

    fileDataHandler.enterMethod(constructorFqn);
    functionCount++;

    final MethodDataHandler constructor = fileDataHandler.getCurrentClassData()
        .addConstructor(constructorName, constructorFqn);

    for (final String modifier : extractMemberModifiers(ctx)) {
      constructor.addModifier(modifier);
    }

    addParameters(constructor, ctx.formalParameters());

    if (ctx.start != null && ctx.stop != null) {
      constructor.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }

    constructor.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    constructor.addMetric(LOC, String.valueOf(getLoc(ctx)));
  }

  @Override
  public void exitConstructorDeclaration(final JavaParser.ConstructorDeclarationContext ctx) {
    fileDataHandler.leaveMethod();
  }

  private String buildFqn(final String className) {
    if (currentPackage.isEmpty()) {
      return className;
    }

    try {
      final String currentFqn = fileDataHandler.getCurrentClassFqn();
      return currentFqn + "." + className;
    } catch (IllegalStateException e) {
      return currentPackage + "." + className;
    }
  }

  private String getQualifiedName(final JavaParser.QualifiedNameContext ctx) {
    return ctx.identifier().stream()
        .map(JavaParser.IdentifierContext::getText)
        .collect(Collectors.joining("."));
  }

  private List<? extends ParserRuleContext> getTypeModifiers(final ParserRuleContext ctx) {
    final ParseTree parent = ctx.getParent();
    if (parent instanceof JavaParser.TypeDeclarationContext typeDeclarationContext) {
      return typeDeclarationContext.classOrInterfaceModifier();
    }
    if (parent instanceof JavaParser.LocalTypeDeclarationContext localTypeDeclarationContext) {
      return localTypeDeclarationContext.classOrInterfaceModifier();
    }
    return List.of();
  }

  private List<String> extractMemberModifiers(final ParserRuleContext ctx) {
    final List<String> modifiers = new ArrayList<>();
    for (final ParserRuleContext modCtx : getMemberModifiers(ctx)) {
      final String modText = modCtx.getText();
      if (!modText.startsWith("@")) {
        modifiers.add(modText);
      }
    }
    return modifiers;
  }

  private List<? extends ParserRuleContext> getMemberModifiers(final ParserRuleContext ctx) {
    ParseTree parent = ctx.getParent();
    if (parent instanceof JavaParser.MemberDeclarationContext) {
      parent = parent.getParent();
    }
    if (parent instanceof JavaParser.ClassBodyDeclarationContext classBodyDeclarationContext) {
      return classBodyDeclarationContext.modifier();
    }
    if (parent instanceof JavaParser.InterfaceBodyDeclarationContext interfaceBodyDeclarationContext) {
      return interfaceBodyDeclarationContext.modifier();
    }
    return List.of();
  }

  private boolean hasModifier(final List<? extends ParserRuleContext> modifiers,
      final String modifier) {
    if (modifiers == null) {
      return false;
    }
    for (final ParserRuleContext modCtx : modifiers) {
      if (modCtx.getText().equals(modifier)) {
        return true;
      }
    }
    return false;
  }

  private void addModifiers(final List<? extends ParserRuleContext> modifiers) {
    if (modifiers == null) {
      return;
    }
    for (final ParserRuleContext modCtx : modifiers) {
      final String modText = modCtx.getText();
      if (!modText.startsWith("@")) {
        fileDataHandler.getCurrentClassData().addModifier(modText);
      }
    }
  }

  private List<String> extractInterfaceMethodModifiers(
      final JavaParser.InterfaceMethodDeclarationContext ctx) {
    final List<String> modifiers = new ArrayList<>();
    if (ctx.interfaceMethodModifier() != null) {
      for (final JavaParser.InterfaceMethodModifierContext modCtx : ctx.interfaceMethodModifier()) {
        final String modText = modCtx.getText();
        if (!modText.startsWith("@")) {
          modifiers.add(modText);
        }
      }
    }
    return modifiers;
  }

  private List<String> extractParameterTypes(final JavaParser.FormalParametersContext ctx) {
    final List<String> parameterTypes = new ArrayList<>();
    if (ctx == null) {
      return parameterTypes;
    }

    for (final JavaParser.FormalParameterContext paramCtx : collectFormalParameters(ctx)) {
      parameterTypes.add(paramCtx.typeType().getText());
    }

    return parameterTypes;
  }

  private void addParameters(final MethodDataHandler methodData,
      final JavaParser.FormalParametersContext ctx) {
    if (ctx == null) {
      return;
    }

    for (final JavaParser.FormalParameterContext paramCtx : collectFormalParameters(ctx)) {
      final String paramName = paramCtx.variableDeclaratorId().identifier().getText();
      final String paramType = resolveTypeName(paramCtx.typeType().getText());
      final List<String> modifiers = extractParameterModifiers(paramCtx);
      methodData.addParameter(paramName, paramType, modifiers);
    }
  }

  private List<JavaParser.FormalParameterContext> collectFormalParameters(
      final JavaParser.FormalParametersContext ctx) {
    final List<JavaParser.FormalParameterContext> parameters = new ArrayList<>();

    if (ctx.receiverParameter() != null) {
      // Skip receiver parameter (this)
    }

    if (ctx.formalParameter() != null) {
      parameters.add(ctx.formalParameter());
    }

    for (final JavaParser.FormalParameterListContext paramListCtx : ctx.formalParameterList()) {
      parameters.addAll(paramListCtx.formalParameter());
    }

    return parameters;
  }

  private List<String> extractParameterModifiers(final JavaParser.FormalParameterContext ctx) {
    final List<String> modifiers = new ArrayList<>();
    if (ctx.variableModifier() != null) {
      for (final JavaParser.VariableModifierContext modCtx : ctx.variableModifier()) {
        final String modText = modCtx.getText();
        if (!modText.startsWith("@")) {
          modifiers.add(modText);
        }
      }
    }
    return modifiers;
  }

  private String resolveTypeName(final String typeName) {
    final List<String> imports = fileDataHandler.getImportNames();

    String baseType = typeName.replaceAll("\\[\\]", "");
    final String arraySuffix = typeName.substring(baseType.length());

    final int genericStart = baseType.indexOf('<');
    String genericsPart = "";
    if (genericStart != -1) {
      genericsPart = baseType.substring(genericStart);
      baseType = baseType.substring(0, genericStart);
    }

    if (isPrimitiveType(baseType)) {
      return typeName;
    }

    if (baseType.contains(".")) {
      return typeName;
    }

    for (final String importName : imports) {
      if (importName.endsWith("." + baseType)) {
        return importName + genericsPart + arraySuffix;
      }
    }

    if (wildcardImportProperty && wildcardImportCount == 1 && wildcardImport != null) {
      return wildcardImport + "." + baseType + genericsPart + arraySuffix;
    }

    final List<String> javaLangTypes = Arrays.asList(
        "String", "Integer", "Long", "Double", "Float", "Boolean", "Character",
        "Byte", "Short", "Object", "Class", "System", "Math", "Thread",
        "Runnable", "Exception", "RuntimeException", "Error");
    if (javaLangTypes.contains(baseType)) {
      return "java.lang." + baseType + genericsPart + arraySuffix;
    }

    if (!currentPackage.isEmpty()) {
      return currentPackage + "." + typeName;
    }

    return typeName;
  }

  @Override
  public void enterLocalVariableDeclaration(final JavaParser.LocalVariableDeclarationContext ctx) {
    if (ctx.variableDeclarators() != null) {
      variableCount += ctx.variableDeclarators().variableDeclarator().size();
    }
    if (ctx.VAR() != null && ctx.identifier() != null) {
      variableCount++;
    }
  }

  private boolean isPrimitiveType(final String type) {
    return Arrays.asList("byte", "short", "int", "long", "float", "double",
        "boolean", "char", "void").contains(type);
  }

  private int getCloc(final ParserRuleContext ctx) {
    if (ctx == null || tokens == null) {
      return 0;
    }

    int commentLines = 0;

    for (int i = 0; i < tokens.size(); i++) {
      final org.antlr.v4.runtime.Token token = tokens.get(i);

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
