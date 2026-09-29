package net.explorviz.code.analysis.handler;

import java.util.List;
import net.explorviz.code.proto.ParameterData;

/** A handler to easily access and maintain the ParameterData object. */
public class ParameterDataHandler implements ProtoBufConvertable<ParameterData> {

  private final ParameterData.Builder builder;

  public ParameterDataHandler() {
    builder = ParameterData.newBuilder();
  }

  public void setName(final String name) {
    builder.setName(name);
  }

  public void setType(final String type) {
    builder.setType(type);
  }

  public void addModifier(final String modifier) {
    builder.addModifiers(modifier);
  }

  public void addModifiers(final List<String> modifiers) {
    builder.addAllModifiers(modifiers);
  }

  @Override
  public ParameterData getProtoBufObject() {
    return builder.build();
  }
}
