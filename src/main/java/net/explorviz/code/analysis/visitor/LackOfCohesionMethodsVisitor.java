package net.explorviz.code.analysis.visitor;

import com.github.javaparser.Range;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.handler.MetricAppender;
import net.explorviz.code.analysis.types.Graph;

/** Basic LCOM4 implementation. */
public class LackOfCohesionMethodsVisitor extends VoidVisitorAdapter<Pair<MetricAppender, Object>> {

  private static final String METRIC_NAME = "LCOM4";
  private final Stack<Graph> graphStack = new Stack<>();
  private final Stack<List<FieldDeclaration>> fieldsStack = new Stack<>();
  private final Stack<List<String>> fieldNamesStack = new Stack<>();
  private final Stack<List<String>> methodNamesStack = new Stack<>();
  private Graph currentGraph;
  private List<FieldDeclaration> currentFields;
  private List<String> currentFieldNames;
  private List<String> currentMethodNames;

  @Override
  public void visit(final ClassOrInterfaceDeclaration n, final Pair<MetricAppender, Object> data) {
    initNewClass();

    data.a.enterClass(n);

    currentFields = n.getFields();
    currentFieldNames =
        n.getFields().stream()
            .map(field -> field.getVariables().getFirst().get().getName().asString())
            .collect(Collectors.toList());
    for (final String fieldName : currentFieldNames) {
      currentGraph.addVertex(fieldName, true);
    }
    currentMethodNames =
        n.getMethods().stream()
            .map(NodeWithSimpleName::getNameAsString)
            .collect(Collectors.toList());
    for (final String methodName : currentMethodNames) {
      currentGraph.addVertex(methodName);
    }

    super.visit(n, data);

    try {
      data.a.putClassMetric(METRIC_NAME, String.valueOf(currentGraph.getGroups().size()));
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
    data.a.leaveClass();
    leaveClass();
  }

  @Override
  public void visit(final EnumDeclaration n, final Pair<MetricAppender, Object> data) {
    initNewClass();

    data.a.enterClass(n);

    currentFields = n.getFields();
    currentFieldNames =
        n.getFields().stream()
            .map(field -> field.getVariables().getFirst().get().getName().asString())
            .collect(Collectors.toList());
    for (final String fieldName : currentFieldNames) {
      currentGraph.addVertex(fieldName, true);
    }
    currentMethodNames =
        n.getMethods().stream()
            .map(NodeWithSimpleName::getNameAsString)
            .collect(Collectors.toList());
    for (final String methodName : currentMethodNames) {
      currentGraph.addVertex(methodName);
    }

    super.visit(n, data);

    try {
      data.a.putClassMetric(METRIC_NAME, String.valueOf(currentGraph.getGroups().size()));
    } catch (NotFoundException e) {
      throw new RuntimeException(e);
    }
    data.a.leaveClass();
    leaveClass();
  }

  @Override
  public void visit(final FieldDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(data.a.getCurrentClassName() + "." + n.getVariable(0).getNameAsString());
    super.visit(n, data);
    data.a.leaveMethod();
  }

  @Override
  public void visit(final MethodDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(n);
    // Skip this method if it is inherited, remove the graph entry
    if (n.isAnnotationPresent("Override")) {
      currentGraph.removeVertex(n.getNameAsString());
      return;
    }
    // it the method is empty, remove the graph entry
    if (n.getBody().isEmpty()
        || n.getBody().get().getStatements().isEmpty()
            && n.getBody().get().getChildNodes().isEmpty()) {
      currentGraph.removeVertex(n.getNameAsString());
      return;
    }
    // field access
    for (final FieldAccessExpr expr : n.findAll(FieldAccessExpr.class)) {
      if (expr.isInternal()) {
        continue;
      }
      if (findInClassFields(expr.getNameAsString())) {
        currentGraph.addEdge(n.getNameAsString(), expr.getNameAsString());
      }
    }
    final List<Pair<String, Optional<Range>>> localVariables = new ArrayList<>();
    for (final VariableDeclarator variableDeclarator : n.findAll(VariableDeclarator.class)) {
      localVariables.add(
          new Pair<>(variableDeclarator.getNameAsString(), variableDeclarator.getRange()));
    }

    for (final NameExpr nameExpr : n.findAll(NameExpr.class)) {
      if (currentFieldNames.contains(nameExpr.getNameAsString())
          && isNotShadowedByLocalVariable(localVariables, nameExpr)) {
        currentGraph.addEdge(n.getNameAsString(), nameExpr.getNameAsString());
      }
    }

    for (final MethodCallExpr method : n.findAll(MethodCallExpr.class)) {
      if (isClassMethod(method)) {
        // add something to method name so fields and methods can have the same name
        currentGraph.addEdge(n.getNameAsString(), method.getNameAsString());
      }
    }
    super.visit(n, data);
    data.a.leaveMethod();
  }

  @Override
  public void visit(final ObjectCreationExpr n, final Pair<MetricAppender, Object> data) {
    if (n.getAnonymousClassBody().isEmpty()) {
      super.visit(n, data);
    }
  }

  private boolean isNotShadowedByLocalVariable(
      final List<Pair<String, Optional<Range>>> localVariables, final NameExpr nameExpr) {
    for (final Pair<String, Optional<Range>> entry : localVariables) {
      if (nameExpr.getNameAsString().equals(entry.a)
          && entry.b.isPresent()
          && nameExpr.getRange().isPresent()) {
        return entry.b.get().isAfter(nameExpr.getRange().get().end);
      }
    }
    return true;
  }

  private boolean findInClassFields(final String field) {
    for (final FieldDeclaration f : currentFields) {
      if (f.getVariables().getFirst().isPresent()
          && field.contains(f.getVariables().getFirst().get().getName().toString())) {
        return true;
      }
    }
    return false;
  }

  private boolean isClassMethod(final MethodCallExpr method) {
    if (method.getScope().isEmpty() || method.getScope().get().isThisExpr()) {
      return currentMethodNames.contains(method.getNameAsString());
    } else {
      return false;
    }
  }

  private void initNewClass() {
    graphStack.push(new Graph());
    currentGraph = graphStack.peek();
    fieldsStack.push(new ArrayList<>());
    currentFields = fieldsStack.peek();
    fieldNamesStack.push(new ArrayList<>());
    currentFieldNames = fieldNamesStack.peek();
    methodNamesStack.push(new ArrayList<>());
    currentMethodNames = methodNamesStack.peek();
  }

  private void leaveClass() {
    graphStack.pop();
    currentGraph = graphStack.isEmpty() ? null : graphStack.peek();
    fieldsStack.pop();
    currentFields = fieldsStack.isEmpty() ? null : fieldsStack.peek();
    fieldNamesStack.pop();
    currentFieldNames = fieldNamesStack.isEmpty() ? null : fieldNamesStack.peek();
    methodNamesStack.pop();
    currentMethodNames = methodNamesStack.isEmpty() ? null : methodNamesStack.peek();
  }
}
