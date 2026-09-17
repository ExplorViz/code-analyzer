package net.explorviz.code.analysis.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;

/**
 * ClassData object holds data from analyzed classes.
 */
public class ClassDataHandler implements ProtoBufConvertable<ClassData> {

  private final ClassData.Builder builder;

  private final Map<String, MethodDataHandler> methodDataMap;
  private final Map<String, ClassDataHandler> innerClassDataMap;

  /**
   * Creates a blank ClassData object.
   */
  public ClassDataHandler() {
    this.builder = ClassData.newBuilder();
    this.methodDataMap = new HashMap<>();
    this.innerClassDataMap = new HashMap<>();
  }

  public void setName(final String name) {
    this.builder.setName(name);
  }

  /**
   * Adds a methodData object.
   *
   * @param name       the method's simple name
   * @param fqn        the method's fully qualified name
   * @param returnType the return type of the method
   * @return the created methodData object
   */
  public MethodDataHandler addMethod(final String name, final String fqn, final String returnType) {
    this.methodDataMap.put(fqn, new MethodDataHandler(name, returnType));
    return methodDataMap.get(fqn);
  }

  public MethodDataHandler getMethod(final String methodFqn) {
    return methodDataMap.get(methodFqn);
  }

  public void setSuperClass(final String superClass) {
    if (superClass != null && !superClass.isEmpty()) {
      this.builder.addSuperclasses(superClass);
    }
  }

  public void clearSuperClass() {
    this.builder.clearSuperclasses();
  }

  public void clearImplementedInterfaces() {
    this.builder.clearImplementedInterfaces();
  }

  public void clearFields() {
    this.builder.clearFields();
  }


  /**
   * Adds a constructor object.
   *
   * @param name the constructor's simple name
   * @param fqn  the constructor's fully qualified name
   * @return the created methodData object
   */
  public MethodDataHandler addConstructor(final String name, final String fqn) {
    this.methodDataMap.put(fqn, new MethodDataHandler(name));
    return methodDataMap.get(fqn);
  }

  public void addField(final String fieldName, final String fieldType,
      final List<String> modifiers) {
    this.builder.addFields(
        FieldData.newBuilder().setName(fieldName).setType(fieldType).addAllModifiers(modifiers));
  }

  public void addModifier(final String modifier) {
    this.builder.addModifiers(modifier);
  }

  public void addAnnotation(final String annotation) {
    this.builder.addAnnotations(annotation);
  }

  public void addInnerClass(final String fqn, final ClassDataHandler innerClassHandler) {
    this.innerClassDataMap.put(fqn, innerClassHandler);
  }

  public void addEnumConstant(final String name) {
    this.builder.addEnumValues(name);
  }

  public int getMethodCount() {
    return this.methodDataMap.size();
  }

  public void addImplementedInterface(final String implementedInterfaceName) {
    this.builder.addImplementedInterfaces(implementedInterfaceName);
  }

  public void setIsInterface() {
    this.builder.setType(ClassType.INTERFACE);
  }

  public boolean isInterface() {
    return this.builder.getType() == ClassType.INTERFACE;
  }

  public void setIsAbstractClass() {
    this.builder.setType(ClassType.ABSTRACT_CLASS);
  }

  public boolean isAbstractClass() {
    return this.builder.getType() == ClassType.ABSTRACT_CLASS;
  }

  /**
   * Set the current ClassType as class if it wasn't set to anonymous class already.
   */
  public void setIsClass() {
    if (this.builder.getType() != ClassType.ANONYMOUS_CLASS) {
      this.builder.setType(ClassType.CLASS);
    }
  }

  public boolean isClass() {
    return this.builder.getType() == ClassType.CLASS;
  }

  public void setIsEnum() {
    this.builder.setType(ClassType.ENUM);
  }

  public boolean isEnum() {
    return this.builder.getType() == ClassType.ENUM;
  }

  public void setIsAnonymousClass() {
    this.builder.setType(ClassType.ANONYMOUS_CLASS);
  }

  public boolean isAnonymousClass() {
    return this.builder.getType() == ClassType.ANONYMOUS_CLASS;
  }

  public void setIsStruct() {
    this.builder.setType(ClassType.STRUCT);
  }

  public boolean isStruct() {
    return this.builder.getType() == ClassType.STRUCT;
  }

  /**
   * Adds a new metric entry to the ClassData, returns the old value of the metric if it existed, null otherwise.
   *
   * @param metricName  the name/identifier of the metric
   * @param metricValue the value of the metric
   * @return the old value of the metric if it existed, null otherwise.
   */
  public String addMetric(final String metricName, final String metricValue) {
    final String oldMetricValue = getMetricValue(metricName);
    try {
      builder.putMetrics(metricName, Double.parseDouble(metricValue));
    } catch (NumberFormatException e) {
      builder.putMetrics(metricName, 0.0);
    }
    return oldMetricValue;
  }

  /**
   * Returns the value of the metric, if no entry with the name exists, returns null.
   *
   * @param metricName the name/identifier of the metric
   * @return the value of the metric or null if the metric does not exist
   */
  public String getMetricValue(final String metricName) {
    return builder.getMetricsMap().containsKey(metricName)
        ? String.valueOf(builder.getMetricsMap().get(metricName))
        : null;
  }

  /**
   * Returns the metrics map.
   *
   * @return the map containing the File metrics
   */
  public Map<String, Double> getMetrics() {
    return builder.getMetricsMap();
  }

  @Override
  public ClassData getProtoBufObject() {
    this.builder.clearFunctions();
    for (final Map.Entry<String, MethodDataHandler> entry : this.methodDataMap.entrySet()) {
      this.builder.addFunctions(entry.getValue().getProtoBufObject());
    }
    this.builder.clearInnerClasses();
    for (final Map.Entry<String, ClassDataHandler> entry : this.innerClassDataMap.entrySet()) {
      this.builder.addInnerClasses(entry.getValue().getProtoBufObject());
    }
    return this.builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder methodDataString = new StringBuilder(INITIAL_STRING_CAPACITY);
    for (final Map.Entry<String, MethodDataHandler> entry : this.methodDataMap.entrySet()) {
      methodDataString.append(entry.getKey()).append(": \n");
      methodDataString.append(entry.getValue().toString());
      methodDataString.append('\n');
    }
    final StringBuilder metricDataString = new StringBuilder(INITIAL_STRING_CAPACITY);
    for (final Map.Entry<String, Double> entry : this.builder.getMetricsMap().entrySet()) {
      metricDataString.append(entry.getKey()).append(": ");
      metricDataString.append(entry.getValue()).append('\n');
    }
    return "{ \n"
        + "type: " + this.builder.getType().toString() + "\n"
        + "modifier: " + this.builder.getModifiersList() + "\n"
        + "superClasses: " + this.builder.getSuperclassesList() + "\n"
        + "interfaces: " + this.builder.getImplementedInterfacesList() + "\n"
        + "fields: " + this.builder.getFieldsList() + "\n"
        + "innerClasses: " + this.innerClassDataMap.keySet() + "\n"
        + "functions: \n" + methodDataString + "\n"
        + metricDataString + "\n}";
  }

  //statische Abhängigkeiten
  /**
   * Adds an outgoing method call to all methods matching the given simple name
   * (overloads all receive the same call entry - acceptable simplification).
   *
   * @param methodSimpleName the simple (non-qualified) name of the calling method
   * @param targetFqn        the target class name of the call
   */
  public void addOutgoingMethodCallToMethod(final String methodSimpleName, final String targetFqn) {
    for (final Map.Entry<String, MethodDataHandler> entry : this.methodDataMap.entrySet()) {
      final String fqn = entry.getKey();
      final int hashIdx = fqn.indexOf('#');
      final String withoutHash = hashIdx != -1 ? fqn.substring(0, hashIdx) : fqn;
      final String namePart = withoutHash.substring(withoutHash.lastIndexOf('.') + 1);
      if (namePart.equals(methodSimpleName)) {
        entry.getValue().addOutgoingMethodCall(targetFqn);
      }
    }
  }

  public void addFieldType(final String fieldType) {
    this.builder.addFields(FieldData.newBuilder().setType(fieldType));
  }

}
