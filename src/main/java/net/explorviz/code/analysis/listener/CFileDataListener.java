package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.c.CParser;
import net.explorviz.code.analysis.antlr.generated.c.CParserBaseListener;
import net.explorviz.code.analysis.handler.CFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.metrics.MetricsCollector;
import net.explorviz.code.analysis.metrics.c.CControlFlow;
import net.explorviz.code.analysis.metrics.c.CTokens;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR listener for extracting file data from C source code. */
public class CFileDataListener extends CParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CFileDataListener.class);
  private static final String ANONYMOUS_TYPE_NAME = "<anonymous>";
  private static final String ANONYMOUS_TYPE_SUFFIX = " " + ANONYMOUS_TYPE_NAME;

  private final CFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;
  private final MetricsCollector metricsCollector =
      new MetricsCollector(new CControlFlow(), CTokens.GRAMMAR, null);

  public CFileDataListener(final CFileDataHandler fileDataHandler, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterTranslationUnit(final CParser.TranslationUnitContext ctx) {
    metricsCollector.enterFile(ctx);
    // File-level SLOC/CLOC/includes are populated by AntlrCParserService via
    // populateLexerMetrics() before the walk (also covers parse-tree failures).
  }

  /** Populates file-level metrics from lexer tokens when a full parse tree is unavailable. */
  public void populateLexerMetrics() {
    final int sloc = getSlocIncludingPreprocessor();
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));
    extractIncludes();

    LOGGER
        .atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitTranslationUnit(final CParser.TranslationUnitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
    metricsCollector.exitFile(fileDataHandler);
  }

  private int getSlocIncludingPreprocessor() {
    if (tokens == null) {
      return 0;
    }

    final java.util.Set<Integer> codeLines = new java.util.HashSet<>();
    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);
      final String text = token.getText();
      if (text == null || text.trim().isEmpty()) {
        continue;
      }

      if (token.getChannel() == 0) {
        codeLines.add(token.getLine());
        continue;
      }

      final String trimmed = text.trim();
      if (trimmed.startsWith("#include")
          || trimmed.startsWith("#define")
          || trimmed.startsWith("#if")) {
        codeLines.add(token.getLine());
      }
    }
    return codeLines.size();
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

  /**
   * Models anonymous structs and unions nested in a type.
   *
   * <ul>
   *   <li>A named member of anonymous type ({@code struct S { struct { int x; } pos; };}) is a
   *       field {@code pos} of {@code S} with type {@code struct <anonymous>} (or {@code union
   *       <anonymous>}). The anonymous type is an inner anonymous class {@code <anonymous>} of
   *       {@code S} that owns its members ({@code x}), just like a named nested struct.
   *   <li>An unnamed anonymous member ({@code struct S { union { int i; float f; }; };}, C11) has
   *       no field. Its members are accessed as members of the enclosing type ({@code s.i}), so
   *       they stay fields of {@code S}.
   * </ul>
   */
  @Override
  public void enterStructOrUnionSpecifier(final CParser.StructOrUnionSpecifierContext ctx) {
    if (isAnonymousMemberType(ctx)) {
      fileDataHandler.enterClass(ANONYMOUS_TYPE_NAME, buildFqn(ANONYMOUS_TYPE_NAME));
      final var classData = fileDataHandler.getCurrentClassData();
      classData.setIsAnonymousClass();
      classData.addModifier(ctx.structOrUnion().getText());
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
      return;
    }

    if (ctx.structOrUnion() == null || ctx.Identifier() == null) {
      return;
    }

    // Bare "struct Tag" / "union Tag" mentions (e.g. in parameters) are not definitions.
    if (ctx.LeftBrace() == null) {
      return;
    }

    final String typeName = ctx.Identifier().getText();
    final String fqn = buildFqn(typeName);
    fileDataHandler.enterClass(typeName, fqn);

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitStructOrUnionSpecifier(final CParser.StructOrUnionSpecifierContext ctx) {
    if ((ctx.Identifier() != null && ctx.LeftBrace() != null) || isAnonymousMemberType(ctx)) {
      fileDataHandler.leaveClass();
    }
  }

  /**
   * Whether a struct/union specifier defines an anonymous type of a named member of an enclosing
   * type, e.g. {@code struct { int x; } pos;} inside a struct.
   */
  private boolean isAnonymousMemberType(final CParser.StructOrUnionSpecifierContext ctx) {
    if (ctx.structOrUnion() == null
        || ctx.Identifier() != null
        || ctx.LeftBrace() == null
        || !fileDataHandler.isInClassContext()) {
      return false;
    }
    ParserRuleContext parent = ctx.getParent();
    while (parent instanceof CParser.TypeSpecifierContext
        || parent instanceof CParser.TypeSpecifierQualifierContext
        || parent instanceof CParser.SpecifierQualifierListContext) {
      parent = parent.getParent();
    }
    return parent instanceof CParser.MemberDeclarationContext member
        && member.memberDeclaratorList() != null;
  }

  @Override
  public void enterEnumSpecifier(final CParser.EnumSpecifierContext ctx) {
    if (ctx.Identifier() == null) {
      return;
    }

    // Bare "enum Tag" mentions without a body are not definitions.
    if (ctx.LeftBrace() == null) {
      return;
    }

    final String enumName = ctx.Identifier().getText();
    final String fqn = buildFqn(enumName);

    fileDataHandler.enterClass(enumName, fqn);
    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitEnumSpecifier(final CParser.EnumSpecifierContext ctx) {
    if (ctx.Identifier() != null && ctx.LeftBrace() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumerator(final CParser.EnumeratorContext ctx) {
    // Enumerators of an anonymous enum belong to no enum type; they must not be added to the
    // enclosing struct.
    ParserRuleContext enumSpecifier = ctx.getParent();
    while (enumSpecifier != null && !(enumSpecifier instanceof CParser.EnumSpecifierContext)) {
      enumSpecifier = enumSpecifier.getParent();
    }
    if (enumSpecifier == null
        || ((CParser.EnumSpecifierContext) enumSpecifier).Identifier() == null) {
      return;
    }
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
      returnType =
          extractTypeFromDeclarationSpecifiers(ctx.declarationSpecifiers())
              + pointerSuffix(ctx.declarator());
    }

    final int functionLoc = calculateLoc(ctx);

    if (fileDataHandler.isInClassContext()) {
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final String currentClassName = getSimpleName(fileDataHandler.getCurrentClassFqnOrNull());
        final MethodDataHandler methodData;
        if (functionName.equals(currentClassName)) {
          methodData =
              classData.addConstructor(
                  functionName,
                  functionName + "#" + extractParameterTypes(ctx.declarator()).hashCode());
        } else {
          methodData =
              classData.addMethod(
                  functionName,
                  functionName + "#" + extractParameterTypes(ctx.declarator()).hashCode(),
                  returnType);
        }
        addFunctionParameters(methodData, ctx.declarator());
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LINE_COUNT, String.valueOf(functionLoc));
        metricsCollector.addFunction(methodData, classData, ctx);
      }
    } else {
      final var methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);
      addFunctionParameters(methodHandler, ctx.declarator());
      if (ctx.start != null && ctx.stop != null) {
        methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
      }
      methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      methodHandler.addMetric(LINE_COUNT, String.valueOf(functionLoc));
      metricsCollector.addFunction(methodHandler, null, ctx);
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
    for (final CParser.MemberDeclaratorContext memberDecl :
        ctx.memberDeclaratorList().memberDeclarator()) {
      if (memberDecl.declarator() != null) {
        final String fieldName = extractDeclaratorName(memberDecl.declarator());
        if (fieldName != null && !fieldName.contains("(")) {
          classData.addField(
              fieldName, fieldType + pointerSuffix(memberDecl.declarator()), new ArrayList<>());
        }
      }
    }
  }

  /** Counts declared variables, ignoring typedefs and function prototypes. */
  @Override
  public void enterDeclaration(final CParser.DeclarationContext ctx) {
    if (ctx.initDeclaratorList() == null || isTypedef(ctx.declarationSpecifiers())) {
      return;
    }
    for (final CParser.InitDeclaratorContext initDecl : ctx.initDeclaratorList().initDeclarator()) {
      if (initDecl.declarator() != null && !isFunctionDeclarator(initDecl.declarator())) {
        variableCount++;
      }
    }
  }

  private static boolean isTypedef(final CParser.DeclarationSpecifiersContext ctx) {
    if (ctx == null) {
      return false;
    }
    for (final CParser.DeclarationSpecifierContext declSpec : ctx.declarationSpecifier()) {
      if (declSpec.storageClassSpecifier() != null
          && "typedef".equals(declSpec.storageClassSpecifier().getText())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether a declarator declares a function (as opposed to e.g. a pointer to a function): the
   * identifier is directly followed by a parameter list.
   */
  private static boolean isFunctionDeclarator(final CParser.DeclaratorContext ctx) {
    final CParser.DirectDeclaratorContext direct = ctx.directDeclarator();
    if (direct == null || direct.Identifier() == null || direct.getChildCount() < 2) {
      return false;
    }
    // The child after the identifier (and its optional attributes) must be '('
    for (int i = 1; i < direct.getChildCount(); i++) {
      final ParseTree child = direct.getChild(i);
      if (child instanceof CParser.AttributeSpecifierSequenceContext) {
        continue;
      }
      return child instanceof TerminalNode && "(".equals(child.getText());
    }
    return false;
  }

  /** Returns "*" per pointer level of a declarator, e.g. "**" for {@code **argv}. */
  private static String pointerSuffix(final CParser.DeclaratorContext ctx) {
    if (ctx == null) {
      return "";
    }
    final StringBuilder suffix = new StringBuilder();
    for (final CParser.PointerContext pointer : ctx.pointer()) {
      appendPointerStars(suffix, pointer);
    }
    return suffix.toString();
  }

  private static void appendPointerStars(
      final StringBuilder suffix, final CParser.PointerContext pointer) {
    if (pointer == null) {
      return;
    }
    for (int i = 0; i < pointer.getChildCount(); i++) {
      if ("*".equals(pointer.getChild(i).getText())) {
        suffix.append('*');
      }
    }
  }

  /** Returns the pointer suffix of a parameter's declarator or abstract declarator. */
  private static String parameterPointerSuffix(final CParser.ParameterDeclarationContext param) {
    if (param.declarator() != null) {
      return pointerSuffix(param.declarator());
    }
    if (param.abstractDeclarator() != null) {
      final StringBuilder suffix = new StringBuilder();
      appendPointerStars(suffix, param.abstractDeclarator().pointer());
      return suffix.toString();
    }
    return "";
  }

  /**
   * Returns the parameter declarations of a parameter list. An empty list {@code ()} and {@code
   * (void)} declare no parameters.
   */
  private static List<CParser.ParameterDeclarationContext> getParameterDeclarations(
      final CParser.ParameterTypeListContext params) {
    final List<CParser.ParameterDeclarationContext> result = new ArrayList<>();
    if (params == null || params.parameterList() == null) {
      return result;
    }
    for (final CParser.ParameterDeclarationContext param :
        params.parameterList().parameterDeclaration()) {
      if (param.declarationSpecifiers() == null
          && param.declarator() == null
          && param.abstractDeclarator() == null) {
        continue;
      }
      result.add(param);
    }
    if (result.size() == 1) {
      final CParser.ParameterDeclarationContext only = result.get(0);
      if (only.declarator() == null
          && only.abstractDeclarator() == null
          && only.declarationSpecifiers() != null
          && "void".equals(only.declarationSpecifiers().getText())) {
        result.clear();
      }
    }
    return result;
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
        appendTypeText(typeBuilder, typeSpecifierText(declSpec.typeSpecifier()));
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
        appendTypeText(typeBuilder, typeSpecifierText(specQual.typeSpecifier()));
      }
    }

    final String result = typeBuilder.toString().trim();
    return result.isEmpty() ? "unknown" : result;
  }

  /**
   * Returns the text of a type specifier with spaces between words, e.g. {@code struct Node}. A
   * struct, union or enum definition is reduced to its keyword and tag.
   */
  private static String typeSpecifierText(final CParser.TypeSpecifierContext ctx) {
    final CParser.StructOrUnionSpecifierContext struct = ctx.structOrUnionSpecifier();
    if (struct != null && struct.structOrUnion() != null) {
      if (struct.Identifier() != null) {
        return struct.structOrUnion().getText() + " " + struct.Identifier().getText();
      }
      return struct.structOrUnion().getText() + ANONYMOUS_TYPE_SUFFIX;
    }
    final CParser.EnumSpecifierContext enumSpec = ctx.enumSpecifier();
    if (enumSpec != null) {
      return enumSpec.Identifier() != null ? "enum " + enumSpec.Identifier().getText() : "enum";
    }
    return joinTokens(ctx);
  }

  /** Joins the tokens of a subtree, separating adjacent words by a space. */
  private static String joinTokens(final ParseTree tree) {
    final StringBuilder result = new StringBuilder();
    appendTokens(result, tree);
    return result.toString();
  }

  private static void appendTokens(final StringBuilder result, final ParseTree tree) {
    if (tree instanceof TerminalNode) {
      final String text = tree.getText();
      if (text.isEmpty() || "<EOF>".equals(text)) {
        return;
      }
      if (result.length() > 0
          && isWordChar(result.charAt(result.length() - 1))
          && isWordChar(text.charAt(0))) {
        result.append(' ');
      }
      result.append(text);
      return;
    }
    for (int i = 0; i < tree.getChildCount(); i++) {
      appendTokens(result, tree.getChild(i));
    }
  }

  private static boolean isWordChar(final char c) {
    return Character.isLetterOrDigit(c) || c == '_';
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
    if ("static".equals(text)
        || "extern".equals(text)
        || "inline".equals(text)
        || "typedef".equals(text)
        || "const".equals(text)
        || "volatile".equals(text)) {
      return;
    }
    appendTypeText(typeBuilder, text);
  }

  private List<String> extractParameterTypes(final CParser.DeclaratorContext ctx) {
    final List<String> paramTypes = new ArrayList<>();
    for (final CParser.ParameterDeclarationContext param :
        getParameterDeclarations(findParameterTypeList(ctx))) {
      paramTypes.add(extractParameterType(param));
    }
    return paramTypes;
  }

  private String extractParameterType(final CParser.ParameterDeclarationContext param) {
    if (param.declarationSpecifiers() == null) {
      return "unknown";
    }
    return extractTypeFromDeclarationSpecifiers(param.declarationSpecifiers())
        + parameterPointerSuffix(param);
  }

  private CParser.ParameterTypeListContext findParameterTypeList(
      final CParser.DeclaratorContext ctx) {
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

  private void addFunctionParameters(
      final MethodDataHandler methodData, final CParser.DeclaratorContext ctx) {
    for (final CParser.ParameterDeclarationContext param :
        getParameterDeclarations(findParameterTypeList(ctx))) {
      final String paramType = extractParameterType(param);
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
