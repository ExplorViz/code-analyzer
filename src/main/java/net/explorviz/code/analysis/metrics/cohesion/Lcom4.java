package net.explorviz.code.analysis.metrics.cohesion;

/**
 * Lack of cohesion of methods (Hitz and Montazeri, 1995): the number of connected components of a
 * type, where methods are connected if they use a common field or if one calls the other.
 *
 * <p>A value of one is a cohesive type, higher values hint at several responsibilities and zero
 * means that there are no methods with code.
 */
public final class Lcom4 {

  private Lcom4() {}

  /** Calculates LCOM4 of a type. */
  public static int of(final TypeMembers members, final MemberGrammar grammar) {
    final DisjointSets components = new DisjointSets();
    for (final Method method : members.methods()) {
      final UsedMembers used = UsedMembers.of(method, members, grammar);
      used.fields().forEach(field -> components.union(method, field));
      used.methods().forEach(other -> components.union(method, other));
    }
    return (int)
        members.methods().stream().filter(Method::hasCode).map(components::find).distinct().count();
  }
}
