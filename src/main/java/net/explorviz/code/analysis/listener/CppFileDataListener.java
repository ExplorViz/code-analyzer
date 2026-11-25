package net.explorviz.code.analysis.listener;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14Parser;
import net.explorviz.code.analysis.antlr.generated.cpp.CPP14ParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.CppFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.metrics.MetricsCollector;
import net.explorviz.code.analysis.metrics.cpp.CppControlFlow;
import net.explorviz.code.analysis.metrics.cpp.CppMembers;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR Listener for extracting file data from C/C++ source code using the CPP14 grammar. */
public class CppFileDataListener extends CPP14ParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(CppFileDataListener.class);
  private static final String ANONYMOUS_TYPE_NAME = "<anonymous>";

  private final CppFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;
  private final MetricsCollector metricsCollector =
      new MetricsCollector(new CppControlFlow(), new CppMembers());

  public CppFileDataListener(
      final CppFileDataHandler fileDataHandler, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterTranslationUnit(final CPP14Parser.TranslationUnitContext ctx) {
    metricsCollector.enterFile(ctx);
    // Calculate total source SLOC and CLOC for the entire file
    final int sloc = getSloc(tokens);
    final int cloc = getCloc(ctx);

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    // Extract #include directives
    extractIncludes();

    LOGGER
        .atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitTranslationUnit(final CPP14Parser.TranslationUnitContext ctx) {
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
    metricsCollector.exitFile(fileDataHandler);
  }

  /** Scans (hidden-channel) tokens for #include directives and adds them as imports. */
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

              LOGGER.atTrace().addArgument(includeName).log("Include: {}");
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
      // The handler uses the first top-level namespace as the package name
      fileDataHandler.enterNamespace(namespaceName);

      LOGGER.atTrace().addArgument(namespaceName).log("Namespace: {}");
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
      if (isAnonymousMemberType(ctx)) {
        enterAnonymousClass(ctx);
      } else {
        LOGGER.atTrace().log("Anonymous class/struct detected, skipping");
      }
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
      classData.addMetric(LINE_COUNT, String.valueOf(classLoc));
      metricsCollector.addClass(classData, ctx);

      // Handle base classes
      if (classHead.baseClause() != null) {
        extractBaseClasses(classHead.baseClause());
      }

      LOGGER
          .atTrace()
          .addArgument(isStruct ? "Struct" : "Class")
          .addArgument(className)
          .addArgument(fqn)
          .log("{}: {} (FQN: {})");
    }
  }

  @Override
  public void exitClassSpecifier(final CPP14Parser.ClassSpecifierContext ctx) {
    final boolean named =
        ctx.classHead() != null
            && ctx.classHead().classHeadName() != null
            && ctx.classHead().classHeadName().className() != null;
    if (named || isAnonymousMemberType(ctx)) {
      fileDataHandler.leaveClass();
    }
  }

  /**
   * Whether an anonymous class specifier is modeled as an anonymous inner class.
   *
   * <ul>
   *   <li>A named member of anonymous type ({@code class W { struct { int x; } inner; };}) is a
   *       field {@code inner} of {@code W} with type {@code struct <anonymous>}. The anonymous type
   *       is an inner anonymous class {@code <anonymous>} of {@code W} that owns its members
   *       ({@code x}), just like a named nested struct. The same applies to anonymous types of
   *       local variables in member function bodies.
   *   <li>An unnamed anonymous member ({@code union { int i; float f; };}, anonymous union) has no
   *       field. Its members are accessed as members of the enclosing class ({@code w.i}), so they
   *       stay fields of the enclosing class.
   * </ul>
   *
   * <p>Anonymous types outside of classes (e.g. {@code typedef struct { ... } P;}) are skipped.
   */
  private boolean isAnonymousMemberType(final CPP14Parser.ClassSpecifierContext ctx) {
    if (ctx.classHead() == null
        || ctx.classHead().classHeadName() != null
        || !fileDataHandler.isInClassContext()) {
      return false;
    }
    ParserRuleContext parent = ctx.getParent();
    while (parent instanceof CPP14Parser.TypeSpecifierContext
        || parent instanceof CPP14Parser.DeclSpecifierContext
        || parent instanceof CPP14Parser.DeclSpecifierSeqContext) {
      parent = parent.getParent();
    }
    return !(parent instanceof CPP14Parser.MemberDeclarationContext member
        && member.memberDeclaratorList() == null);
  }

  private void enterAnonymousClass(final CPP14Parser.ClassSpecifierContext ctx) {
    fileDataHandler.enterClass(ANONYMOUS_TYPE_NAME, buildFqn(ANONYMOUS_TYPE_NAME));
    final var classData = fileDataHandler.getCurrentClassData();
    classData.setIsAnonymousClass();
    classData.addModifier(classKeyText(ctx.classHead()));
    classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    metricsCollector.addClass(classData, ctx);
    if (ctx.classHead().baseClause() != null) {
      extractBaseClasses(ctx.classHead().baseClause());
    }
  }

  /** Returns "class", "struct" or "union". */
  private static String classKeyText(final CPP14Parser.ClassHeadContext classHead) {
    return classHead.classKey() != null ? classHead.classKey().getText() : "union";
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
        classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
        metricsCollector.addClass(classData, ctx);
      }

      LOGGER.atTrace().addArgument(enumName).log("Enum: {}");
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
    // Enumerators of an anonymous enum belong to no enum type; they must not be added to the
    // enclosing class.
    final CPP14Parser.EnumSpecifierContext enumSpecifier = findEnclosingEnumSpecifier(ctx);
    if (enumSpecifier == null
        || enumSpecifier.enumHead() == null
        || enumSpecifier.enumHead().Identifier() == null) {
      return;
    }
    if (ctx.enumerator() != null && ctx.enumerator().Identifier() != null) {
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.enumerator().Identifier().getText());
      }
    }
  }

  private static CPP14Parser.EnumSpecifierContext findEnclosingEnumSpecifier(
      final ParserRuleContext ctx) {
    ParserRuleContext current = ctx.getParent();
    while (current != null && !(current instanceof CPP14Parser.EnumSpecifierContext)) {
      current = current.getParent();
    }
    return (CPP14Parser.EnumSpecifierContext) current;
  }

  // --- Function / Method handling ---

  @Override
  public void enterFunctionDefinition(final CPP14Parser.FunctionDefinitionContext ctx) {
    // Extract function name, return type, parameters from declarator and
    // declSpecifierSeq
    if (ctx.declarator() == null) {
      return;
    }

    final String functionName = extractFunctionName(ctx.declarator());
    if (functionName == null) {
      functionCount++;
      return;
    }

    String returnType = extractReturnType(ctx.declSpecifierSeq(), ctx.declarator());
    String qualifiedName = extractQualifiedFunctionName(ctx.declarator());

    // The grammar parses an out-of-line constructor or destructor definition such as
    // "Widget::Widget(int) {}" as the type "Widget" followed by the declarator "::Widget(int)".
    if (qualifiedName != null && qualifiedName.startsWith("::")) {
      final String typeName = extractLastTypeSpecifier(ctx.declSpecifierSeq());
      if (typeName != null) {
        final String simpleTypeName = getSimpleName(stripTemplateArguments(typeName));
        if (functionName.equals(simpleTypeName) || functionName.equals("~" + simpleTypeName)) {
          qualifiedName = typeName + qualifiedName;
          returnType = "void";
        }
      }
    }

    // Extract parameters
    final List<String> paramTypes = extractParameterTypes(ctx.declarator());
    final String methodKey = buildMethodKey(functionName, paramTypes, ctx.declarator());

    if (fileDataHandler.isInClassContext()) {
      functionCount++;
      // Method inside a class
      final var classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        // Check if it is a constructor (name matches class name)
        final String currentClassFqn = fileDataHandler.getCurrentClassFqnOrNull();
        final String currentClassName =
            currentClassFqn != null ? getSimpleName(currentClassFqn) : null;

        final MethodDataHandler methodData;
        if (functionName.equals(currentClassName)) {
          methodData = classData.addConstructor(functionName, methodKey);
        } else if (functionName.startsWith("~")) {
          methodData = classData.addMethod(functionName, methodKey, "void");
        } else {
          methodData = classData.addMethod(functionName, methodKey, returnType);
        }
        fillFunctionDefinition(methodData, classData, ctx);

        LOGGER.atTrace().addArgument(functionName).log("Class method: {}");
      }
      return;
    }

    // Global function (or namespace-level function)
    // Check for qualified names (e.g., ClassName::method)
    if (qualifiedName != null && qualifiedName.contains("::")) {
      // This is a method definition outside the class body (e.g., void Foo::bar()). If the class
      // is defined in this file, the definition replaces the matching declaration.
      final String qualifier =
          stripTemplateArguments(qualifiedName.substring(0, qualifiedName.lastIndexOf("::")));
      final var classData = fileDataHandler.findClassData(qualifier);
      if (classData != null) {
        final MethodDataHandler declaration = classData.getMethod(methodKey);
        if (declaration == null) {
          functionCount++;
        }
        final MethodDataHandler methodData;
        if (functionName.equals(getSimpleName(qualifier))) {
          methodData = classData.putConstructor(functionName, methodKey);
        } else if (functionName.startsWith("~")) {
          methodData = classData.putMethod(functionName, methodKey, "void");
        } else {
          methodData = classData.putMethod(functionName, methodKey, returnType);
        }
        if (declaration != null) {
          metricsCollector.removeFunction(declaration);
          // Keep modifiers that only the declaration carries (e.g. virtual, static)
          for (final String modifier : declaration.getProtoBufObject().getModifiersList()) {
            methodData.addModifier(modifier);
          }
        }
        fillFunctionDefinition(methodData, classData, ctx);

        LOGGER.atTrace().addArgument(qualifiedName).log("Out-of-class method definition: {}");
        return;
      }

      // The class is not part of this file, keep the definition as a global function
      functionCount++;
      final var methodHandler = fileDataHandler.addGlobalFunction(qualifiedName, returnType);
      fillFunctionDefinition(methodHandler, null, ctx);

      LOGGER.atTrace().addArgument(qualifiedName).log("Out-of-class method definition: {}");
    } else {
      functionCount++;
      final var methodHandler = fileDataHandler.addGlobalFunction(functionName, returnType);
      if (ctx.start != null && ctx.stop != null) {
        methodHandler.setLines(ctx.start.getLine(), ctx.stop.getLine());
      }
      methodHandler.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      methodHandler.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
      metricsCollector.addFunction(methodHandler, null, ctx);

      addFunctionParameters(methodHandler, ctx.declarator());

      LOGGER.atTrace().addArgument(functionName).log("Global function: {}");
    }
  }

  /** Adds modifiers, parameters, lines and size metrics of a function definition. */
  private void fillFunctionDefinition(
      final MethodDataHandler methodData,
      final ClassDataHandler owner,
      final CPP14Parser.FunctionDefinitionContext ctx) {
    addFunctionModifiers(methodData, ctx);
    addFunctionParameters(methodData, ctx.declarator());
    if (ctx.start != null && ctx.stop != null) {
      methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
    }
    methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
    methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    metricsCollector.addFunction(methodData, owner, ctx);
  }

  /**
   * Builds the key of a method within its class from its name, parameter types and qualifiers, so
   * that overloads get distinct keys and a declaration and its out-of-line definition share one.
   */
  private String buildMethodKey(
      final String name, final List<String> paramTypes, final CPP14Parser.DeclaratorContext decl) {
    final StringBuilder key = new StringBuilder(name).append('#').append(paramTypes.hashCode());
    final CPP14Parser.ParametersAndQualifiersContext params = findParametersAndQualifiers(decl);
    if (params != null) {
      if (params.cvQualifierSeq() != null) {
        key.append('#').append(params.cvQualifierSeq().getText());
      }
      if (params.refQualifier() != null) {
        key.append('#').append(params.refQualifier().getText());
      }
    }
    return key.toString();
  }

  /** Returns the text of the last declSpecifier if it is a type specifier, otherwise null. */
  private static String extractLastTypeSpecifier(final CPP14Parser.DeclSpecifierSeqContext ctx) {
    if (ctx == null || ctx.declSpecifier().isEmpty()) {
      return null;
    }
    final CPP14Parser.DeclSpecifierContext last =
        ctx.declSpecifier().get(ctx.declSpecifier().size() - 1);
    if (last.typeSpecifier() == null || last.typeSpecifier().trailingTypeSpecifier() == null) {
      return null;
    }
    return last.typeSpecifier().trailingTypeSpecifier().getText();
  }

  private static String stripTemplateArguments(final String name) {
    final StringBuilder result = new StringBuilder();
    int depth = 0;
    for (final char c : name.toCharArray()) {
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth = Math.max(0, depth - 1);
      } else if (depth == 0) {
        result.append(c);
      }
    }
    return result.toString();
  }

  // --- Member field handling ---

  @Override
  public void enterMemberDeclaration(final CPP14Parser.MemberDeclarationContext ctx) {
    // Function definitions with bodies are handled by enterFunctionDefinition
    if (ctx.functionDefinition() != null) {
      return;
    }
    if (ctx.memberDeclaratorList() == null) {
      return;
    }

    final var classData = fileDataHandler.getCurrentClassData();
    if (classData == null) {
      return;
    }

    final String currentClassFqn = fileDataHandler.getCurrentClassFqnOrNull();
    final String currentClassName = currentClassFqn != null ? getSimpleName(currentClassFqn) : null;
    final boolean isTypedef = hasTypedef(ctx.declSpecifierSeq());

    for (final CPP14Parser.MemberDeclaratorContext memberDecl :
        ctx.memberDeclaratorList().memberDeclarator()) {
      if (memberDecl.declarator() == null) {
        continue;
      }

      final String name = extractFunctionName(memberDecl.declarator());
      if (name == null) {
        continue;
      }

      // Declarators with parameter lists are method declarations (incl. constructors,
      // destructors and pure virtual methods). Declarations without declSpecifierSeq are
      // constructors, destructors or conversion functions.
      if (!isTypedef && isFunctionDeclarator(memberDecl.declarator())) {
        functionCount++;
        final List<String> paramTypes = extractParameterTypes(memberDecl.declarator());
        final String methodKey = buildMethodKey(name, paramTypes, memberDecl.declarator());
        final MethodDataHandler methodData;
        if (name.equals(currentClassName)) {
          methodData = classData.addConstructor(name, methodKey);
        } else if (name.startsWith("~")) {
          methodData = classData.addMethod(name, methodKey, "void");
        } else {
          methodData =
              classData.addMethod(
                  name,
                  methodKey,
                  extractReturnType(ctx.declSpecifierSeq(), memberDecl.declarator()));
        }
        addDeclSpecifierModifiers(methodData, ctx.declSpecifierSeq());
        if (memberDecl.virtualSpecifierSeq() != null) {
          for (final CPP14Parser.VirtualSpecifierContext virtSpec :
              memberDecl.virtualSpecifierSeq().virtualSpecifier()) {
            methodData.addModifier(virtSpec.getText());
          }
        }
        if (isPureSpecified(memberDecl)) {
          methodData.addModifier("pure");
        }
        addFunctionParameters(methodData, memberDecl.declarator());
        if (ctx.start != null && ctx.stop != null) {
          methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
        }
        methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
        methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
        metricsCollector.addDeclaredFunction(methodData, classData);
        continue;
      }

      if (ctx.declSpecifierSeq() == null || isTypedef) {
        continue;
      }
      final String fieldType =
          extractTypeFromDeclSpecifierSeq(ctx.declSpecifierSeq())
              + extractPointerSuffix(memberDecl.declarator());
      classData.addField(name, fieldType, new ArrayList<>());
    }
  }

  /**
   * Whether a member declarator is pure virtual ({@code = 0}). The grammar only produces a
   * pureSpecifier if its predicate accepts the declarator shape (e.g. not for pointer return
   * types), otherwise {@code = 0} is parsed as an initializer of the function declarator.
   */
  private static boolean isPureSpecified(final CPP14Parser.MemberDeclaratorContext memberDecl) {
    if (memberDecl.pureSpecifier() != null) {
      return true;
    }
    final CPP14Parser.BraceOrEqualInitializerContext init = memberDecl.braceOrEqualInitializer();
    return init != null
        && init.Assign() != null
        && init.initializerClause() != null
        && "0".equals(init.initializerClause().getText());
  }

  private static boolean hasTypedef(final CPP14Parser.DeclSpecifierSeqContext ctx) {
    if (ctx == null) {
      return false;
    }
    for (final CPP14Parser.DeclSpecifierContext declSpec : ctx.declSpecifier()) {
      if (declSpec.Typedef() != null) {
        return true;
      }
    }
    return false;
  }

  // --- Access specifier handling ---

  @Override
  public void enterAccessSpecifier(final CPP14Parser.AccessSpecifierContext ctx) {
    final var classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      final String accessModifier = ctx.getText();
      LOGGER.atTrace().addArgument(accessModifier).log("Access specifier: {}");
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

    for (final CPP14Parser.BaseSpecifierContext baseSpec :
        baseClause.baseSpecifierList().baseSpecifier()) {
      if (baseSpec.baseTypeSpecifier() != null
          && baseSpec.baseTypeSpecifier().classOrDeclType() != null) {
        final String baseClassName = baseSpec.baseTypeSpecifier().classOrDeclType().getText();
        classData.setSuperClass(baseClassName);
      }
    }
  }

  /** Extract the function name from a declarator, handling nested declarators. */
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
      return extractUnqualifiedIdName(ctx.unqualifiedId());
    }
    if (ctx.qualifiedId() != null && ctx.qualifiedId().unqualifiedId() != null) {
      return extractUnqualifiedIdName(ctx.qualifiedId().unqualifiedId());
    }
    return null;
  }

  private String extractUnqualifiedIdName(final CPP14Parser.UnqualifiedIdContext ctx) {
    if (ctx.Identifier() != null) {
      return ctx.Identifier().getText();
    }
    // Handle destructor names (~ClassName, ~decltype(x))
    if (ctx.Tilde() != null) {
      if (ctx.className() != null) {
        return "~" + ctx.className().getText();
      }
      if (ctx.decltypeSpecifier() != null) {
        return "~" + ctx.decltypeSpecifier().getText();
      }
      return null;
    }
    // Handle operator overloading
    if (ctx.operatorFunctionId() != null) {
      return ctx.operatorFunctionId().getText();
    }
    // Handle conversion functions (operator bool)
    if (ctx.conversionFunctionId() != null
        && ctx.conversionFunctionId().conversionTypeId() != null) {
      return "operator " + ctx.conversionFunctionId().conversionTypeId().getText();
    }
    return null;
  }

  /**
   * Extract the fully qualified function name (including class prefix for out-of-class
   * definitions).
   */
  private String extractQualifiedFunctionName(final CPP14Parser.DeclaratorContext ctx) {
    final CPP14Parser.NoPointerDeclaratorContext noPtr = getNoPointerDeclarator(ctx);
    return noPtr != null ? extractQualifiedFromNoPointer(noPtr) : null;
  }

  /** Returns the noPointerDeclarator of both declarator alternatives. */
  private static CPP14Parser.NoPointerDeclaratorContext getNoPointerDeclarator(
      final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null) {
      return ctx.pointerDeclarator().noPointerDeclarator();
    }
    return ctx.noPointerDeclarator();
  }

  /**
   * Whether a declarator declares a function (as opposed to e.g. a pointer to a function or a
   * variable): the declarator id is directly followed by a parameter list.
   */
  private static boolean isFunctionDeclarator(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.parametersAndQualifiers() != null) {
      // noPointerDeclarator parametersAndQualifiers trailingReturnType
      return ctx.noPointerDeclarator() != null && ctx.noPointerDeclarator().declaratorId() != null;
    }
    final CPP14Parser.NoPointerDeclaratorContext noPtr = getNoPointerDeclarator(ctx);
    return noPtr != null
        && noPtr.parametersAndQualifiers() != null
        && noPtr.noPointerDeclarator() != null
        && noPtr.noPointerDeclarator().declaratorId() != null;
  }

  private String extractQualifiedFromNoPointer(final CPP14Parser.NoPointerDeclaratorContext ctx) {
    if (ctx.declaratorId() != null && ctx.declaratorId().idExpression() != null) {
      return ctx.declaratorId().idExpression().getText();
    }
    if (ctx.noPointerDeclarator() != null) {
      return extractQualifiedFromNoPointer(ctx.noPointerDeclarator());
    }
    return null;
  }

  /** Extract a simple declarator name for fields. */
  private String extractDeclaratorName(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null) {
      return extractDeclaratorNameFromPointer(ctx.pointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  private String extractDeclaratorNameFromPointer(final CPP14Parser.PointerDeclaratorContext ctx) {
    if (ctx.noPointerDeclarator() != null) {
      return extractNameFromNoPointerDeclarator(ctx.noPointerDeclarator());
    }
    return null;
  }

  /**
   * Extract the return type of a function: the trailing return type if present, otherwise the
   * declSpecifierSeq type plus the pointer operators of the declarator.
   */
  private String extractReturnType(
      final CPP14Parser.DeclSpecifierSeqContext declSpecifierSeq,
      final CPP14Parser.DeclaratorContext declarator) {
    if (declarator.trailingReturnType() != null) {
      final CPP14Parser.TrailingReturnTypeContext trailing = declarator.trailingReturnType();
      final StringBuilder typeBuilder = new StringBuilder();
      if (trailing.trailingTypeSpecifierSeq() != null) {
        for (final CPP14Parser.TrailingTypeSpecifierContext spec :
            trailing.trailingTypeSpecifierSeq().trailingTypeSpecifier()) {
          if (spec.cvQualifier() != null) {
            continue;
          }
          if (typeBuilder.length() > 0) {
            typeBuilder.append(' ');
          }
          typeBuilder.append(spec.getText());
        }
      }
      if (typeBuilder.length() > 0) {
        if (trailing.abstractDeclarator() != null) {
          typeBuilder.append(extractAbstractPointerSuffix(trailing.abstractDeclarator()));
        }
        return typeBuilder.toString();
      }
    }
    if (declSpecifierSeq == null) {
      return "void";
    }
    return extractTypeFromDeclSpecifierSeq(declSpecifierSeq) + extractPointerSuffix(declarator);
  }

  /**
   * Extract the type from a declSpecifierSeq context, skipping cv-qualifiers (const, volatile) and
   * non-type specifiers.
   */
  private String extractTypeFromDeclSpecifierSeq(final CPP14Parser.DeclSpecifierSeqContext ctx) {
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
        if (!"static".equals(text)
            && !"extern".equals(text)
            && !"inline".equals(text)
            && !"virtual".equals(text)
            && !"explicit".equals(text)
            && !"friend".equals(text)
            && !"typedef".equals(text)
            && !"constexpr".equals(text)
            && !"mutable".equals(text)
            && !"register".equals(text)
            && !"thread_local".equals(text)) {
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
   * Extract the actual data type from a typeSpecifier, filtering out cv-qualifiers (const,
   * volatile).
   */
  private String extractActualType(final CPP14Parser.TypeSpecifierContext typeSpec) {
    // typeSpecifier -> trailingTypeSpecifier | classSpecifier | enumSpecifier
    if (typeSpec.classSpecifier() != null) {
      // A class definition used as a type, e.g. "struct { int x; } pos;": its name, not its body.
      final CPP14Parser.ClassHeadContext classHead = typeSpec.classSpecifier().classHead();
      if (classHead.classHeadName() != null) {
        return classHead.classHeadName().getText();
      }
      return classKeyText(classHead) + " " + ANONYMOUS_TYPE_NAME;
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

  /** Extract parameter types from a declarator's parameter list. */
  private List<String> extractParameterTypes(final CPP14Parser.DeclaratorContext ctx) {
    final List<String> paramTypes = new ArrayList<>();
    final CPP14Parser.ParametersAndQualifiersContext params = findParametersAndQualifiers(ctx);

    if (params != null
        && params.parameterDeclarationClause() != null
        && params.parameterDeclarationClause().parameterDeclarationList() != null) {
      for (final CPP14Parser.ParameterDeclarationContext param :
          params.parameterDeclarationClause().parameterDeclarationList().parameterDeclaration()) {
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

  /** Find the parametersAndQualifiers context within a declarator. */
  private CPP14Parser.ParametersAndQualifiersContext findParametersAndQualifiers(
      final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.parametersAndQualifiers() != null) {
      // noPointerDeclarator parametersAndQualifiers trailingReturnType
      return ctx.parametersAndQualifiers();
    }
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

  /** Add function modifiers (virtual, static, inline, etc.) to the method handler. */
  private void addFunctionModifiers(
      final MethodDataHandler methodData, final CPP14Parser.FunctionDefinitionContext ctx) {
    addDeclSpecifierModifiers(methodData, ctx.declSpecifierSeq());

    // Check for virtual specifiers (override, final)
    if (ctx.virtualSpecifierSeq() != null) {
      for (final CPP14Parser.VirtualSpecifierContext virtSpec :
          ctx.virtualSpecifierSeq().virtualSpecifier()) {
        methodData.addModifier(virtSpec.getText());
      }
    }
  }

  /** Add function and storage class specifiers (virtual, explicit, static, ...) as modifiers. */
  private void addDeclSpecifierModifiers(
      final MethodDataHandler methodData, final CPP14Parser.DeclSpecifierSeqContext ctx) {
    if (ctx == null) {
      return;
    }
    for (final CPP14Parser.DeclSpecifierContext declSpec : ctx.declSpecifier()) {
      if (declSpec.functionSpecifier() != null) {
        methodData.addModifier(declSpec.functionSpecifier().getText());
      } else if (declSpec.storageClassSpecifier() != null) {
        methodData.addModifier(declSpec.storageClassSpecifier().getText());
      }
    }
  }

  /** Add function parameters to the method handler. */
  private void addFunctionParameters(
      final MethodDataHandler methodData, final CPP14Parser.DeclaratorContext ctx) {
    final CPP14Parser.ParametersAndQualifiersContext params = findParametersAndQualifiers(ctx);

    if (params != null
        && params.parameterDeclarationClause() != null
        && params.parameterDeclarationClause().parameterDeclarationList() != null) {
      for (final CPP14Parser.ParameterDeclarationContext param :
          params.parameterDeclarationClause().parameterDeclarationList().parameterDeclaration()) {
        String paramType =
            param.declSpecifierSeq() != null
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

  /** Extract const and volatile modifiers from a parameter declaration. */
  private List<String> extractParameterModifiers(
      final CPP14Parser.ParameterDeclarationContext param) {
    final List<String> modifiers = new ArrayList<>();

    // Check declSpecifierSeq for const/volatile
    if (param.declSpecifierSeq() != null) {
      for (final CPP14Parser.DeclSpecifierContext declSpec :
          param.declSpecifierSeq().declSpecifier()) {
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
            for (final CPP14Parser.CvQualifierContext cvQual :
                ptrOp.cvQualifierSeq().cvQualifier()) {
              modifiers.add(cvQual.getText());
            }
          }
        }
      }
    }

    return modifiers;
  }

  /**
   * Check if a parameter declaration contains array brackets (e.g. int arr[]) and return "[]" if
   * so, otherwise empty string.
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
   * Return the pointer suffix ("*", "&", "&&", ...) of a parameter declaration (e.g. int *p, or the
   * unnamed int*).
   */
  private String extractPointerSuffix(final CPP14Parser.ParameterDeclarationContext param) {
    if (param.declarator() != null) {
      return extractPointerSuffix(param.declarator());
    }
    if (param.abstractDeclarator() != null) {
      return extractAbstractPointerSuffix(param.abstractDeclarator());
    }
    return "";
  }

  /** Return the pointer suffix ("*", "&", "&&", ...) of a declarator's pointer operators. */
  private String extractPointerSuffix(final CPP14Parser.DeclaratorContext declarator) {
    if (declarator == null || declarator.pointerDeclarator() == null) {
      return "";
    }
    return pointerOperatorsToString(declarator.pointerDeclarator().pointerOperator());
  }

  /** Return the pointer suffix of an abstract declarator (e.g. the "*" of an unnamed int*). */
  private String extractAbstractPointerSuffix(final CPP14Parser.AbstractDeclaratorContext ctx) {
    if (ctx.pointerAbstractDeclarator() == null) {
      return "";
    }
    return pointerOperatorsToString(ctx.pointerAbstractDeclarator().pointerOperator());
  }

  private static String pointerOperatorsToString(
      final List<CPP14Parser.PointerOperatorContext> pointerOperators) {
    final StringBuilder suffix = new StringBuilder();
    for (final CPP14Parser.PointerOperatorContext ptrOp : pointerOperators) {
      if (ptrOp.Star() != null) {
        suffix.append('*');
      } else if (ptrOp.AndAnd() != null) {
        suffix.append("&&");
      } else if (ptrOp.And() != null) {
        suffix.append('&');
      }
    }
    return suffix.toString();
  }

  /** Check if a declarator contains array brackets (LeftBracket / RightBracket). */
  private boolean hasArrayBrackets(final CPP14Parser.DeclaratorContext ctx) {
    if (ctx.pointerDeclarator() != null && ctx.pointerDeclarator().noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.pointerDeclarator().noPointerDeclarator());
    }
    if (ctx.noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.noPointerDeclarator());
    }
    return false;
  }

  private boolean hasArrayBracketsInNoPointer(final CPP14Parser.NoPointerDeclaratorContext ctx) {
    // noPointerDeclarator: ... | noPointerDeclarator LeftBracket ... RightBracket
    if (ctx.LeftBracket() != null) {
      return true;
    }
    if (ctx.noPointerDeclarator() != null) {
      return hasArrayBracketsInNoPointer(ctx.noPointerDeclarator());
    }
    return false;
  }

  /** Counts declared variables, ignoring typedefs and function declarations. */
  @Override
  public void enterSimpleDeclaration(final CPP14Parser.SimpleDeclarationContext ctx) {
    if (ctx.initDeclaratorList() == null || hasTypedef(ctx.declSpecifierSeq())) {
      return;
    }
    for (final CPP14Parser.InitDeclaratorContext initDecl :
        ctx.initDeclaratorList().initDeclarator()) {
      if (initDecl.declarator() != null && !isFunctionDeclarator(initDecl.declarator())) {
        variableCount++;
      }
    }
  }

  /** Get comment lines of code by counting tokens on the hidden channel. */
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
