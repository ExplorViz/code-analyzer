package net.explorviz.code.analysis.metrics.cohesion;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Names that hide members of the measured type: local variables and parameters, as well as the
 * members of local and anonymous types.
 */
/* package */ final class Scopes {

  private final Deque<Scope> scopes = new ArrayDeque<>();

  /* package */ Scopes() {
    enter();
  }

  /* package */ void enter() {
    scopes.push(new Scope(new HashSet<>(), null, false));
  }

  /** Enters the code of another object. */
  /* package */ void enterOther() {
    scopes.push(new Scope(new HashSet<>(), null, true));
  }

  /* package */ void enterType(final TypeMembers members) {
    scopes.push(new Scope(new HashSet<>(members.fieldNames()), members, true));
  }

  /* package */ void leave() {
    scopes.pop();
  }

  /* package */ void declare(final List<String> variables) {
    scopes.peek().variables().addAll(variables);
  }

  /* package */ boolean hidesVariable(final String name) {
    return scopes.stream().anyMatch(scope -> scope.variables().contains(name));
  }

  /* package */ boolean hidesMethod(final String name) {
    return scopes.stream()
        .anyMatch(scope -> scope.type() != null && scope.type().declaresMethod(name));
  }

  /** Whether the own object is not the one of the measured type. */
  /* package */ boolean isOther() {
    return scopes.stream().anyMatch(Scope::isOther);
  }

  /** {@code type} is {@code null} unless the scope is the body of a type. */
  private record Scope(Set<String> variables, TypeMembers type, boolean isOther) {}
}
