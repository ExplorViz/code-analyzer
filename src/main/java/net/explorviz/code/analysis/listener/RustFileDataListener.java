package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.antlr.generated.rust.RustParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.handler.RustFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from Rust source code.
 */
public class RustFileDataListener extends RustParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(RustFileDataListener.class);

  private final RustFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;
  private String currentImplType;

  public RustFileDataListener(final RustFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterCrate(final RustParser.CrateContext ctx) {
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
  public void exitCrate(final RustParser.CrateContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterModule(final RustParser.ModuleContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.enterModule(ctx.identifier().getText());
    }
  }

  @Override
  public void exitModule(final RustParser.ModuleContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveModule();
    }
  }

  @Override
  public void enterUseDeclaration(final RustParser.UseDeclarationContext ctx) {
    if (ctx.useTree() != null) {
      fileDataHandler.addImport(ctx.useTree().getText());
    }
  }

  @Override
  public void enterStructStruct(final RustParser.StructStructContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String structName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(structName);
    fileDataHandler.enterClass(structName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitStructStruct(final RustParser.StructStructContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterTupleStruct(final RustParser.TupleStructContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String structName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(structName);
    fileDataHandler.enterClass(structName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitTupleStruct(final RustParser.TupleStructContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumeration(final RustParser.EnumerationContext ctx) {
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
    }
  }

  @Override
  public void exitEnumeration(final RustParser.EnumerationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumItem(final RustParser.EnumItemContext ctx) {
    if (ctx.identifier() != null) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.identifier().getText());
      }
    }
  }

  @Override
  public void enterTrait_(final RustParser.Trait_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String traitName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(traitName);
    fileDataHandler.enterClass(traitName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsInterface();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitTrait_(final RustParser.Trait_Context ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterUnion_(final RustParser.Union_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String unionName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(unionName);
    fileDataHandler.enterClass(unionName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitUnion_(final RustParser.Union_Context ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterInherentImpl(final RustParser.InherentImplContext ctx) {
    if (ctx.type_() != null) {
      currentImplType = extractTypeName(ctx.type_());
    }
  }

  @Override
  public void exitInherentImpl(final RustParser.InherentImplContext ctx) {
    currentImplType = null;
  }

  @Override
  public void enterTraitImpl(final RustParser.TraitImplContext ctx) {
    if (ctx.type_() != null) {
      currentImplType = extractTypeName(ctx.type_());
      if (ctx.typePath() != null) {
        final ClassDataHandler classData =
            fileDataHandler.getClassData(fileDataHandler.buildFqn(currentImplType));
        if (classData != null) {
          classData.addImplementedInterface(ctx.typePath().getText());
        }
      }
    }
  }

  @Override
  public void exitTraitImpl(final RustParser.TraitImplContext ctx) {
    currentImplType = null;
  }

  @Override
  public void enterStructField(final RustParser.StructFieldContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null || ctx.identifier() == null || ctx.type_() == null) {
      return;
    }

    classData.addField(ctx.identifier().getText(), ctx.type_().getText(), new ArrayList<>());
    variableCount++;
  }

  @Override
  public void enterFunction_(final RustParser.Function_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    functionCount++;
    final String functionName = ctx.identifier().getText();
    final String returnType = extractReturnType(ctx);
    final MethodDataHandler methodData = createFunctionHandler(functionName, returnType, ctx);
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
  public void enterLetStatement(final RustParser.LetStatementContext ctx) {
    variableCount++;
  }

  @Override
  public void enterConstantItem(final RustParser.ConstantItemContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  @Override
  public void enterStaticItem(final RustParser.StaticItemContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  private MethodDataHandler createFunctionHandler(final String functionName, final String returnType,
      final RustParser.Function_Context ctx) {
    if (currentImplType != null) {
      final ClassDataHandler classData =
          fileDataHandler.getClassData(fileDataHandler.buildFqn(currentImplType));
      if (classData != null) {
        final MethodDataHandler methodData = classData.addMethod(functionName,
            functionName + "#" + extractParameterTypes(ctx).hashCode(), returnType);
        addFunctionParameters(methodData, ctx);
        return methodData;
      }
    }

    if (fileDataHandler.isInClassContext()) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        final MethodDataHandler methodData = classData.addMethod(functionName,
            functionName + "#" + extractParameterTypes(ctx).hashCode(), returnType);
        addFunctionParameters(methodData, ctx);
        return methodData;
      }
    }

    final MethodDataHandler methodData = fileDataHandler.addGlobalFunction(functionName, returnType);
    addFunctionParameters(methodData, ctx);
    return methodData;
  }

  private String extractReturnType(final RustParser.Function_Context ctx) {
    if (ctx.functionReturnType() != null && ctx.functionReturnType().type_() != null) {
      return ctx.functionReturnType().type_().getText();
    }
    return "void";
  }

  private List<String> extractParameterTypes(final RustParser.Function_Context ctx) {
    final List<String> paramTypes = new ArrayList<>();
    if (ctx.functionParameters() == null) {
      return paramTypes;
    }

    for (final RustParser.FunctionParamContext param : ctx.functionParameters().functionParam()) {
      if (param.type_() != null) {
        paramTypes.add(param.type_().getText());
      } else if (param.functionParamPattern() != null
          && param.functionParamPattern().type_() != null) {
        paramTypes.add(param.functionParamPattern().type_().getText());
      }
    }
    return paramTypes;
  }

  private void addFunctionParameters(final MethodDataHandler methodData,
      final RustParser.Function_Context ctx) {
    if (ctx.functionParameters() == null) {
      return;
    }

    for (final RustParser.FunctionParamContext param : ctx.functionParameters().functionParam()) {
      if (param.functionParamPattern() != null) {
        final RustParser.FunctionParamPatternContext pattern = param.functionParamPattern();
        final String paramType = pattern.type_() != null ? pattern.type_().getText() : "unknown";
        String paramName = pattern.pattern().getText();
        if (paramName == null || paramName.isEmpty()) {
          paramName = "";
        }
        methodData.addParameter(paramName, paramType, new ArrayList<>());
      } else if (param.type_() != null) {
        methodData.addParameter("", param.type_().getText(), new ArrayList<>());
      }
    }
  }

  private String extractTypeName(final RustParser.Type_Context typeCtx) {
    return typeCtx.getText().replace('<', '_').replace('>', '_').trim();
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
