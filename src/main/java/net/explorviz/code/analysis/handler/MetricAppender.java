package net.explorviz.code.analysis.handler;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.analysis.exceptions.NotFoundException;
import net.explorviz.code.analysis.types.Verification;

/**
 * The MetricAppender is a helper Object to facilitate the adding of metric data to the internal
 * FileData object. It provides basic functionality to keep track of the class hierarchy and handles
 * the adding of metrics to the right objects.
 */
public class MetricAppender {

  private static final String OBJECT_NOT_FOUND = "Object to add metric not found.";
  private static final String UNKNOWN = "UNKNOWN";
  private final JavaFileDataHandler fileData;
  private final Stack<String> classStack;
  private final Stack<String> methodStack;
  private final Map<String, Integer> anonymousCounter;

  /**
   * Creates a MetricAppender to facilitate the access to the given {@link JavaFileDataHandler}.
   *
   * @param fileDataHandler the fileDataHandler to wrap and access.
   */
  public MetricAppender(final JavaFileDataHandler fileDataHandler) {
    this.fileData = fileDataHandler;
    this.classStack = new Stack<>();
    this.methodStack = new Stack<>();
    this.anonymousCounter = new HashMap<>();
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the current file.
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @return returns the old value of the metric if the metric exists, otherwise null
   */
  public String putFileMetric(final String metricName, final String metricValue) {
    return fileData.addMetric(metricName, metricValue);
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the current class set
   * by {@link #enterClass(ClassOrInterfaceDeclaration)}
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the class object was not found and therefore the
   *     metric could not be added
   */
  public String putClassMetric(final String metricName, final String metricValue)
      throws NotFoundException {
    try {
      final ClassDataHandler classDataHandler = fileData.getClassData(classStack.peek());
      return classDataHandler.addMetric(metricName, metricValue);
    } catch (NullPointerException e) {
      final NotFoundException notFoundException =
          new NotFoundException("Not inside class. Object to add metric not found.");
      notFoundException.addSuppressed(e);
      throw notFoundException;
    }
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the given class.
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @param clazz the class object to add the metric to
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the method object was not found and therefore the
   *     metric could not be added
   */
  public String putClassMetric(
      final String metricName, final String metricValue, final ClassOrInterfaceDeclaration clazz)
      throws NotFoundException {
    try {
      return fileData
          .getClassData(clazz.getFullyQualifiedName().orElse(UNKNOWN))
          .addMetric(metricName, metricValue);
    } catch (NullPointerException e) {
      final NotFoundException notFoundException = new NotFoundException(OBJECT_NOT_FOUND);
      notFoundException.addSuppressed(e);
      throw notFoundException;
    }
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the class defined by
   * classFqn.
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @param classFqn the fqn of the class
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the method object was not found and therefore the
   *     metric could not be added
   */
  public String putClassMetric(
      final String metricName, final String metricValue, final String classFqn)
      throws NotFoundException {
    try {
      return fileData.getClassData(classFqn).addMetric(metricName, metricValue);
    } catch (NullPointerException e) {
      final NotFoundException notFoundException = new NotFoundException(OBJECT_NOT_FOUND);
      notFoundException.addSuppressed(e);
      throw notFoundException;
    }
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the current method
   * set by {@link #enterMethod(MethodDeclaration)}
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the method object was not found and therefore the
   *     metric could not be added
   */
  public String putMethodMetric(final String metricName, final String metricValue)
      throws NotFoundException {
    try {
      final ClassDataHandler classDataHandler = fileData.getClassData(classStack.peek());
      final MethodDataHandler methodDataHandler = classDataHandler.getMethod(methodStack.peek());
      return methodDataHandler.addMetric(metricName, metricValue);
    } catch (NullPointerException e) {
      final NotFoundException notFoundException =
          new NotFoundException("Not inside class or method. Object to add metric not found.");
      notFoundException.addSuppressed(e);
      throw notFoundException;
    }
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the given method. If
   * the method's name can't be resolved, null gets returned
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @param method the method to add the metric to
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the method object was not found and therefore the
   *     metric could not be added
   */
  public String putMethodMetric(
      final String metricName, final String metricValue, final MethodDeclaration method)
      throws NotFoundException {
    if (method.getParentNode().isPresent()) {
      final Node parent = method.getParentNode().get();
      if (parent instanceof ClassOrInterfaceDeclaration) {
        final String fqn =
            ((ClassOrInterfaceDeclaration) parent).getFullyQualifiedName().orElse(UNKNOWN);
        return fileData
            .getClassData(fqn)
            .getMethod(
                fqn
                    + "."
                    + method.getNameAsString()
                    + "#"
                    + Verification.parameterHash(method.getParameters()))
            .addMetric(metricName, metricValue);
      }
    }
    throw new NotFoundException(OBJECT_NOT_FOUND);
  }

  /**
   * Sets the metric with metricName to metricValue. The metric is attached to the method defined by
   * classFqn and methodFqn. Keep in mind to append the {@link Verification#parameterHash(NodeList)}
   * to the methodFqn to differentiate overloaded methods.
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @param classFqn the fqn of the method's class
   * @param methodFqn the fqn of the method
   * @return returns the old value of the metric if the metric exists, otherwise null
   * @throws NotFoundException gets thrown if the method object was not found and therefore the
   *     metric could not be added
   */
  public String putMethodMetric(
      final String metricName,
      final String metricValue,
      final String classFqn,
      final String methodFqn)
      throws NotFoundException {
    try {
      return fileData
          .getClassData(classFqn)
          .getMethod(methodFqn)
          .addMetric(metricName, metricValue);
    } catch (NullPointerException e) {
      final NotFoundException notFoundException = new NotFoundException(OBJECT_NOT_FOUND);
      notFoundException.addSuppressed(e);
      throw notFoundException;
    }
  }

  /**
   * Used to handle the tracking of the current class. Call with the current class to use the {@link
   * MetricAppender#getClass()} and {@link MetricAppender#getCurrentClassName()} anywhere later.
   * Enables the usability of {@link MetricAppender#putMethodMetric(String, String)} and {@link
   * MetricAppender#putClassMetric(String, String)}.
   *
   * @param clazz the class to enter
   */
  public void enterClass(final ClassOrInterfaceDeclaration clazz) {
    this.classStack.push(clazz.getFullyQualifiedName().orElse(UNKNOWN));
  }

  public void enterClass(final EnumDeclaration clazz) {
    this.classStack.push(clazz.getFullyQualifiedName().orElse(UNKNOWN));
  }

  /** Leaves a class. */
  public void leaveClass() {
    this.classStack.pop();
  }

  /**
   * Used to signal the entering of an anonymous class.
   *
   * @param anonymousClassName the name used to identify the anonymous class.
   * @param parentFqn the parent fqn, e.g the fqn of the method the anonymous class is in
   */
  public void enterAnonymousClass(final String anonymousClassName, final String parentFqn) {
    String fqn = parentFqn + "." + anonymousClassName;
    final Integer val = anonymousCounter.put(fqn, anonymousCounter.getOrDefault(fqn, 0) + 1);
    fqn = parentFqn + "." + anonymousClassName + (val == null ? "" : "#" + val);

    this.classStack.push(fqn);
  }

  /** Leaves an anonymous class. */
  public void leaveAnonymousClass() {
    this.classStack.pop();
  }

  public String getCurrentClassName() {
    return classStack.peek();
  }

  /**
   * Used to handle the tracking of the current method. Call with the current method to use the
   * {@link MetricAppender#getCurrentMethodName()} anywhere later. Enables the usability of {@link
   * MetricAppender#putMethodMetric(String, String)}.
   *
   * @param method the method to enter
   */
  public void enterMethod(final MethodDeclaration method) {
    methodStack.push(
        getCurrentClassName()
            + "."
            + method.getNameAsString()
            + "#"
            + Verification.parameterHash(method.getParameters()));
  }

  /**
   * Used to handle the tracking of the current method. Call with the current constructor to use the
   * {@link MetricAppender#getCurrentMethodName()} anywhere later. Enables the usability of {@link
   * MetricAppender#putMethodMetric(String, String)}.
   *
   * @param constructor the constructor to enter
   */
  public void enterMethod(final ConstructorDeclaration constructor) {
    methodStack.push(
        getCurrentClassName()
            + "."
            + constructor.getNameAsString()
            + "#"
            + Verification.parameterHash(constructor.getParameters()));
  }

  /**
   * Used to handle the tracking of the current method. Call with the methodFqn to use the {@link
   * MetricAppender#getCurrentMethodName()} anywhere later. Enables the usability of {@link
   * MetricAppender#putMethodMetric(String, String)}.
   *
   * @param methodFqn the methodFqn, the user needs to make sure the fqn is correct
   */
  public void enterMethod(final String methodFqn) {
    methodStack.push(methodFqn);
  }

  /** Leaves a method. */
  public void leaveMethod() {
    methodStack.pop();
  }

  public String getCurrentMethodName() {
    return methodStack.peek();
  }

  /**
   * Gets the wrapped {@link JavaFileDataHandler}. Keep in mind that some functionality of the
   * fileDataHandler is only avaible during the initial collection of data, use with caution.
   *
   * @return the wrapped FileDataHanlder
   */
  public JavaFileDataHandler getFileData() {
    return fileData;
  }
}
