package net.explorviz.code.analysis.metrics.java.lcom4;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Names that hide members of the measured class: local variables and parameters, as well as the
 * members of local and anonymous classes.
 */
/* package */ final class Scopes {

  private final Deque<Scope> scopes = new ArrayDeque<>();

  /* package */ Scopes() {
    enterBlock();
  }

  /* package */ void enterBlock() {
    scopes.push(new Scope(new HashSet<>(), null));
  }

  /* package */ void enterType(final ClassMembers members) {
    scopes.push(new Scope(new HashSet<>(members.fieldNames()), members));
  }

  /* package */ void leave() {
    scopes.pop();
  }

  /* package */ void declareVariable(final String name) {
    scopes.peek().variables().add(name);
  }

  /* package */ boolean hidesVariable(final String name) {
    return scopes.stream().anyMatch(scope -> scope.variables().contains(name));
  }

  /* package */ boolean hidesMethod(final String name) {
    return scopes.stream()
        .anyMatch(scope -> scope.type() != null && scope.type().declaresMethod(name));
  }

  /** Whether {@code this} refers to a local or anonymous class. */
  /* package */ boolean isInNestedType() {
    return scopes.stream().anyMatch(scope -> scope.type() != null);
  }

  /** {@code type} is {@code null} unless the scope is the body of a type. */
  private record Scope(Set<String> variables, ClassMembers type) {}
}
