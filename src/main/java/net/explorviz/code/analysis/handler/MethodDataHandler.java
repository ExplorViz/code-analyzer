package net.explorviz.code.analysis.handler;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import java.util.List;
import java.util.Map;
import net.explorviz.code.proto.FunctionData;
import net.explorviz.code.proto.ParameterData;

/** FunctionData object holds data from analyzed function/method. */
public class MethodDataHandler implements ProtoBufConvertable<FunctionData> {

  private final FunctionData.Builder builder;

  /**
   * Creates a new FunctionData object holding data describing the method.
   *
   * @param name the name of the function
   * @param returnType the return type of the method
   */
  public MethodDataHandler(final String name, final String returnType) {
    this.builder = FunctionData.newBuilder();
    this.builder.setName(name);
    this.builder.setIsConstructor(false);
    this.builder.setReturnType(returnType);
  }

  /**
   * Creates a new Constructor object holding data describing the method.
   *
   * @param name the name of the constructor
   */
  public MethodDataHandler(final String name) {
    this.builder = FunctionData.newBuilder();
    this.builder.setName(name);
    this.builder.setIsConstructor(true);
  }

  public void setLines(final int start, final int end) {
    this.builder.setStartLine(start);
    this.builder.setEndLine(end);
  }

  public void addModifier(final String modifier) {
    this.builder.addModifiers(modifier);
  }

  public void addAnnotation(final String annotation) {
    this.builder.addAnnotations(annotation);
  }

  /**
   * Add a parameter to the method.
   *
   * @param name name of the parameter
   * @param type type of the parameter
   * @param modifiers modifiers of the parameter
   */
  public void addParameter(
      final String name, final String type, final NodeList<Modifier> modifiers) {
    final ParameterData.Builder parameterBuilder = ParameterData.newBuilder();
    parameterBuilder.setName(name);
    parameterBuilder.setType(type);
    for (final Modifier mod : modifiers) {
      parameterBuilder.addModifiers(mod.toString());
    }
    builder.addParameters(parameterBuilder);
  }

  /**
   * Add a parameter to the method.
   *
   * @param name name of the parameter
   * @param type type of the parameter
   * @param modifiers modifiers of the parameter
   */
  public void addParameter(final String name, final String type, final List<String> modifiers) {
    final ParameterData.Builder parameterBuilder = ParameterData.newBuilder();
    parameterBuilder.setName(name);
    parameterBuilder.setType(type);
    parameterBuilder.addAllModifiers(modifiers);
    this.builder.addParameters(parameterBuilder);
  }

  public void addOutgoingMethodCall(final String fqn) {
    this.builder.addOutgoingMethodCalls(fqn);
  }

  /**
   * Adds a metric to the Method.
   *
   * @param metricName the name of the metric
   * @param metricValue the value of the metric
   * @return the old metric value if it existed, null otherwise
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
  public FunctionData getProtoBufObject() {
    return this.builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder metricDataString = new StringBuilder(INITIAL_STRING_CAPACITY);
    for (final Map.Entry<String, Double> entry : this.builder.getMetricsMap().entrySet()) {
      metricDataString.append(entry.getKey()).append(": ");
      metricDataString.append(entry.getValue()).append('\n');
    }
    return "  type: "
        + this.builder.getReturnType()
        + "\n"
        + "  modifiers: "
        + this.builder.getModifiersList()
        + "\n"
        + "  parameters: "
        + this.builder.getParametersList()
        + "\n"
        + "  outgoing calls: "
        + this.builder.getOutgoingMethodCallsList()
        + "\n"
        + metricDataString;
  }
}
