package net.explorviz.code.analysis.metrics.java.lcom4;

import net.explorviz.code.analysis.metrics.cohesion.DisjointSets;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Lack of cohesion of methods (Hitz and Montazeri, 1995): the number of connected components of a
 * class, where methods are connected if they use a common field or if one calls the other.
 *
 * <p>A value of one is a cohesive class, higher values hint at several responsibilities and zero
 * means that there are no methods with code. Constructors and initializers are ignored because they
 * typically use all fields. Constants are not considered as shared state. Methods without code do
 * not count as a component, but connect the methods that call them.
 */
public final class Lcom4 {

  private Lcom4() {}

  /** Calculates LCOM4 of the given type declaration or anonymous class. */
  public static int of(final ParserRuleContext type) {
    final ClassMembers members = ClassMembers.of(type);
    final DisjointSets components = new DisjointSets();
    for (final Method method : members.methods()) {
      final MemberAccessListener accesses = MemberAccessListener.analyze(method, members);
      accesses.usedFields().forEach(field -> components.union(method, field));
      accesses.usedMethods().forEach(used -> components.union(method, used));
    }
    return (int)
        members.methods().stream().filter(Method::hasCode).map(components::find).distinct().count();
  }
}
