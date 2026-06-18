package net.explorviz.code.analysis.visitor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.listener.CommonFileDataListener;
import net.explorviz.code.analysis.types.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visitor filling a FileData object with typical information about java files.
 * Includes the LOC (lines of code)
 * metric.
 */
public class FileDataVisitor extends VoidVisitorAdapter<JavaFileDataHandler> { // NOPMD

  private static final Logger LOGGER = LoggerFactory.getLogger(FileDataVisitor.class);
  private static final String UNKNOWN = "UNKNOWN";

  private final Optional<TypeSolver> fallbackTypeSolver;
  private final boolean wildcardImportProperty;
  private int wildcardImportCount;
  private String wildcardImport;
  private int functionCount = 0;
  private int variableCount = 0;

  /**
   * Create a FileDataVisitor.
   *
   * @param fallbackTypeSolver     a fallback type solver
   * @param wildcardImportProperty set true if wildcard imports should be handled
   */
  public FileDataVisitor(final Optional<TypeSolver> fallbackTypeSolver,
      final boolean wildcardImportProperty) {
    super();
    this.fallbackTypeSolver = fallbackTypeSolver;
    this.wildcardImportProperty = wildcardImportProperty;
  }

  @Override
  public void visit(final PackageDeclaration n, final JavaFileDataHandler data) {
    data.setPackageName(n.getNameAsString());
    super.visit(n, data);
  }

  @Override
  public void visit(final ImportDeclaration n, final JavaFileDataHandler data) {
    if (n.isAsterisk()) {
      if (wildcardImportCount == 0 && wildcardImport == null) {
        wildcardImport = n.getNameAsString();
      }
      wildcardImportCount++;
    }
    data.addImport(n.getNameAsString());
    super.visit(n, data);
  }

  @Override
  public void visit(final EnumDeclaration n, final JavaFileDataHandler data) {
    final String name = n.getNameAsString();
    final String fqn = n.getFullyQualifiedName().orElse(UNKNOWN);
    data.enterClass(name, fqn);
    data.getCurrentClassData().addMetric(CommonFileDataListener.SLOC, String.valueOf(getLoc(n) - getCloc(n)));
    data.getCurrentClassData().addMetric(CommonFileDataListener.LINE_COUNT, String.valueOf(getLoc(n)));
    data.getCurrentClassData().setIsEnum();
    for (final Modifier modifier : n.getModifiers()) {
      data.getCurrentClassData().addModifier(modifier.getKeyword().asString());
    }
    super.visit(n, data);
    data.leaveClass();
  }

  @Override
  public void visit(final FieldDeclaration n, final JavaFileDataHandler data) {
    data.enterMethod(data.getCurrentClassFqn() + "." + n.getVariable(0).getNameAsString());
    final List<String> modifierList = new ArrayList<>();
    for (final Modifier modifier : n.getModifiers()) {
      modifierList.add(modifier.getKeyword().asString());
    }
    for (final VariableDeclarator declarator : n.getVariables()) {
      data.getCurrentClassData()
          .addField(declarator.getNameAsString(), resolveFqn(declarator.getType(), data),
              modifierList);
    }
    super.visit(n, data);
    data.leaveMethod();
  }

  @Override
  public void visit(final VariableDeclarator n, final JavaFileDataHandler data) {
    variableCount++;
    super.visit(n, data);
  }

  @Override // NOCS
  public void visit(final ClassOrInterfaceDeclaration n,
      final JavaFileDataHandler data) { // NOCS NOPMD
    final String name = n.getNameAsString();
    final String classFqn = n.getFullyQualifiedName().orElse(UNKNOWN);
    data.enterClass(name, classFqn);
    data.getCurrentClassData().addMetric(CommonFileDataListener.SLOC, String.valueOf(getLoc(n) - getCloc(n)));
    data.getCurrentClassData().addMetric(CommonFileDataListener.LINE_COUNT, String.valueOf(getLoc(n)));

    if (n.isInterface()) {
      data.getCurrentClassData().setIsInterface();
    } else if (n.isAbstract()) {
      data.getCurrentClassData().setIsAbstractClass();
    } else {
      data.getCurrentClassData().setIsClass();
    }

    for (final Modifier modifier : n.getModifiers()) {
      data.getCurrentClassData().addModifier(modifier.getKeyword().asString());
    }

    for (final ClassOrInterfaceType classOrInterfaceType : n.getExtendedTypes()) {
      final String fqn = resolveFqn(classOrInterfaceType, data);
      if (data.getCurrentClassData().isClass() || data.getCurrentClassData().isAbstractClass()) {
        data.getCurrentClassData().setSuperClass(fqn);
      } else if (data.getCurrentClassData().isInterface()) {
        data.getCurrentClassData().addImplementedInterface(fqn);
      } else {
        if (LOGGER.isErrorEnabled()) {
          LOGGER.error(
              "Unexpected Error, Declaration is neither Interface, AbstractClass nor Class but"
                  + " has Interface(s) attached to it");
        }
      }
    }

    for (final ClassOrInterfaceType classOrInterfaceType : n.getImplementedTypes()) {
      data.getCurrentClassData().addImplementedInterface(resolveFqn(classOrInterfaceType, data));
    }
    for (final AnnotationExpr annotation : n.getAnnotations()) {
      // TODO change resolve pipeline to accommodate for annotations
      try {
        final String ann = annotation.resolve().getQualifiedName();
        data.getCurrentClassData().addAnnotation(ann);
      } catch (UnsolvedSymbolException | IllegalStateException e) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn("Annotation's FQN for class was not resolvable");
        }
        data.getCurrentClassData().addAnnotation(annotation.getNameAsString());
      }
    }
    super.visit(n, data);
    data.leaveClass();
  }

  @Override
  public void visit(final MethodDeclaration n, final JavaFileDataHandler data) {
    final String methodsFullyQualifiedName = data.getCurrentClassFqn() + "." + n.getNameAsString() + "#"
        + Verification.parameterHash(
            n.getParameters());
    data.enterMethod(methodsFullyQualifiedName);
    final String returnType = resolveFqn(n.getType(), data);
    // data.setLastAddedMethodFqn(methodsFullyQualifiedName);
    final MethodDataHandler method = data.getCurrentClassData()
        .addMethod(n.getNameAsString(), methodsFullyQualifiedName, returnType);
    for (final Modifier modifier : n.getModifiers()) {
      method.addModifier(modifier.getKeyword().asString());
    }
    for (final Parameter parameter : n.getParameters()) {
      method.addParameter(parameter.getNameAsString(), resolveFqn(parameter.getType(), data),
          parameter.getModifiers());
    }
    for (final AnnotationExpr annotation : n.getAnnotations()) {
      // TODO change resolve pipeline to accommodate for annotations
      try {
        final String ann = annotation.resolve().getQualifiedName();
        method.addAnnotation(ann);
      } catch (UnsolvedSymbolException | IllegalStateException e) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn("Annotation's FQN for method was not resolvable");
        }
        method.addAnnotation(annotation.getNameAsString());
      }
    }
    method.addMetric(CommonFileDataListener.SLOC, String.valueOf(getLoc(n) - getCloc(n)));
    method.addMetric(CommonFileDataListener.LINE_COUNT, String.valueOf(getLoc(n)));
    functionCount++;
    super.visit(n, data);
    data.leaveMethod();
  }

  @Override
  public void visit(final ConstructorDeclaration n, final JavaFileDataHandler data) {
    final String constructorsFullyQualifiedName = data.getCurrentClassFqn() + "." + n.getNameAsString() + "#"
        + Verification.parameterHash(
            n.getParameters());
    data.enterMethod(constructorsFullyQualifiedName);
    final MethodDataHandler constructor = data.getCurrentClassData()
        .addConstructor(n.getNameAsString(), constructorsFullyQualifiedName);
    for (final Modifier modifier : n.getModifiers()) {
      constructor.addModifier(modifier.getKeyword().asString());
    }
    for (final Parameter parameter : n.getParameters()) {
      constructor.addParameter(parameter.getNameAsString(), resolveFqn(parameter.getType(), data),
          parameter.getModifiers());
    }
    constructor.addMetric(CommonFileDataListener.SLOC, String.valueOf(getLoc(n) - getCloc(n)));
    constructor.addMetric(CommonFileDataListener.LINE_COUNT, String.valueOf(getLoc(n)));
    functionCount++;
    super.visit(n, data);
    data.leaveMethod();
  }

  @Override
  public void visit(final EnumConstantDeclaration n, final JavaFileDataHandler data) {
    data.getCurrentClassData().addEnumConstant(n.getNameAsString());
    super.visit(n, data);
  }

  @Override
  public void visit(final CompilationUnit n, final JavaFileDataHandler data) {
    final int loc = getLoc(n);
    final int sloc = loc - getCloc(n);
    final String locValue = String.valueOf(loc);
    final String slocValue = String.valueOf(sloc);
    final String clocValue = String.valueOf(getCloc(n));
    data.addMetric(CommonFileDataListener.LINE_COUNT, locValue);
    data.addMetric(CommonFileDataListener.SLOC, slocValue);
    data.addMetric(CommonFileDataListener.CLOC, clocValue);
    super.visit(n, data);
    data.addMetric(CommonFileDataListener.IMPORT_COUNT, String.valueOf(data.getImportCount()));
    data.addMetric(CommonFileDataListener.CLASS_COUNT, String.valueOf(data.getClassCount()));
    data.addMetric(CommonFileDataListener.FUNCTION_COUNT, String.valueOf(functionCount));
    data.addMetric(CommonFileDataListener.VARIABLE_COUNT, String.valueOf(variableCount));
  }

  // If FieldAccessExpr, then tight coupling
  @Override
  public void visit(final MethodCallExpr n, final JavaFileDataHandler data) {
    // System.out.println(n.getNameAsString());
    super.visit(n, data);
  }

  @Override
  public void visit(final ObjectCreationExpr n, final JavaFileDataHandler data) {
    if (n.getAnonymousClassBody().isPresent()) {
      data.enterAnonymousClass(n.getTypeAsString(), data.getCurrentMethodFqn() + "$Anonymous");
      super.visit(n, data);
      data.leaveAnonymousClass();
    } else {
      super.visit(n, data);
    }
  }

  private String resolveFqn(final Type type, final JavaFileDataHandler data) {
    try {
      final ResolvedType resolvedType = type.resolve();
      if (resolvedType.isReferenceType()) {
        return buildResolvedTypeFullDepthType(resolvedType.asReferenceType());
      } else {
        return type.toString();
      }
    } catch (UnsolvedSymbolException | IllegalStateException e) {
      return findFqnInImports(type, data);
    } catch (UnsupportedOperationException e) {
      String typeName = type.toString();
      if (e.getMessage().contains("CorrespondingDeclaration")) {
        typeName = solveTypeInCurrentContext(type.toString());
      }
      if (typeName.equals(type.toString())) {
        typeName = findFqnInImports(type, data);
      }
      if (LOGGER.isWarnEnabled() && typeName.equals(type.toString())) {
        LOGGER.warn("Type <" + typeName + "> could not be solved.");
      }
      return typeName;
    }
  }

  private String buildResolvedTypeFullDepthType(final ResolvedReferenceType resolvedType) {
    final List<String> genericList = new ArrayList<>();
    for (final ResolvedType rt : resolvedType.typeParametersValues()) {
      if (rt.isReferenceType()) {
        genericList.add(buildResolvedTypeFullDepthType(rt.asReferenceType()));
      } else if (rt.isTypeVariable()) {
        genericList.add(rt.asTypeParameter().getName()); // TODO Does not work!
      } else {
        genericList.add(rt.toString());
      }
    }
    return resolvedType.asReferenceType().getQualifiedName() + typeListToGeneric(genericList);
  }

  /**
   * Returns the FQN for the type by simply comparing it with potential imports.
   * If no import matches the type, the type
   * itself will be returned
   *
   * @param type the type of the Object
   * @return the fqn or the original type
   */
  private String findFqnInImports(final Type type, final JavaFileDataHandler data) { // NOPMD NOCS
    final List<String> imports = data.getImportNames();
    String attachedGenerics = "";
    if (type instanceof ClassOrInterfaceType) {
      final ClassOrInterfaceType classOrInterfaceType = type.asClassOrInterfaceType();
      if (classOrInterfaceType.getTypeArguments().isPresent()) {
        final List<String> typeList = new ArrayList<>();
        for (final Type localType : classOrInterfaceType.getTypeArguments().get()) {
          typeList.add(resolveFqn(localType, data));
        }
        attachedGenerics = typeListToGeneric(typeList);
      }
      for (final String importEntry : imports) {
        if (importEntry.endsWith(classOrInterfaceType.getName().asString())) {
          return importEntry + attachedGenerics;
        }
      }
    }
    // check imports
    for (final String importEntry : imports) {
      if (type.asString().contains(".")) {
        final String[] a = type.asString().split("\\.");
        if (importEntry.endsWith(a[0])) {
          final String result = Arrays.stream(a).filter(str -> !str.equals(a[0]))
              .collect(Collectors.joining("."));
          return importEntry + "." + result + attachedGenerics;
        }
      }
      if (importEntry.endsWith(type.asString())) {
        return importEntry + attachedGenerics;
      }
    }

    if (wildcardImportProperty && wildcardImportCount == 1) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("assumed type from wildcard import: " + wildcardImport + "." + type.asString()
            + attachedGenerics);
      }
      return wildcardImport + "." + type.asString() + attachedGenerics;
    }

    if (LOGGER.isWarnEnabled()) {
      // if wildcard in imports, note here that it might be possible the type is
      // defined there
      if (wildcardImportCount > 1 && wildcardImportProperty) {
        LOGGER.warn("File contains multiple wildcard imports, type <" + type.asString() // NOPMD
            + "> is ambiguous.");
      } else {
        if (wildcardImportCount > 0 && !wildcardImportProperty) {
          LOGGER.warn("File contains wildcard import(s), type <" + type.asString() // NOPMD
              + "> might be defined there. Type assumption by wildcards is turned off.");
        } else {
          LOGGER.warn("Unable to get FQN for <" + type.asString() + ">"); // NOPMD
        }
      }
    }
    return type.asString() + attachedGenerics;
  }

  private String typeListToGeneric(final List<String> typeList) {
    if (typeList.isEmpty()) {
      return "";
    }
    final StringBuilder generics = new StringBuilder("<");
    for (int i = 0; i < typeList.size(); i++) {
      generics.append(typeList.get(i)).append(i + 1 == typeList.size() ? ">" : ", ");
    }
    return generics.toString();
  }

  private int getLoc(final Node node) {
    if (node.getRange().isPresent()) {
      return node.getRange().get().getLineCount();
    }
    if (LOGGER.isErrorEnabled()) {
      LOGGER.error("Getting the lines of code failed!");
    }
    return 0;
  }

  private int getCloc(final Node node) {
    int linesOfComments = 0;
    if (node.getRange().isPresent()) {
      for (final Comment commentNode : node.getAllContainedComments()) {
        if (commentNode.getRange().isPresent()) {
          linesOfComments += commentNode.getRange().get().getLineCount();
        }
      }
    } else {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error("Getting the comment lines of code failed!");
      }
    }
    return linesOfComments;
  }

  private String solveTypeInCurrentContext(final String name) {
    if (fallbackTypeSolver.isPresent()) {
      // Don't know why, but symbol solver seems to have problems with
      for (final String builtInPackage : Arrays.asList("", "java.lang.")) {
        final SymbolReference<ResolvedReferenceTypeDeclaration> ref = fallbackTypeSolver.get()
            .tryToSolveType(builtInPackage + name);
        if (ref.isSolved()) {
          return ref.getCorrespondingDeclaration().getQualifiedName();
        }
      }
    }
    return name;
  }
}
