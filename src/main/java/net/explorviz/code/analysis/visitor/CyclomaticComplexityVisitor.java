package net.explorviz.code.analysis.visitor;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.Pair;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.handler.MetricAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Visitor to calculate the cyclomatic complexity for methods and classes. */
public class CyclomaticComplexityVisitor extends VoidVisitorAdapter<Pair<MetricAppender, Object>> {

  public static final String CYCLOMATIC_COMPLEXITY = "cyclomatic_complexity";
  private static final Logger LOGGER = LoggerFactory.getLogger(CyclomaticComplexityVisitor.class);
  private static final String CYCLOMATIC_COMPLEXITY_WEIGHTED = "cyclomatic_complexity_weighted";

  private final Map<String, Integer> methodCounter;

  public CyclomaticComplexityVisitor() {
    super();
    methodCounter = new HashMap<>();
  }

  @Override
  public void visit(final FieldDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(data.a.getCurrentClassName() + "." + n.getVariable(0).getNameAsString());
    super.visit(n, data);
    data.a.leaveMethod();
  }

  @Override
  public void visit(final ClassOrInterfaceDeclaration n, final Pair<MetricAppender, Object> data) {
    methodCounter.clear();

    data.a.enterClass(n);
    super.visit(n, data);
    int metricValue = 0;
    for (final Map.Entry<String, Integer> entry : methodCounter.entrySet()) {
      metricValue += methodCounter.get(entry.getKey());
    }
    if (metricValue == 0) {
      metricValue = 1;
    }
    // set the class metric
    try {
      data.a.putClassMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    if (!methodCounter.isEmpty()) {
      metricValue = metricValue / methodCounter.size();
    }
    try {
      data.a.putClassMetric(CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    // Update the file metric
    final Integer oldValue =
        Integer.getInteger(data.a.getFileData().getMetricValue(CYCLOMATIC_COMPLEXITY));
    if (oldValue == null) {
      data.a.putFileMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } else {
      data.a.putFileMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(oldValue + metricValue));
    }
    data.a.leaveClass();
  }

  @Override
  public void visit(final EnumDeclaration n, final Pair<MetricAppender, Object> data) {
    methodCounter.clear();

    data.a.enterClass(n);
    super.visit(n, data);
    int metricValue = 0;
    for (final Map.Entry<String, Integer> entry : methodCounter.entrySet()) {
      metricValue += methodCounter.get(entry.getKey());
    }
    if (metricValue == 0) {
      metricValue = 1;
    }
    // set the class metric
    try {
      data.a.putClassMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    if (!methodCounter.isEmpty()) {
      metricValue = metricValue / methodCounter.size();
    }
    try {
      data.a.putClassMetric(CYCLOMATIC_COMPLEXITY_WEIGHTED, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }

    // Update the file metric
    final Integer oldValue =
        Integer.getInteger(data.a.getFileData().getMetricValue(CYCLOMATIC_COMPLEXITY));
    if (oldValue == null) {
      data.a.putFileMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } else {
      data.a.putFileMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(oldValue + metricValue));
    }
    data.a.leaveClass();
  }

  @Override
  public void visit(final ConstructorDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(n);
    super.visit(n, data);
    final int metricValue = methodCounter.getOrDefault(data.a.getCurrentMethodName(), 1);
    try {
      data.a.putMethodMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    data.a.leaveMethod();
  }

  @Override
  public void visit(final MethodDeclaration n, final Pair<MetricAppender, Object> data) {
    data.a.enterMethod(n);
    super.visit(n, data);
    final int metricValue = methodCounter.getOrDefault(data.a.getCurrentMethodName(), 1);
    try {
      data.a.putMethodMetric(CYCLOMATIC_COMPLEXITY, String.valueOf(metricValue));
    } catch (NotFoundException e) {
      // metric was not addable.
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(e.getMessage(), e);
      }
    }
    data.a.leaveMethod();
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
  public void visit(final ForEachStmt statement, final Pair<MetricAppender, Object> data) {
    addOccurrence(data.a.getCurrentMethodName());

    super.visit(statement, data);
  }

  @Override
  public void visit(final ForStmt statement, final Pair<MetricAppender, Object> data) {
    addOccurrence(data.a.getCurrentMethodName());

    if (statement.getCompare().isPresent()) {
      conditionCheck(statement.getCompare().get().toString(), data);
    }

    super.visit(statement, data);
  }

  @Override
  public void visit(final IfStmt statement, final Pair<MetricAppender, Object> data) {
    addOccurrence(data.a.getCurrentMethodName());

    if (statement.getElseStmt().isPresent()) {
      addOccurrence(data.a.getCurrentMethodName());
    }

    conditionCheck(statement.getCondition().toString(), data);

    super.visit(statement, data);
  }

  @Override
  public void visit(final SwitchEntry statement, final Pair<MetricAppender, Object> data) {

    for (final Statement ignored : statement.getStatements()) {
      addOccurrence(data.a.getCurrentMethodName());
    }

    super.visit(statement, data);
  }

  @Override
  public void visit(final CatchClause statement, final Pair<MetricAppender, Object> data) {

    addOccurrence(data.a.getCurrentMethodName());

    super.visit(statement, data);
  }

  @Override
  public void visit(final ThrowStmt statement, final Pair<MetricAppender, Object> data) {

    addOccurrence(data.a.getCurrentMethodName());

    super.visit(statement, data);
  }

  @Override
  public void visit(final TryStmt statement, final Pair<MetricAppender, Object> data) {

    addOccurrence(data.a.getCurrentMethodName());

    super.visit(statement, data);
  }

  @Override
  public void visit(final WhileStmt statement, final Pair<MetricAppender, Object> data) {

    addOccurrence(data.a.getCurrentMethodName());

    conditionCheck(statement.getCondition().toString(), data);

    super.visit(statement, data);
  }

  private void conditionCheck(final String condition, final Pair<MetricAppender, Object> data) {
    regexCheck(condition, Pattern.compile("/(\\s|\\w|\\d)&(\\s|\\w|\\d)/xg"), data);
    regexCheck(condition, Pattern.compile("/(\\s|\\w|\\d)\\|(\\s|\\w|\\d)/xg"), data);
    regexCheck(condition, Pattern.compile("/(\\s|\\w|\\d)&&(\\s|\\w|\\d)/xg"), data);
    regexCheck(condition, Pattern.compile("/(\\s|\\w|\\d)\\|\\|(\\s|\\w|\\d)/xg"), data);
  }

  private void regexCheck(
      final String haystack, final Pattern pattern, final Pair<MetricAppender, Object> data) {
    final Matcher matcher = pattern.matcher(haystack);

    while (matcher.find()) {
      addOccurrence(data.a.getCurrentMethodName());
    }
  }

  private void addOccurrence(final String methodName) {

    // check if such method was ever updated
    if (methodCounter.containsKey(methodName)) {

      methodCounter.put(methodName, methodCounter.get(methodName) + 1);
    } else {

      // method name was never queried. create a new HashMap
      methodCounter.put(methodName, 1);
    }
  }
}
