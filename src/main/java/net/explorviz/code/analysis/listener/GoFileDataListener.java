package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.antlr.generated.golang.GoParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.GoFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.metrics.MetricsCollector;
import net.explorviz.code.analysis.metrics.golang.GoControlFlow;
import net.explorviz.code.analysis.metrics.golang.GoMembers;
import net.explorviz.code.analysis.metrics.golang.GoTokens;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR listener for extracting file data from Go source code. */
public class GoFileDataListener extends GoParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(GoFileDataListener.class);

  private final GoFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;
  private final MetricsCollector metricsCollector =
      new MetricsCollector(new GoControlFlow(), GoTokens.GRAMMAR, new GoMembers());

  /** Names of the types declared at the top level of this file. */
  private final Set<String> fileTypeNames = new HashSet<>();

  /**
   * Methods whose receiver type is declared in this file. They are attached once the whole file has
   * been walked, so that a method declared above its type is not lost.
   */
  private final List<GoParser.MethodDeclContext> pendingMethods = new ArrayList<>();

  public GoFileDataListener(
      final GoFileDataHandler fileDataHandler, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterSourceFile(final GoParser.SourceFileContext ctx) {
    metricsCollector.enterFile(ctx);
    collectFileTypeNames(ctx);

    final int sloc = getSloc(tokens);
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    LOGGER
        .atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitSourceFile(final GoParser.SourceFileContext ctx) {
    // All types of the file are known now; attach the methods in source order.
    for (final GoParser.MethodDeclContext methodDecl : pendingMethods) {
      addMethod(methodDecl);
    }
    pendingMethods.clear();

    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
    metricsCollector.exitFile(fileDataHandler);
  }

  @Override
  public void enterPackageClause(final GoParser.PackageClauseContext ctx) {
    // packageName is also used for import aliases (import pb "x/proto"), so only the package
    // clause may set the package name.
    if (ctx.packageName() != null && ctx.packageName().identifier() != null) {
      fileDataHandler.setPackageName(ctx.packageName().identifier().getText());
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

    metricsCollector.addClass(classData, ctx);
    if (ctx.type_().typeLit() != null) {
      if (ctx.type_().typeLit().structType() != null) {
        classData.setIsStruct();
      } else if (ctx.type_().typeLit().interfaceType() != null) {
        classData.setIsInterface();
      }
    }

    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
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
    methodHandler.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    metricsCollector.addFunction(methodHandler, null, ctx);

    LOGGER
        .atTrace()
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
    final String receiverType = extractReceiverType(ctx.receiver());
    if (fileTypeNames.contains(receiverType)) {
      // The receiver type may be declared further down; attach the method at the end of the file.
      pendingMethods.add(ctx);
    } else {
      addMethodOfForeignType(ctx, receiverType);
    }
  }

  /** Attaches a method to its receiver type declared in this file. */
  private void addMethod(final GoParser.MethodDeclContext ctx) {
    final String methodName = ctx.IDENTIFIER().getText();
    final String receiverType = extractReceiverType(ctx.receiver());
    final ClassDataHandler classData =
        fileDataHandler.getClassData(fileDataHandler.buildFqn(receiverType));
    if (classData == null) {
      addMethodOfForeignType(ctx, receiverType);
      return;
    }

    final String returnType = extractReturnType(ctx.signature());
    final String methodFqn = methodName + "#" + extractParameterTypes(ctx.signature()).hashCode();
    final MethodDataHandler methodData = classData.addMethod(methodName, methodFqn, returnType);
    addSignatureParameters(methodData, ctx.signature());
    setLinesAndMetrics(methodData, classData, ctx);
  }

  /**
   * Adds a method whose receiver type is declared in another file of the package. The type is not
   * known here, so the method is exported as a top-level function qualified with its receiver type
   * ({@code Type.Method}, Go's method expression syntax), like out-of-class method definitions in
   * C++ ({@code Type::method}).
   */
  private void addMethodOfForeignType(
      final GoParser.MethodDeclContext ctx, final String receiverType) {
    final String qualifiedName = receiverType + "." + ctx.IDENTIFIER().getText();
    final MethodDataHandler methodData =
        fileDataHandler.addGlobalFunction(qualifiedName, extractReturnType(ctx.signature()));
    addSignatureParameters(methodData, ctx.signature());
    setLinesAndMetrics(methodData, null, ctx);

    LOGGER.atTrace().addArgument(qualifiedName).log("Method {} on a type declared in another file");
  }

  private void setLinesAndMetrics(
      final MethodDataHandler methodData,
      final ClassDataHandler classData,
      final GoParser.MethodDeclContext ctx) {
    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    metricsCollector.addFunction(methodData, classData, ctx);
  }

  /** Collects the names of the types declared at the top level of the file. */
  private void collectFileTypeNames(final GoParser.SourceFileContext ctx) {
    for (final GoParser.DeclarationContext declaration : ctx.declaration()) {
      if (declaration.typeDecl() == null) {
        continue;
      }
      for (final GoParser.TypeSpecContext typeSpec : declaration.typeDecl().typeSpec()) {
        if (typeSpec.typeDef() != null
            && typeSpec.typeDef().IDENTIFIER() != null
            && typeSpec.typeDef().type_() != null) {
          fileTypeNames.add(typeSpec.typeDef().IDENTIFIER().getText());
        }
      }
    }
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
        final String paramType = getParameterType(param);
        final int names =
            param.identifierList() != null
                ? Math.max(1, param.identifierList().IDENTIFIER().size())
                : 1;
        for (int i = 0; i < names; i++) {
          paramTypes.add(paramType);
        }
      }
    }
    return paramTypes;
  }

  private void addSignatureParameters(
      final MethodDataHandler methodData, final GoParser.SignatureContext signature) {
    if (signature.parameters() == null) {
      return;
    }

    for (final GoParser.ParameterDeclContext param : signature.parameters().parameterDecl()) {
      final String paramType = getParameterType(param);
      if (param.identifierList() == null || param.identifierList().IDENTIFIER().isEmpty()) {
        // Unnamed parameter, e.g. func(int, string)
        methodData.addParameter("", paramType, new ArrayList<>());
        continue;
      }
      // a, b int declares two parameters sharing one type
      for (final var identifier : param.identifierList().IDENTIFIER()) {
        methodData.addParameter(identifier.getText(), paramType, new ArrayList<>());
      }
    }
  }

  /** Returns the parameter's type, keeping the {@code ...} of a variadic parameter. */
  private String getParameterType(final GoParser.ParameterDeclContext param) {
    final String type = param.type_() != null ? param.type_().getText() : "unknown";
    return param.ELLIPSIS() != null ? "..." + type : type;
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
    if (typeCtx.type_() != null) {
      // Parenthesized type, e.g. func (p (*T)) M()
      return extractTypeName(typeCtx.type_());
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
