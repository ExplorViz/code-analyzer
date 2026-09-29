package net.explorviz.code.analysis.types;

import java.util.Objects;

/** Vertex to be used in the Graph. Holds a flag if it is a field vertex and holds a group label. */
public class Vertex {

  private final String label;
  private boolean isField;
  private int group;

  public Vertex(final String label, final int group) {
    this.label = label;
    this.group = group;
  }

  @Override
  public String toString() {
    return label + (isField ? "(field)" : "") + "#" + group;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Vertex vertex = (Vertex) o;
    return label.equals(vertex.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label);
  }

  public String getLabel() {
    return label;
  }

  public boolean isField() {
    return isField;
  }

  public void setField(final boolean isField) {
    this.isField = isField;
  }

  public int getGroup() {
    return group;
  }

  public void setGroup(final int group) {
    this.group = group;
  }
}
