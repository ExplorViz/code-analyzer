package net.explorviz.code.analysis.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.ClassType;
import net.explorviz.code.proto.FieldData;

/** ClassData object holds data from analyzed classes. */
public class ClassDataHandler implements ProtoBufConvertable<ClassData> {

  private final ClassData.Builder builder;

  private final Map<String, MethodDataHandler> methodDataMap;
  private final Map<String, ClassDataHandler> innerClassDataMap;

  /** Creates a blank ClassData object. */
  public ClassDataHandler() {
    this.builder = ClassData.newBuilder();
    // Insertion order keeps the output in source order.
    this.methodDataMap = new LinkedHashMap<>();
    this.innerClassDataMap = new LinkedHashMap<>();
  }

  public void setName(final String name) {
    this.builder.setName(name);
  }

  /**
   * Adds a methodData object. A method whose key is already taken, e.g. an overload that the key
   * does not distinguish, is added as well instead of replacing the existing one.
   *
   * @param name the method's simple name
   * @param fqn the method's fully qualified name, used as key
   * @param returnType the return type of the method
   * @return the created methodData object
   */
  public MethodDataHandler addMethod(final String name, final String fqn, final String returnType) {
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    this.methodDataMap.put(uniqueKey(this.methodDataMap, fqn), handler);
    return handler;
  }

  /**
   * Adds a methodData object, replacing a method with the same key. Use this to merge several
   * declarations of the same method, e.g. a declaration and its out-of-line definition.
   *
   * @param name the method's simple name
   * @param fqn the method's fully qualified name, used as key
   * @param returnType the return type of the method
   * @return the created methodData object
   */
  public MethodDataHandler putMethod(final String name, final String fqn, final String returnType) {
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    this.methodDataMap.put(fqn, handler);
    return handler;
  }

  /** Returns {@code key}, or {@code key#n} with the smallest free {@code n >= 2}. */
  private static String uniqueKey(final Map<String, ?> map, final String key) {
    if (!map.containsKey(key)) {
      return key;
    }
    int suffix = 2;
    while (map.containsKey(key + "#" + suffix)) {
      suffix++;
    }
    return key + "#" + suffix;
  }

  public MethodDataHandler getMethod(final String methodFqn) {
    return methodDataMap.get(methodFqn);
  }

  public void setSuperClass(final String superClass) {
    if (superClass != null && !superClass.isEmpty()) {
      this.builder.addSuperclasses(superClass);
    }
  }

  /**
   * Adds a constructor object. Like {@link #addMethod}, an existing key is not replaced.
   *
   * @param name the constructor's simple name
   * @param fqn the constructor's fully qualified name, used as key
   * @return the created methodData object
   */
  public MethodDataHandler addConstructor(final String name, final String fqn) {
    final MethodDataHandler handler = new MethodDataHandler(name);
    this.methodDataMap.put(uniqueKey(this.methodDataMap, fqn), handler);
    return handler;
  }

  /**
   * Adds a constructor object, replacing a method with the same key, see {@link #putMethod}.
   *
   * @param name the constructor's simple name
   * @param fqn the constructor's fully qualified name, used as key
   * @return the created methodData object
   */
  public MethodDataHandler putConstructor(final String name, final String fqn) {
    final MethodDataHandler handler = new MethodDataHandler(name);
    this.methodDataMap.put(fqn, handler);
    return handler;
  }

  public void addField(
      final String fieldName, final String fieldType, final List<String> modifiers) {
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
    this.innerClassDataMap.put(uniqueKey(this.innerClassDataMap, fqn), innerClassHandler);
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

  /** Set the current ClassType as class if it wasn't set to anonymous class already. */
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
   * Adds a new metric entry to the ClassData, returns the old value of the metric if it existed,
   * null otherwise.
   *
   * @param metricName the name/identifier of the metric
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
        + "type: "
        + this.builder.getType().toString()
        + "\n"
        + "modifier: "
        + this.builder.getModifiersList()
        + "\n"
        + "superClasses: "
        + this.builder.getSuperclassesList()
        + "\n"
        + "interfaces: "
        + this.builder.getImplementedInterfacesList()
        + "\n"
        + "fields: "
        + this.builder.getFieldsList()
        + "\n"
        + "innerClasses: "
        + this.innerClassDataMap.keySet()
        + "\n"
        + "functions: \n"
        + methodDataString
        + "\n"
        + metricDataString
        + "\n}";
  }
}
