package net.explorviz.code.analysis.metrics.cohesion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fields and methods that a type declares itself. Constants are not part of the type's state, but
 * still hide outer variables.
 */
public final class TypeMembers {

  private final String typeName;
  private final Set<String> aliases;
  private final Set<String> names = new HashSet<>();
  private final Set<String> stateFields = new HashSet<>();
  private final List<Method> methods = new ArrayList<>();

  /**
   * Creates the members of a type.
   *
   * @param typeName the simple name of the type, {@code null} if it is anonymous
   * @param aliases the names that refer to the type only inside of it, e.g. {@code Self}
   */
  public TypeMembers(final String typeName, final String... aliases) {
    this.typeName = typeName;
    this.aliases = Set.of(aliases);
  }

  /** Adds a field that is part of the state. */
  public void addField(final String name) {
    names.add(name);
    stateFields.add(name);
  }

  /** Adds a field that is either a constant or part of the state. */
  public void addField(final String name, final boolean isConstant) {
    names.add(name);
    if (!isConstant) {
      stateFields.add(name);
    }
  }

  public void addMethod(final Method method) {
    methods.add(method);
  }

  /** Whether a method or property with code of this name is declared. */
  public boolean declaresMethod(final String name) {
    return methods.stream().anyMatch(method -> method.name().equals(name));
  }

  /** The names of all fields including constants. */
  public Set<String> fieldNames() {
    return Collections.unmodifiableSet(names);
  }

  /* package */ String typeName() {
    return typeName;
  }

  /* package */ boolean isAlias(final String name) {
    return aliases.contains(name);
  }

  /* package */ List<Method> methods() {
    return methods;
  }

  /* package */ boolean isStateField(final String name) {
    return stateFields.contains(name);
  }
}
