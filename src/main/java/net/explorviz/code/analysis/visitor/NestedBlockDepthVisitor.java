package net.explorviz.code.analysis.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.handler.MetricAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This Visitor calculates the nestedBlockDepth for methods. */
public class NestedBlockDepthVisitor extends VoidVisitorAdapter<Pair<MetricAppender, Object>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(NestedBlockDepthVisitor.class);

  private static final String METRIC_NAME = "nestedBlockDepth";

  private int maxDepth;
  private int currentDepth;

  @Override
  public void visit(final ClassOrInterfaceDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterClass(n);
    super.visit(n, data);
    data.a.leaveClass();
  }

  @Override
  public void visit(final EnumDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterClass(n);
    super.visit(n, data);
    data.a.leaveClass();
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
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, data);
    try {
      data.a.putMethodMetric(METRIC_NAME, String.valueOf(maxDepth));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    data.a.leaveMethod();
    maxDepth = 0;
    currentDepth = 0;
  }

  @Override
  public void visit(final ConstructorDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(n);
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, data);
    try {
      data.a.putMethodMetric(METRIC_NAME, String.valueOf(maxDepth));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    data.a.leaveMethod();
    maxDepth = 0;
    currentDepth = 0;
  }

  @Override
  public void visit(final ObjectCreationExpr n, final Pair<MetricAppender, Object> data) {
    if (n.getAnonymousClassBody().isPresent()) {
      data.a.enterAnonymousClass(n.getTypeAsString(), data.a.getCurrentMethodName());
      super.visit(n, data);
      data.a.leaveAnonymousClass();
    } else {
      super.visit(n, data);
    }
  }

  @Override
  public void visit(final ForStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final ForEachStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final WhileStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final DoStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final IfStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final TryStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final SwitchEntry n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final SwitchStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final LambdaExpr n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }

  @Override
  public void visit(final SynchronizedStmt n, final Pair<MetricAppender, Object> arg) {
    currentDepth++;
    maxDepth = Math.max(maxDepth, currentDepth);
    super.visit(n, arg);
    currentDepth--;
  }
}
