package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5Parser;
import net.explorviz.code.analysis.antlr.generated.swift.Swift5ParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.handler.SwiftFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from Swift source code.
 */
public class SwiftFileDataListener extends Swift5ParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(SwiftFileDataListener.class);

  private final SwiftFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public SwiftFileDataListener(final SwiftFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterTop_level(final Swift5Parser.Top_levelContext ctx) {
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
  public void exitTop_level(final Swift5Parser.Top_levelContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterImport_declaration(final Swift5Parser.Import_declarationContext ctx) {
    if (ctx.import_path() != null) {
      fileDataHandler.addImport(ctx.import_path().getText());
    }
  }

  @Override
  public void enterClass_declaration(final Swift5Parser.Class_declarationContext ctx) {
    if (ctx.class_name() == null || ctx.class_name().identifier() == null) {
      return;
    }

    enterType(ctx.class_name().identifier().getText(), ctx, false, false);
    addInheritanceRelations(fileDataHandler.getCurrentClassData(), ctx.type_inheritance_clause(),
        false);
  }

  @Override
  public void exitClass_declaration(final Swift5Parser.Class_declarationContext ctx) {
    if (ctx.class_name() != null && ctx.class_name().identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterStruct_declaration(final Swift5Parser.Struct_declarationContext ctx) {
    if (ctx.struct_name() == null || ctx.struct_name().identifier() == null) {
      return;
    }

    enterType(ctx.struct_name().identifier().getText(), ctx, true, false);
    addInheritanceRelations(fileDataHandler.getCurrentClassData(), ctx.type_inheritance_clause(),
        true);
  }

  @Override
  public void exitStruct_declaration(final Swift5Parser.Struct_declarationContext ctx) {
    if (ctx.struct_name() != null && ctx.struct_name().identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterProtocol_declaration(final Swift5Parser.Protocol_declarationContext ctx) {
    if (ctx.protocol_name() == null || ctx.protocol_name().identifier() == null) {
      return;
    }

    enterType(ctx.protocol_name().identifier().getText(), ctx, false, true);
    addInheritanceRelations(fileDataHandler.getCurrentClassData(), ctx.type_inheritance_clause(),
        true);
  }

  @Override
  public void exitProtocol_declaration(final Swift5Parser.Protocol_declarationContext ctx) {
    if (ctx.protocol_name() != null && ctx.protocol_name().identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterUnion_style_enum(final Swift5Parser.Union_style_enumContext ctx) {
    if (ctx.enum_name() == null || ctx.enum_name().identifier() == null) {
      return;
    }

    enterType(ctx.enum_name().identifier().getText(), ctx, false, false);
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      addInheritanceRelations(classData, ctx.type_inheritance_clause(), true);
    }
  }

  @Override
  public void exitUnion_style_enum(final Swift5Parser.Union_style_enumContext ctx) {
    if (ctx.enum_name() != null && ctx.enum_name().identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterRaw_value_style_enum(final Swift5Parser.Raw_value_style_enumContext ctx) {
    if (ctx.enum_name() == null || ctx.enum_name().identifier() == null) {
      return;
    }

    enterType(ctx.enum_name().identifier().getText(), ctx, false, false);
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      addInheritanceRelations(classData, ctx.type_inheritance_clause(), true);
    }
  }

  @Override
  public void exitRaw_value_style_enum(final Swift5Parser.Raw_value_style_enumContext ctx) {
    if (ctx.enum_name() != null && ctx.enum_name().identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  private void enterType(final String typeName, final org.antlr.v4.runtime.ParserRuleContext ctx,
      final boolean isStruct, final boolean isInterface) {
    final String fqn = fileDataHandler.buildFqn(typeName);
    fileDataHandler.enterClass(typeName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    if (isStruct) {
      classData.setIsStruct();
    } else if (isInterface) {
      classData.setIsInterface();
    } else {
      classData.setIsClass();
    }

    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  private void addInheritanceRelations(final ClassDataHandler classData,
      final Swift5Parser.Type_inheritance_clauseContext inheritance,
      final boolean treatAllAsInterfaces) {
    if (classData == null || inheritance == null || inheritance.type_inheritance_list() == null) {
      return;
    }

    final List<Swift5Parser.Type_identifierContext> types =
        inheritance.type_inheritance_list().type_identifier();

    for (int i = 0; i < types.size(); i++) {
      final String typeName = types.get(i).getText();
      if (treatAllAsInterfaces || i > 0) {
        classData.addImplementedInterface(typeName);
      } else {
        addFormattedSuperClass(classData, typeName);
      }
    }
  }

  private void addFormattedSuperClass(final ClassDataHandler classData, final String superClassFqn) {
    classData.setSuperClass(getClassPathFromFqn(superClassFqn, ".swift", fileDataHandler.getFileName(),
        fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
  }

  @Override
  public void enterUnion_style_enum_case(final Swift5Parser.Union_style_enum_caseContext ctx) {
    addEnumCase(ctx.enum_case_name());
  }

  @Override
  public void enterRaw_value_style_enum_case(final Swift5Parser.Raw_value_style_enum_caseContext ctx) {
    addEnumCase(ctx.enum_case_name());
  }

  private void addEnumCase(final Swift5Parser.Enum_case_nameContext enumCaseName) {
    if (enumCaseName == null || enumCaseName.identifier() == null) {
      return;
    }

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.addEnumConstant(enumCaseName.identifier().getText());
    }
  }

  @Override
  public void enterVariable_declaration(final Swift5Parser.Variable_declarationContext ctx) {
    if (ctx.variable_name() != null && ctx.variable_name().identifier() != null) {
      variableCount++;
      addPropertyField(ctx.variable_name().identifier().getText(), ctx.type_annotation());
    } else if (ctx.pattern_initializer_list() != null) {
      for (final Swift5Parser.Pattern_initializerContext patternInit : ctx.pattern_initializer_list()
          .pattern_initializer()) {
        countPattern(patternInit.pattern());
      }
    }
  }

  @Override
  public void enterConstant_declaration(final Swift5Parser.Constant_declarationContext ctx) {
    if (ctx.pattern_initializer_list() == null) {
      return;
    }

    for (final Swift5Parser.Pattern_initializerContext patternInit : ctx.pattern_initializer_list()
        .pattern_initializer()) {
      countPattern(patternInit.pattern());
      if (fileDataHandler.isInClassContext()) {
        addFieldFromPattern(patternInit.pattern(), null);
      }
    }
  }

  private void countPattern(final Swift5Parser.PatternContext pattern) {
    if (pattern == null) {
      return;
    }

    if (pattern.identifier_pattern() != null && pattern.identifier_pattern().identifier() != null) {
      variableCount++;
      return;
    }

    if (pattern.tuple_pattern() != null && pattern.tuple_pattern().tuple_pattern_element_list() != null) {
      for (final Swift5Parser.Tuple_pattern_elementContext element : pattern.tuple_pattern()
          .tuple_pattern_element_list().tuple_pattern_element()) {
        countPattern(element.pattern());
      }
    }
  }

  private void addPropertyField(final String fieldName,
      final Swift5Parser.Type_annotationContext typeAnnotation) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String fieldType = typeAnnotation != null && typeAnnotation.type() != null
        ? typeAnnotation.type().getText()
        : "unknown";
    classData.addField(fieldName, fieldType, new ArrayList<>());
  }

  private void addFieldFromPattern(final Swift5Parser.PatternContext pattern,
      final Swift5Parser.Type_annotationContext typeAnnotation) {
    if (pattern == null || pattern.identifier_pattern() == null
        || pattern.identifier_pattern().identifier() == null) {
      return;
    }

    addPropertyField(pattern.identifier_pattern().identifier().getText(), typeAnnotation);
  }

  @Override
  public void enterFunction_declaration(final Swift5Parser.Function_declarationContext ctx) {
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
    methodData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterInitializer_declaration(final Swift5Parser.Initializer_declarationContext ctx) {
    functionCount++;

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String className = classData.getProtoBufObject().getName();
    final MethodDataHandler methodData = classData.addConstructor(className,
        className + "#" + extractParameterTypes(ctx.parameter_clause()).hashCode());
    addParameters(methodData, ctx.parameter_clause());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterParameter(final Swift5Parser.ParameterContext ctx) {
    if (ctx.local_parameter_name() != null && ctx.local_parameter_name().identifier() != null) {
      variableCount++;
    }
  }

  private String extractFunctionName(final Swift5Parser.Function_declarationContext ctx) {
    if (ctx.function_name() == null) {
      return null;
    }
    if (ctx.function_name().identifier() != null) {
      return ctx.function_name().identifier().getText();
    }
    return ctx.function_name().getText();
  }

  private String extractReturnType(final Swift5Parser.Function_declarationContext ctx) {
    if (ctx.function_signature() != null && ctx.function_signature().function_result() != null
        && ctx.function_signature().function_result().type() != null) {
      return ctx.function_signature().function_result().type().getText();
    }
    return "Void";
  }

  private MethodDataHandler createMethodHandler(final String methodName, final String returnType,
      final Swift5Parser.Function_declarationContext ctx) {
    if (fileDataHandler.isInClassContext()) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData == null) {
        return null;
      }
      final MethodDataHandler methodData = classData.addMethod(methodName,
          methodName + "#" + extractParameterTypes(ctx.function_signature().parameter_clause())
              .hashCode(),
          returnType);
      addParameters(methodData, ctx.function_signature().parameter_clause());
      return methodData;
    }

    final MethodDataHandler methodData = fileDataHandler.addGlobalFunction(methodName, returnType);
    if (ctx.function_signature() != null) {
      addParameters(methodData, ctx.function_signature().parameter_clause());
    }
    return methodData;
  }

  private List<String> extractParameterTypes(final Swift5Parser.Parameter_clauseContext parameters) {
    final List<String> paramTypes = new ArrayList<>();
    if (parameters == null || parameters.parameter_list() == null) {
      return paramTypes;
    }

    for (final Swift5Parser.ParameterContext param : parameters.parameter_list().parameter()) {
      if (param.type_annotation() != null && param.type_annotation().type() != null) {
        paramTypes.add(param.type_annotation().type().getText());
      }
    }
    return paramTypes;
  }

  private void addParameters(final MethodDataHandler methodData,
      final Swift5Parser.Parameter_clauseContext parameters) {
    if (parameters == null || parameters.parameter_list() == null) {
      return;
    }

    for (final Swift5Parser.ParameterContext param : parameters.parameter_list().parameter()) {
      final String paramType = param.type_annotation() != null && param.type_annotation().type() != null
          ? param.type_annotation().type().getText()
          : "unknown";
      String paramName = "";
      if (param.local_parameter_name() != null && param.local_parameter_name().identifier() != null) {
        paramName = param.local_parameter_name().identifier().getText();
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
