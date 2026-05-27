package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.c.CParser;
import net.explorviz.code.analysis.antlr.generated.c.CParserBaseListener;
import net.explorviz.code.analysis.handler.CFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from C source code.
 */
public class CFileDataListener extends CParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CFileDataListener.class);

  private final CFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public CFileDataListener(final CFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterTranslationUnit(final CParser.TranslationUnitContext ctx) {
    final int sloc = getSloc(tokens);
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));
    extractIncludes();

    LOGGER.atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitTranslationUnit(final CParser.TranslationUnitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
  }

  private void extractIncludes() {
    if (tokens == null) {
      return;
    }

    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);
      if (token.getChannel() != 0) {
        final String text = token.getText();
        if (text != null) {
          final String trimmed = text.trim();
          if (trimmed.startsWith("#include")) {
            final String includeName = trimmed.substring("#include".length()).trim();
            if (!includeName.isEmpty()) {
              fileDataHandler.addImport(includeName);
            }
          }
        }
      }
    }
  }

  @Override
  public void enterStructOrUnionSpecifier(final CParser.StructOrUnionSpecifierContext ctx) {
    if (ctx.structOrUnion() == null) {
      return;
    }

    String typeName = null;
    if (ctx.Identifier() != null) {
      typeName = ctx.Identifier().getText();
    }

    if (typeName == null) {
      return;
    }

    final String fqn = buildFqn(typeName);
    fileDataHandler.enterClass(typeName, fqn);

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      final boolean isUnion = ctx.structOrUnion().Union() != null;
      if (isUnion) {
        classData.setIsStruct();
      } else {
        classData.setIsStruct();
      }
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitStructOrUnionSpecifier(final CParser.StructOrUnionSpecifierContext ctx) {
    if (ctx.Identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumSpecifier(final CParser.EnumSpecifierContext ctx) {
    if (ctx.Identifier() == null) {
      return;
    }

    final String enumName = ctx.Identifier().getText();
    final String fqn = buildFqn(enumName);

    fileDataHandler.enterClass(enumName, fqn);
    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitEnumSpecifier(final CParser.EnumSpecifierContext ctx) {
    if (ctx.Identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumerator(final CParser.EnumeratorContext ctx) {
    if (ctx.enumerationConstant() != null && ctx.enumerationConstant().Identifier() != null) {
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.enumerationConstant().Identifier().getText());
      }
    }
  }

  @Override
  public void enterFunctionDefinition(final CParser.FunctionDefinitionContext ctx) {
    if (ctx.declarator() == null) {
      return;
    }

    functionCount++;

    final String functionName = extractFunctionName(ctx.declarator());
    if (functionName == null) {
      return;
    }

    String returnType = "void";
    if (ctx.declarationSpecifiers() != null) {
      returnType = extractTypeFromDeclarationSpecifiers(ctx.declarationSpecifiers());
    }

    final int functionLoc = calculateLoc(ctx);

    if (fileDataHandler.isInClassContext()) {
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final String currentClassName = getSimpleName(fileDataHandler.getCurrentClassFqnOrNull());
        final MethodDataHandler methodData;
        if (functionName.equals(currentClassName)) {
          methodData = classData.addConstructor(functionName,
              functionName + "#" + extractParameterTypes(ctx.declarator()).hashCode());
        } else {
          methodData = classData.addMethod(functionName,
              functionName + "#" + extractParameterTypes(ctx.declarator()).hashCode(), returnType);
        }
        addFunctionParameters(methodData, ctx.declarator());
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(functionLoc));
      }
    } else {
      final var methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);
      addFunctionParameters(methodHandler, ctx.declarator());
      if (ctx.start != null && ctx.stop != null) {
        methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
      }
      methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      methodHandler.addMetric(LOC, String.valueOf(functionLoc));
    }
  }

  @Override
  public void enterMemberDeclaration(final CParser.MemberDeclarationContext ctx) {
    if (ctx.specifierQualifierList() == null || ctx.memberDeclaratorList() == null) {
      return;
    }

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String fieldType = extractTypeFromSpecifierQualifierList(ctx.specifierQualifierList());
    for (final CParser.MemberDeclaratorContext memberDecl : ctx.memberDeclaratorList()
        .memberDeclarator()) {
      if (memberDecl.declarator() != null) {
        final String fieldName = extractDeclaratorName(memberDecl.declarator());
        if (fieldName != null && !fieldName.contains("(")) {
          classData.addField(fieldName, fieldType, new ArrayList<>());
        }
      }
    }
  }

  @Override
  public void enterDeclaration(final CParser.DeclarationContext ctx) {
    if (ctx.initDeclaratorList() != null) {
      variableCount += ctx.initDeclaratorList().initDeclarator().size();
    }
  }

  private String buildFqn(final String name) {
    final String currentClassFqn = fileDataHandler.getCurrentClassFqnOrNull();
    if (currentClassFqn != null) {
      return currentClassFqn + "." + name;
    }
    return name;
  }

  private String getSimpleName(final String fqn) {
    if (fqn == null) {
      return null;
    }
    final int lastSep = fqn.lastIndexOf('.');
    return lastSep >= 0 ? fqn.substring(lastSep + 1) : fqn;
  }

  private String extractFunctionName(final CParser.DeclaratorContext ctx) {
    if (ctx.directDeclarator() != null) {
      return extractNameFromDirectDeclarator(ctx.directDeclarator());
    }
    return null;
  }

  private String extractDeclaratorName(final CParser.DeclaratorContext ctx) {
    if (ctx.directDeclarator() != null) {
      return extractNameFromDirectDeclarator(ctx.directDeclarator());
    }
    return null;
  }

  private String extractNameFromDirectDeclarator(final CParser.DirectDeclaratorContext ctx) {
    if (ctx.Identifier() != null) {
      return ctx.Identifier().getText();
    }
    if (ctx.declarator() != null && ctx.declarator().directDeclarator() != null) {
      return extractNameFromDirectDeclarator(ctx.declarator().directDeclarator());
    }
    return null;
  }

  private String extractTypeFromDeclarationSpecifiers(
      final CParser.DeclarationSpecifiersContext ctx) {
    if (ctx == null) {
      return "void";
    }

    final StringBuilder typeBuilder = new StringBuilder();
    for (final CParser.DeclarationSpecifierContext declSpec : ctx.declarationSpecifier()) {
      if (declSpec.typeSpecifier() != null) {
        appendTypeText(typeBuilder, declSpec.typeSpecifier().getText());
      } else {
        appendStorageOrQualifier(typeBuilder, declSpec.getText());
      }
    }

    final String result = typeBuilder.toString().trim();
    return result.isEmpty() ? "void" : result;
  }

  private String extractTypeFromSpecifierQualifierList(
      final CParser.SpecifierQualifierListContext ctx) {
    if (ctx == null) {
      return "unknown";
    }

    final StringBuilder typeBuilder = new StringBuilder();
    for (final CParser.TypeSpecifierQualifierContext specQual : ctx.typeSpecifierQualifier()) {
      if (specQual.typeSpecifier() != null) {
        appendTypeText(typeBuilder, specQual.typeSpecifier().getText());
      }
    }

    final String result = typeBuilder.toString().trim();
    return result.isEmpty() ? "unknown" : result;
  }

  private void appendTypeText(final StringBuilder typeBuilder, final String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    if (typeBuilder.length() > 0) {
      typeBuilder.append(' ');
    }
    typeBuilder.append(text);
  }

  private void appendStorageOrQualifier(final StringBuilder typeBuilder, final String text) {
    if ("static".equals(text) || "extern".equals(text) || "inline".equals(text)
        || "typedef".equals(text) || "const".equals(text) || "volatile".equals(text)) {
      return;
    }
    appendTypeText(typeBuilder, text);
  }

  private List<String> extractParameterTypes(final CParser.DeclaratorContext ctx) {
    final List<String> paramTypes = new ArrayList<>();
    final CParser.ParameterTypeListContext params = findParameterTypeList(ctx);
    if (params != null && params.parameterList() != null) {
      for (final CParser.ParameterDeclarationContext param : params.parameterList()
          .parameterDeclaration()) {
        String type = "unknown";
        if (param.declarationSpecifiers() != null) {
          type = extractTypeFromDeclarationSpecifiers(param.declarationSpecifiers());
        }
        paramTypes.add(type);
      }
    }
    return paramTypes;
  }

  private CParser.ParameterTypeListContext findParameterTypeList(final CParser.DeclaratorContext ctx) {
    if (ctx.directDeclarator() != null) {
      return findParameterTypeListInDirect(ctx.directDeclarator());
    }
    return null;
  }

  private CParser.ParameterTypeListContext findParameterTypeListInDirect(
      final CParser.DirectDeclaratorContext ctx) {
    if (ctx.parameterTypeList() != null && !ctx.parameterTypeList().isEmpty()) {
      return ctx.parameterTypeList().get(ctx.parameterTypeList().size() - 1);
    }
    if (ctx.declarator() != null && ctx.declarator().directDeclarator() != null) {
      return findParameterTypeListInDirect(ctx.declarator().directDeclarator());
    }
    return null;
  }

  private void addFunctionParameters(final MethodDataHandler methodData,
      final CParser.DeclaratorContext ctx) {
    final CParser.ParameterTypeListContext params = findParameterTypeList(ctx);
    if (params == null || params.parameterList() == null) {
      return;
    }

    for (final CParser.ParameterDeclarationContext param : params.parameterList()
        .parameterDeclaration()) {
      String paramType = param.declarationSpecifiers() != null
          ? extractTypeFromDeclarationSpecifiers(param.declarationSpecifiers())
          : "unknown";
      String paramName = "";
      if (param.declarator() != null) {
        paramName = extractDeclaratorName(param.declarator());
        if (paramName == null) {
          paramName = "";
        }
      }
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
