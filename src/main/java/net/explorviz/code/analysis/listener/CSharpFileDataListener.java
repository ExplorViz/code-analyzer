package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParser;
import net.explorviz.code.analysis.antlr.generated.csharp.CSharpParserBaseListener;
import net.explorviz.code.analysis.handler.CSharpFileDataHandler;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR listener for extracting file data from C# source code.
 */
public class CSharpFileDataListener extends CSharpParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CSharpFileDataListener.class);

  private final CSharpFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public CSharpFileDataListener(final CSharpFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterCompilation_unit(final CSharpParser.Compilation_unitContext ctx) {
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
  public void exitCompilation_unit(final CSharpParser.Compilation_unitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterNamespace_declaration(final CSharpParser.Namespace_declarationContext ctx) {
    if (ctx.qualified_identifier() != null) {
      fileDataHandler.enterNamespace(ctx.qualified_identifier().getText());
    }
  }

  @Override
  public void exitNamespace_declaration(final CSharpParser.Namespace_declarationContext ctx) {
    fileDataHandler.leaveNamespace();
  }

  @Override
  public void enterUsing_namespace_directive(
      final CSharpParser.Using_namespace_directiveContext ctx) {
    if (ctx.namespace_name() != null) {
      fileDataHandler.addImport(ctx.namespace_name().getText());
    }
  }

  @Override
  public void enterUsing_alias_directive(final CSharpParser.Using_alias_directiveContext ctx) {
    if (ctx.namespace_or_type_name() != null) {
      fileDataHandler.addImport(ctx.namespace_or_type_name().getText());
    }
  }

  @Override
  public void enterUsing_static_directive(final CSharpParser.Using_static_directiveContext ctx) {
    if (ctx.type_name() != null) {
      fileDataHandler.addImport(ctx.type_name().getText());
    }
  }

  @Override
  public void enterClass_declaration(final CSharpParser.Class_declarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String className = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(className);
    fileDataHandler.enterClass(className, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsClass();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));

      addClassBaseRelations(classData, ctx.class_base());
    }
  }

  private void addClassBaseRelations(final ClassDataHandler classData,
      final CSharpParser.Class_baseContext classBase) {
    if (classBase == null) {
      return;
    }

    if (classBase.class_type() != null) {
      addFormattedSuperClass(classData, classBase.class_type().getText());
    }

    if (classBase.interface_type_list() != null) {
      for (final CSharpParser.Interface_typeContext interfaceType : classBase.interface_type_list()
          .interface_type()) {
        classData.addImplementedInterface(interfaceType.getText());
      }
    }
  }

  private void addFormattedSuperClass(final ClassDataHandler classData, final String superClassFqn) {
    classData.setSuperClass(getClassPathFromFqn(superClassFqn, ".cs", fileDataHandler.getFileName(),
        fileDataHandler.getPackageName()) + "::" + getClassNameFromFqn(superClassFqn));
  }

  @Override
  public void exitClass_declaration(final CSharpParser.Class_declarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterStruct_declaration(final CSharpParser.Struct_declarationContext ctx) {
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
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));

      if (ctx.struct_interfaces() != null && ctx.struct_interfaces().interface_type_list() != null) {
        for (final CSharpParser.Interface_typeContext interfaceType : ctx.struct_interfaces()
            .interface_type_list().interface_type()) {
          classData.addImplementedInterface(interfaceType.getText());
        }
      }
    }
  }

  @Override
  public void exitStruct_declaration(final CSharpParser.Struct_declarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterInterface_declaration(final CSharpParser.Interface_declarationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String interfaceName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(interfaceName);
    fileDataHandler.enterClass(interfaceName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsInterface();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitInterface_declaration(final CSharpParser.Interface_declarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnum_declaration(final CSharpParser.Enum_declarationContext ctx) {
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
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitEnum_declaration(final CSharpParser.Enum_declarationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnum_member_declaration(final CSharpParser.Enum_member_declarationContext ctx) {
    if (ctx.identifier() != null) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.identifier().getText());
      }
    }
  }

  @Override
  public void enterField_declaration(final CSharpParser.Field_declarationContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null || ctx.type_() == null || ctx.variable_declarators() == null) {
      return;
    }

    final String fieldType = ctx.type_().getText();
    for (final CSharpParser.Variable_declaratorContext variableDecl : ctx.variable_declarators()
        .variable_declarator()) {
      if (variableDecl.identifier() != null) {
        classData.addField(variableDecl.identifier().getText(), fieldType, new ArrayList<>());
      }
    }
  }

  @Override
  public void enterMethod_declaration(final CSharpParser.Method_declarationContext ctx) {
    if (ctx.method_header() == null) {
      return;
    }

    functionCount++;
    final String methodName = extractMemberName(ctx.method_header().member_name());
    if (methodName == null) {
      return;
    }

    final String returnType = extractReturnType(ctx);
    final MethodDataHandler methodData = createMethodHandler(methodName, returnType, ctx.method_header());
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
  public void enterConstructor_declaration(final CSharpParser.Constructor_declarationContext ctx) {
    if (ctx.constructor_declarator() == null || ctx.constructor_declarator().identifier() == null) {
      return;
    }

    functionCount++;
    final String constructorName = ctx.constructor_declarator().identifier().getText();
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final MethodDataHandler methodData = classData.addConstructor(constructorName,
        constructorName + "#" + extractParameterTypes(ctx.constructor_declarator()).hashCode());
    addParameters(methodData, ctx.constructor_declarator().parameter_list());

    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
  }

  @Override
  public void enterLocal_variable_declarator(
      final CSharpParser.Local_variable_declaratorContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  @Override
  public void enterConstant_declarator(final CSharpParser.Constant_declaratorContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  @Override
  public void enterVariable_declarator(final CSharpParser.Variable_declaratorContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  private MethodDataHandler createMethodHandler(final String methodName, final String returnType,
      final CSharpParser.Method_headerContext header) {
    if (fileDataHandler.isInClassContext()) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData == null) {
        return null;
      }
      final MethodDataHandler methodData = classData.addMethod(methodName,
          methodName + "#" + extractParameterTypes(header).hashCode(), returnType);
      addParameters(methodData, header.parameter_list());
      return methodData;
    }

    final MethodDataHandler methodData = fileDataHandler.addGlobalFunction(methodName, returnType);
    addParameters(methodData, header.parameter_list());
    return methodData;
  }

  private String extractMemberName(final CSharpParser.Member_nameContext memberName) {
    if (memberName == null) {
      return null;
    }
    if (memberName.identifier() != null) {
      return memberName.identifier().getText();
    }
    return memberName.getText();
  }

  private String extractReturnType(final CSharpParser.Method_declarationContext ctx) {
    if (ctx.return_type() != null) {
      return ctx.return_type().getText();
    }
    if (ctx.ref_return_type() != null) {
      return ctx.ref_return_type().getText();
    }
    return "void";
  }

  private List<String> extractParameterTypes(final CSharpParser.Method_headerContext header) {
    return extractParameterTypes(header.parameter_list());
  }

  private List<String> extractParameterTypes(final CSharpParser.Constructor_declaratorContext declarator) {
    return extractParameterTypes(declarator.parameter_list());
  }

  private List<String> extractParameterTypes(final CSharpParser.Parameter_listContext parameterList) {
    final List<String> paramTypes = new ArrayList<>();
    if (parameterList == null || parameterList.fixed_parameters() == null) {
      return paramTypes;
    }

    for (final CSharpParser.Fixed_parameterContext param : parameterList.fixed_parameters()
        .fixed_parameter()) {
      if (param.type_() != null) {
        paramTypes.add(param.type_().getText());
      }
    }
    return paramTypes;
  }

  private void addParameters(final MethodDataHandler methodData,
      final CSharpParser.Parameter_listContext parameterList) {
    if (parameterList == null || parameterList.fixed_parameters() == null) {
      return;
    }

    for (final CSharpParser.Fixed_parameterContext param : parameterList.fixed_parameters()
        .fixed_parameter()) {
      final String paramType = param.type_() != null ? param.type_().getText() : "unknown";
      String paramName = "";
      if (param.identifier() != null) {
        paramName = param.identifier().getText();
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
