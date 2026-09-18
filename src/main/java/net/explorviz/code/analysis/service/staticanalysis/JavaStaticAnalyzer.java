package net.explorviz.code.analysis.service.staticanalysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;
import net.explorviz.code.analysis.service.AnalysisConfig;

public class JavaStaticAnalyzer {

  private JavaStaticAnalyzer() {}

  public static void analyze(
      final JavaFileDataHandler fileDataHandler,
      final AnalysisConfig config,
      final String fileContent) {

    System.out.println("JavaStaticAnalyzer.analyze called for: " + fileDataHandler.getFileName());

    final JavaParser javaParser = new JavaParser();
    final Optional<CompilationUnit> result = javaParser.parse(fileContent).getResult();

    if (result.isEmpty()) {
      return;
    }

    final CompilationUnit cu = result.get();

    // Package-Filter: Datei nur analysieren, wenn ihr Package zu den erlaubten Präfixen passt
    final String packageName = cu.getPackageDeclaration()
        .map(pd -> pd.getNameAsString())
        .orElse("");
    if (!config.matchesPackageFilter(packageName)) {
      cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
        final String classKey = classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString());
        fileDataHandler.clearStaticDepsForClass(classKey);
      });
      fileDataHandler.clearImports();
      return;
    }

    // ANTLR liefert bei FileDateHandler die selbe Analyse für import, extend, implement und uses-Type ebenfalls
    // JavaStaticAnalyzer soll die alleinige Quelle sein, deshalb werden die ANTLR-Werte hier verworfen,
    // bevor die eigene Analyse sie neu aufbaut.
    fileDataHandler.clearImports();
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
      final String classKey = classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString());
      fileDataHandler.clearStaticDepsForClass(classKey);
    });

    // IMPORT
    if (config.analyzeImports()) {
      cu.getImports().forEach(importDecl -> {
        System.out.println("Found IMPORT: " + importDecl.getNameAsString());
        fileDataHandler.addImport(importDecl.getNameAsString());
      });
    }

    // EXTENDS, IMPLEMENTS, CALLS, USES_TYPE
    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
      final String className = classDecl.getNameAsString();
      final String classKey = classDecl.getFullyQualifiedName().orElse(className);

      final List<String> superClasses = new ArrayList<>();
      final List<String> interfaces = new ArrayList<>();

      // EXTENDS
      if (config.analyzeExtends()) {
        classDecl.getExtendedTypes().forEach(extendedType -> {
          System.out.println("Found EXTENDS: " + className + " -> " + extendedType.getNameAsString());
          superClasses.add(extendedType.getNameAsString());
        });
      }

      // IMPLEMENTS
      if (config.analyzeImplements()) {
        classDecl.getImplementedTypes().forEach(implementedType -> {
          System.out.println("Found IMPLEMENTS: " + className + " -> " + implementedType.getNameAsString());
          interfaces.add(implementedType.getNameAsString());
        });
      }

      fileDataHandler.updateClassWithStaticDeps(classKey, superClasses, interfaces);

      // CALLS (Option A: nur direkt erkennbare Ziele - new X(), X.method(), super.method())
      if (config.analyzeCalls()) {
        classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
          final String methodName = methodDecl.getNameAsString();

          // new X()
          methodDecl.findAll(ObjectCreationExpr.class).forEach(oce -> {
            final String targetClass = oce.getType().getNameAsString();
            System.out.println("Found CALL (constructor): " + className + "." + methodName + " -> " + targetClass);
            fileDataHandler.addOutgoingMethodCall(classKey, methodName, targetClass);
          });

          // X.method() - statischer Aufruf, erkannt an Großbuchstaben-Scope
          methodDecl.findAll(MethodCallExpr.class).forEach(mce -> {
            mce.getScope().ifPresent(scope -> {
              if (scope.isNameExpr()) {
                final String scopeName = scope.asNameExpr().getNameAsString();
                if (!scopeName.isEmpty() && Character.isUpperCase(scopeName.charAt(0))) {
                  System.out.println("Found CALL (static): " + className + "." + methodName + " -> " + scopeName);
                  fileDataHandler.addOutgoingMethodCall(classKey, methodName, scopeName);
                }
              } else if (scope.isSuperExpr() && !superClasses.isEmpty()) {
                final String targetClass = superClasses.get(0);
                System.out.println("Found CALL (super): " + className + "." + methodName + " -> " + targetClass);
                fileDataHandler.addOutgoingMethodCall(classKey, methodName, targetClass);
              }
            });
          });
        });
      }

      // USES_TYPE
      if (config.analyzeUsesType()) {
        classDecl.getFields().forEach(fieldDecl -> {
          final String fieldType = fieldDecl.getElementType().asString();
          fieldDecl.getVariables().forEach(variable -> {
            System.out.println("Found FIELD TYPE: " + className + "." + variable.getNameAsString() + " -> " + fieldType);
            fileDataHandler.addFieldType(classKey, fieldType);
          });
        });
      }
    });
  }
}
