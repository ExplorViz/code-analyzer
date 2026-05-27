package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14ParserBaseListener;
import net.explorviz.code.analysis.handler.CppFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ANTLR Listener for extracting file data from C/C++ source code using the
 * CPP14 grammar.
 */
public class CppFileDataListener extends CPP14ParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CppFileDataListener.class);

  private final CppFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  public CppFileDataListener(final CppFileDataHandler fileDataHandler,
      final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterTranslationUnit(final CPP14Parser.TranslationUnitContext ctx) {
    // Calculate total source SLOC and CLOC for the entire file
    final int sloc = getSloc(tokens);
    final int cloc = getCloc(ctx);

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    // Extract #include directives
    extractIncludes();

    LOGGER.atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitTranslationUnit(final CPP14Parser.TranslationUnitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
  }

  /**
   * Scans (hidden-channel) tokens for #include directives and adds them as
   * imports.
   */
  private void extractIncludes() {
    if (tokens == null) {
      return;
    }

    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);

      // Directive and MultiLineMacro tokens are on the HIDDEN channel
      if (token.getChannel() != 0) {
        final String text = token.getText();
        if (text != null) {
          final String trimmed = text.trim();
          if (trimmed.startsWith("#include")) {
            // Extract the included file/header name
            // e.g. #include <iostream> -> <iostream>
            // #include "myfile.h" -> "myfile.h"
            final String includeName = trimmed.substring("#include".length()).trim();
            if (!includeName.isEmpty()) {
              fileDataHandler.addImport(includeName);

              LOGGER.atTrace()
                  .addArgument(includeName)
                  .log("Include: {}");
            }
          }
        }
      }
    }
  }

  // --- Namespace handling ---

  @Override
  public void enterNamespaceDefinition(final CPP14Parser.NamespaceDefinitionContext ctx) {
    // Extract namespace name: namespace Foo { ... }
    String namespaceName = null;

    if (ctx.Identifier() != null) {
      namespaceName = ctx.Identifier().getText();
    } else if (ctx.originalNamespaceName() != null) {
      namespaceName = ctx.originalNamespaceName().getText();
    }

    if (namespaceName != null) {
      fileDataHandler.enterNamespace(namespaceName);

      // Use the namespace as the package name
      fileDataHandler.setPackageName(fileDataHandler.getCurrentNamespace());

      LOGGER.atTrace()
          .addArgument(namespaceName)
          .log("Namespace: {}");
    }
  }

  @Override
  public void exitNamespaceDefinition(final CPP14Parser.NamespaceDefinitionContext ctx) {
    String namespaceName = null;

    if (ctx.Identifier() != null) {
      namespaceName = ctx.Identifier().getText();
    } else if (ctx.originalNamespaceName() != null) {
      namespaceName = ctx.originalNamespaceName().getText();
    }

    if (namespaceName != null) {
      fileDataHandler.leaveNamespace();
    }
  }

  // --- Class / Struct handling ---

  @Override
  public void enterClassSpecifier(final CPP14Parser.ClassSpecifierContext ctx) {
    if (ctx.classHead() == null) {
      return;
    }

    final CPP14Parser.ClassHeadContext classHead = ctx.classHead();

    // Get class name
    String className = null;
    if (classHead.classHeadName() != null && classHead.classHeadName().className() != null) {
      className = classHead.classHeadName().className().getText();
    }

    if (className == null) {
      // Anonymous class/struct - skip
      LOGGER.atTrace().log("Anonymous class/struct detected, skipping");
      return;
    }

    // Build FQN
    final String fqn = buildFqn(className);

    fileDataHandler.enterClass(className, fqn);

    // Determine if it is a class or struct
    final boolean isStruct = isStructType(classHead);

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      if (isStruct) {
        classData.setIsStruct();
      } else {
        classData.setIsClass();
      }

      // Calculate class SLOC and LOC
      final int classLoc = calculateLoc(ctx);
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LOC, String.valueOf(classLoc));

      // Handle base classes
      if (classHead.baseClause() != null) {
        extractBaseClasses(classHead.baseClause());
      }

      LOGGER.atTrace()
          .addArgument(isStruct ? "Struct" : "Class")
          .addArgument(className)
          .addArgument(fqn)
          .log("{}: {} (FQN: {})");
    }
  }

  @Override
  public void exitClassSpecifier(final CPP14Parser.ClassSpecifierContext ctx) {
    if (ctx.classHead() != null && ctx.classHead().classHeadName() != null
        && ctx.classHead().classHeadName().className() != null) {
      fileDataHandler.leaveClass();
    }
  }

  // --- Enum handling ---

  @Override
  public void enterEnumSpecifier(final CPP14Parser.EnumSpecifierContext ctx) {
    if (ctx.enumHead() == null) {
      return;
    }

    // Get enum name
    final CPP14Parser.EnumHeadContext enumHead = ctx.enumHead();
    if (enumHead.Identifier() != null) {
      final String enumName = enumHead.Identifier().getText();
      final String fqn = buildFqn(enumName);

      fileDataHandler.enterClass(enumName, fqn);
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.setIsEnum();
        classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        classData.addMetric(LOC, String.valueOf(calculateLoc(ctx)));
      }

      LOGGER.atTrace()
          .addArgument(enumName)
          .log("Enum: {}");
    }
  }

  @Override
  public void exitEnumSpecifier(final CPP14Parser.EnumSpecifierContext ctx) {
    if (ctx.enumHead() != null && ctx.enumHead().Identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumeratorDefinition(final CPP14Parser.EnumeratorDefinitionContext ctx) {
    if (ctx.enumerator() != null && ctx.enumerator().Identifier() != null) {
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.enumerator().Identifier().getText());
      }
    }
  }

  // --- Function / Method handling ---

  @Override
  public void enterFunctionDefinition(final CPP14Parser.FunctionDefinitionContext ctx) {
    // Extract function name, return type, parameters from declarator and
    // declSpecifierSeq
    if (ctx.declarator() == null) {
      return;
    }

    functionCount++;

    final String functionName = extractFunctionName(ctx.declarator());
    if (functionName == null) {
      return;
    }

    // Extract return type from declSpecifierSeq
    String returnType = "void";
    if (ctx.declSpecifierSeq() != null) {
      returnType = extractTypeFromDeclSpecifierSeq(ctx.declSpecifierSeq());
    }

    // Extract parameters
    final List<String> paramTypes = extractParameterTypes(ctx.declarator());

    // Calculate LOC
    final int functionLoc = calculateLoc(ctx);

    if (fileDataHandler.isInClassContext()) {
      // Method inside a class
      final String methodFqn = functionName + "#" + paramTypes.hashCode();

      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        // Check if it is a constructor (name matches class name)
        final String currentClassFqn = fileDataHandler.getCurrentClassFqnOrNull();
        final String currentClassName = currentClassFqn != null
            ? getSimpleName(currentClassFqn)
            : null;

        final MethodDataHandler methodData;
        if (functionName.equals(currentClassName)) {
          methodData = classData.addConstructor(functionName, methodFqn);
        } else {
          // Check if it is a destructor
          if (functionName.startsWith("~")) {
            methodData = classData.addMethod(functionName, methodFqn, "void");
          } else {
            methodData = classData.addMethod(functionName, methodFqn, returnType);
          }
        }

        // Add modifiers
        addFunctionModifiers(methodData, ctx);

        // Add parameters
        addFunctionParameters(methodData, ctx.declarator());

        // Set line numbers
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }

        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LOC, String.valueOf(functionLoc));

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Class method: {}");
      }
    } else {
      // Global function (or namespace-level function)
      // Check for qualified names (e.g., ClassName::method)
      final String qualifiedName = extractQualifiedFunctionName(ctx.declarator());
      if (qualifiedName != null && qualifiedName.contains("::")) {
        // This is a method definition outside the class body (e.g., void Foo::bar())
        // Treat it as a global function for now since the class context is not
        // available
        final var methodHandler = fileDataHandler.addGlobalFunction(qualifiedName, returnType);

        if (ctx.start != null && ctx.stop != null) {
          methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodHandler.addMetric(LOC, String.valueOf(functionLoc));

        addFunctionParameters(methodHandler, ctx.declarator());

        LOGGER.atTrace()
            .addArgument(qualifiedName)
            .log("Out-of-class method definition: {}");
      } else {
        final var methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);

        if (ctx.start != null && ctx.stop != null) {
          methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodHandler.addMetric(LOC, String.valueOf(functionLoc));

        addFunctionParameters(methodHandler, ctx.declarator());

        LOGGER.atTrace()
            .addArgument(functionName)
            .log("Global function: {}");
      }
    }
  }

  // --- Member field handling ---

  @Override
  public void enterMemberDeclaration(final CPP14Parser.MemberDeclarationContext ctx) {
    // Only handle member fields; function definitions are handled separately
    if (ctx.functionDefinition() != null) {
      return;
    }
    if (ctx.declSpecifierSeq() == null) {
      return;
    }
    if (ctx.memberDeclaratorList() == null) {
      return;
    }

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    // Extract the type from the declaration specifiers
    final String fieldType = extractTypeFromDeclSpecifierSeq(ctx.declSpecifierSeq());

    // Extract field names from the member declarator list
    for (final CPP14Parser.MemberDeclaratorContext memberDecl : ctx.memberDeclaratorList()
        .memberDeclarator()) {
      if (memberDecl.declarator() != null) {
        final String fieldName = extractDeclaratorName(memberDecl.declarator());
        if (fieldName != null && !fieldName.contains("(")) {
          // It is a field, not a method declaration
          final List<String> modifiers = new ArrayList<>();
          classData.addField(fieldName, fieldType, modifiers);
        }
      }
    }
  }

  // --- Access specifier handling ---

  @Override
  public void enterAccessSpecifier(final CPP14Parser.AccessSpecifierContext ctx) {
    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      final String accessModifier = ctx.getText();
      LOGGER.atTrace()
          .addArgument(accessModifier)
          .log("Access specifier: {}");
    }
  }

  // --- Helper methods ---

  private String buildFqn(final String name) {
    final String namespace = fileDataHandler.getCurrentNamespace();
    final String currentClassFqn = fileDataHandler.getCurrentClassFqnOrNull();

    if (currentClassFqn != null) {
      return currentClassFqn + "::" + name;
    } else if (!namespace.isEmpty()) {
      return namespace + "::" + name;
    }
    return name;
  }

  private String getSimpleName(final String fqn) {
    final int lastSep = fqn.lastIndexOf("::");
    return lastSep >= 0 ? fqn.substring(lastSep + 2) : fqn;
  }

  private boolean isStructType(final CPP14Parser.ClassHeadContext classHead) {
    if (classHead.classKey() != null) {
      return classHead.classKey().Struct() != null;
    }
    return classHead.Union() != null;
  }

  private void extractBaseClasses(final CPP14Parser.BaseClauseContext baseClause) {
    if (baseClause.baseSpecifierList() == null) {
      return;
    }

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    for (final CPP14Parser.BaseSpecifierContext baseSpec : baseClause.baseSpecifierList()
        .baseSpecifier()) {
      if (baseSpec.baseTypeSpecifier() != null
          && baseSpec.baseTypeSpecifier().classOrDeclType() != null) {
        final String baseClassName = baseSpec.baseTypeSpecifier().classOrDeclType().getText();
        classData.setSuperClass(baseClassName);
      }
    }
  }

  /**
   * Extract the function name from a declarator, handling nested declarators.
   */
  private String extractFunctionName(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null) {
      return extractFunctionNameFromPointerDeclarator(ctx.pointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return extractDeclaratorName(ctx);
    }
    return null;
  }

  private String extractFunctionNameFromPointerDeclarator(
      final CPP14Parser.PointerDeclaratorContext ctx) {
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  private String extractNameFromNoPointerDeclarator(
      final CPP14Parser.NoPointerDeclaratorContext ctx) {
    if (ctx.declaratorId() != null) {
      return extractIdExpressionName(ctx.declaratorId().idExpression());
    }
    // Recurse into nested no-pointer declarators
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  private String extractIdExpressionName(final CPP14Parser.IdExpressionContext ctx) {
    if (ctx == null) {
      return null;
    }
    if (ctx.unqualifiedId() != null) {
      if (ctx.unqualifiedId().Identifier() != null) {
        return ctx.unqualifiedId().Identifier().getText();
      }
      // Handle destructor names (~ClassName)
      if (ctx.unqualifiedId().Tilde() != null) {
        return "~" + ctx.unqualifiedId().className().getText();
      }
      // Handle operator overloading
      if (ctx.unqualifiedId().operatorFunctionId() != null) {
        return ctx.unqualifiedId().operatorFunctionId().getText();
      }
    }
    if (ctx.qualifiedId() != null && ctx.qualifiedId().unqualifiedId() != null) {
      if (ctx.qualifiedId().unqualifiedId().Identifier() != null) {
        return ctx.qualifiedId().unqualifiedId().Identifier().getText();
      }
      // Handle destructor
      if (ctx.qualifiedId().unqualifiedId().Tilde() != null) {
        return "~" + ctx.qualifiedId().unqualifiedId().className().getText();
      }
    }
    return null;
  }

  /**
   * Extract the fully qualified function name (including class prefix for
   * out-of-class definitions).
   */
  private String extractQualifiedFunctionName(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null && ctx.pointerDeclarator().noPointerDeclarator() != null) {
      final CPP14Parser.NoPointerDeclaratorContext noPtr = ctx.pointerDeclarator().noPointerDeclarator();
      if (noPtr.declaratorId() != null && noPtr.declaratorId().idExpression() != null) {
        return noPtr.declaratorId().idExpression().getText();
      }
      if (noPtr.noPointerDeclarator() != null) {
        return extractQualifiedFromNoPointer(noPtr.noPointerDeclarator());
      }
    }
    return null;
  }

  private String extractQualifiedFromNoPointer(
      final CPP14Parser.NoPointerDeclaratorContext ctx) {
    if (ctx.declaratorId() != null && ctx.declaratorId().idExpression() != null) {
      return ctx.declaratorId().idExpression().getText();
    }
    if (ctx.noPointerDeclarator() != null) {
      return extractQualifiedFromNoPointer(ctx.noPointerDeclarator());
    }
    return null;
  }

  /**
   * Extract a simple declarator name for fields.
   */
  private String extractDeclaratorName(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null) {
      return extractDeclaratorNameFromPointer(ctx.pointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  private String extractDeclaratorNameFromPointer(
      final CPP14Parser.PointerDeclaratorContext ctx) {
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  /**
   * Extract the type from a declSpecifierSeq context, skipping cv-qualifiers
   * (const, volatile) and non-type specifiers.
   */
  private String extractTypeFromDeclSpecifierSeq(
      final CPP14Parser.DeclSpecifierSeqContext ctx) {
    if (ctx == null) {
      return "void";
    }

    final StringBuilder typeBuilder = new StringBuilder();
    for (final CPP14Parser.DeclSpecifierContext declSpec : ctx.declSpecifier()) {
      if (declSpec.typeSpecifier() != null) {
        final String typeText = extractActualType(declSpec.typeSpecifier());
        if (typeText != null && !typeText.isEmpty()) {
          if (typeBuilder.length() > 0) {
            typeBuilder.append(" ");
          }
          typeBuilder.append(typeText);
        }
      } else if (declSpec.Constexpr() != null) {
        // Skip constexpr for type extraction
      } else {
        final String text = declSpec.getText();
        // Skip storage class specifiers and function specifiers for type name
        if (!"static".equals(text) && !"extern".equals(text) && !"inline".equals(text)
            && !"virtual".equals(text) && !"explicit".equals(text)
            && !"friend".equals(text) && !"typedef".equals(text)
            && !"constexpr".equals(text) && !"mutable".equals(text)
            && !"register".equals(text) && !"thread_local".equals(text)) {
          if (typeBuilder.length() > 0) {
            typeBuilder.append(" ");
          }
          typeBuilder.append(text);
        }
      }
    }

    final String result = typeBuilder.toString().trim();
    return result.isEmpty() ? "void" : result;
  }

  /**
   * Extract the actual data type from a typeSpecifier, filtering out
   * cv-qualifiers (const, volatile).
   */
  private String extractActualType(final CPP14Parser.TypeSpecifierContext typeSpec) {
    // typeSpecifier -> trailingTypeSpecifier | classSpecifier | enumSpecifier
    if (typeSpec.classSpecifier() != null) {
      return typeSpec.classSpecifier().getText();
    }
    if (typeSpec.enumSpecifier() != null) {
      return typeSpec.enumSpecifier().getText();
    }
    if (typeSpec.trailingTypeSpecifier() != null) {
      final var trailing = typeSpec.trailingTypeSpecifier();
      // Skip const and volatile (cv-qualifiers) as qualifiers to get actual data type
      if (trailing.cvQualifier() != null) {
        return null;
      }
      return trailing.getText();
    }
    return typeSpec.getText();
  }

  /**
   * Extract parameter types from a declarator's parameter list.
   */
  private List<String> extractParameterTypes(final CPP14Parser.DeclaratorContext ctx) {
    final List<String> paramTypes = new ArrayList<>();
    final CPP14Parser.ParametersAndQualifiersContext params = findParametersAndQualifiers(ctx);

    if (params != null && params.parameterDeclarationClause() != null
        && params.parameterDeclarationClause().parameterDeclarationList() != null) {
      for (final CPP14Parser.ParameterDeclarationContext param : params.parameterDeclarationClause()
          .parameterDeclarationList()
          .parameterDeclaration()) {
        if (param.declSpecifierSeq() != null) {
          String type = extractTypeFromDeclSpecifierSeq(param.declSpecifierSeq());
          type += extractPointerSuffix(param);
          type += extractArraySuffix(param);
          paramTypes.add(type);
        }
      }
    }
    return paramTypes;
  }

  /**
   * Find the parametersAndQualifiers context within a declarator.
   */
  private CPP14Parser.ParametersAndQualifiersContext findParametersAndQualifiers(
      final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null) {
      return findParamsInNoPointerDeclarator(ctx.pointerDeclarator().noPointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return findParamsInNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  private CPP14Parser.ParametersAndQualifiersContext findParamsInNoPointerDeclarator(
      final CPP14Parser.NoPointerDeclaratorContext ctx) {
    if (ctx == null) {
      return null;
    }
    if (ctx.parametersAndQualifiers() != null) {
      return ctx.parametersAndQualifiers();
    }
    if (ctx.noPointerDeclarator() != null) {
      return findParamsInNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  /**
   * Add function modifiers (virtual, static, inline, etc.) to the method handler.
   */
  private void addFunctionModifiers(final MethodDataHandler methodData,
      final CPP14Parser.FunctionDefinitionContext ctx) {
    if (ctx.declSpecifierSeq() != null) {
      for (final CPP14Parser.DeclSpecifierContext declSpec : ctx.declSpecifierSeq()
          .declSpecifier()) {
        if (declSpec.functionSpecifier() != null) {
          methodData.addModifier(declSpec.functionSpecifier().getText());
        } else if (declSpec.storageClassSpecifier() != null) {
          methodData.addModifier(declSpec.storageClassSpecifier().getText());
        }
      }
    }

    // Check for virtual specifiers (override, final)
    if (ctx.virtualSpecifierSeq() != null) {
      for (final CPP14Parser.VirtualSpecifierContext virtSpec : ctx.virtualSpecifierSeq()
          .virtualSpecifier()) {
        methodData.addModifier(virtSpec.getText());
      }
    }
  }

  /**
   * Add function parameters to the method handler.
   */
  private void addFunctionParameters(final MethodDataHandler methodData,
      final CPP14Parser.DeclaratorContext ctx) {
    final CPP14Parser.ParametersAndQualifiersContext params = findParametersAndQualifiers(ctx);

    if (params != null && params.parameterDeclarationClause() != null
        && params.parameterDeclarationClause().parameterDeclarationList() != null) {
      for (final CPP14Parser.ParameterDeclarationContext param : params.parameterDeclarationClause()
          .parameterDeclarationList()
          .parameterDeclaration()) {
        String paramType = param.declSpecifierSeq() != null
            ? extractTypeFromDeclSpecifierSeq(param.declSpecifierSeq())
            : "unknown";
        paramType += extractPointerSuffix(param);
        paramType += extractArraySuffix(param);
        String paramName = "";
        if (param.declarator() != null) {
          paramName = extractDeclaratorName(param.declarator());
          if (paramName == null) {
            paramName = "";
          }
        }
        final List<String> modifiers = extractParameterModifiers(param);
        methodData.addParameter(paramName, paramType, modifiers);
      }
    }
  }

  /**
   * Extract const and volatile modifiers from a parameter declaration.
   */
  private List<String> extractParameterModifiers(final CPP14Parser.ParameterDeclarationContext param) {
    final List<String> modifiers = new ArrayList<>();

    // Check declSpecifierSeq for const/volatile
    if (param.declSpecifierSeq() != null) {
      for (final CPP14Parser.DeclSpecifierContext declSpec : param.declSpecifierSeq().declSpecifier()) {
        if (declSpec.typeSpecifier() != null) {
          if (declSpec.typeSpecifier().trailingTypeSpecifier() != null) {
            final var trailing = declSpec.typeSpecifier().trailingTypeSpecifier();
            if (trailing.cvQualifier() != null) {
              modifiers.add(trailing.cvQualifier().getText());
            }
          }
        }
      }
    }

    // Check pointer asterisks for cvQualifiers (e.g. int * const p)
    if (param.declarator() != null && param.declarator().pointerDeclarator() != null) {
      final var ptrDecl = param.declarator().pointerDeclarator();
      if (ptrDecl.pointerOperator() != null) {
        for (final CPP14Parser.PointerOperatorContext ptrOp : ptrDecl.pointerOperator()) {
          if (ptrOp.cvQualifierSeq() != null && ptrOp.cvQualifierSeq().cvQualifier() != null) {
            for (final CPP14Parser.CvQualifierContext cvQual : ptrOp.cvQualifierSeq().cvQualifier()) {
              modifiers.add(cvQual.getText());
            }
          }
        }
      }
    }

    return modifiers;
  }

  /**
   * Check if a parameter declaration contains array brackets (e.g. int arr[])
   * and return "[]" if so, otherwise empty string.
   */
  private String extractArraySuffix(final CPP14Parser.ParameterDeclarationContext param) {
    // Check the declarator for array brackets: noPointerDeclarator [ ]
    if (param.declarator() != null) {
      return hasArrayBrackets(param.declarator()) ? "[]" : "";
    }
    // Check the abstract declarator for array brackets (unnamed parameters)
    if (param.abstractDeclarator() != null) {
      final String text = param.abstractDeclarator().getText();
      if (text.contains("[") && text.contains("]")) {
        return "[]";
      }
    }
    return "";
  }

  /**
   * Check if a parameter declaration contains pointer operators (e.g. int *p)
   * and return the pointer suffix ("*", "&", "&&") if so.
   */
  private String extractPointerSuffix(final CPP14Parser.ParameterDeclarationContext param) {
    if (param.declarator() != null && param.declarator().pointerDeclarator() != null) {
      final var ptrDecl = param.declarator().pointerDeclarator();
      if (ptrDecl.pointerOperator() != null && !ptrDecl.pointerOperator().isEmpty()) {
        final StringBuilder suffix = new StringBuilder();
        for (final CPP14Parser.PointerOperatorContext ptrOp : ptrDecl.pointerOperator()) {
          if (ptrOp.Star() != null) {
            suffix.append("*");
          } else if (ptrOp.AndAnd() != null) {
            suffix.append("&&");
          } else if (ptrOp.And() != null) {
            suffix.append("&");
          }
        }
        return suffix.toString();
      }
    }
    return "";
  }

  /**
   * Check if a declarator contains array brackets (LeftBracket / RightBracket).
   */
  private boolean hasArrayBrackets(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null && ctx.pointerDeclarator().noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.pointerDeclarator().noPointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.noPointerDeclarator());
    }
    return false;
  }

  private boolean hasArrayBracketsInNoPointer(
      final CPP14Parser.NoPointerDeclaratorContext ctx) {
    // noPointerDeclarator: ... | noPointerDeclarator LeftBracket ... RightBracket
    if (ctx.LeftBracket() != null) {
      return true;
    }
    if (ctx.noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.noPointerDeclarator());
    }
    return false;
  }

  /**
   * Get comment lines of code by counting tokens on the hidden channel.
   */
  @Override
  public void enterSimpleDeclaration(final CPP14Parser.SimpleDeclarationContext ctx) {
    if (ctx.initDeclaratorList() != null) {
      variableCount += ctx.initDeclaratorList().initDeclarator().size();
    }
  }

  private int getCloc(final ParserRuleContext ctx) {
    if (ctx == null || tokens == null) {
      return 0;
    }

    int commentLines = 0;

    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);

      // Comments are on the HIDDEN channel
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
