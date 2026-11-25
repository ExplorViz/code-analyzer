package net.explorviz.code.analysis.metrics.cohesion;

import java.util.HashSet;
import java.util.Set;
import net.explorviz.code.analysis.metrics.TreeWalker;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Fields and methods of the own type that a method uses, including uses inside of lambdas, local
 * and anonymous types.
 *
 * <p>Types are not resolved. Overloads are therefore told apart by the number of arguments only,
 * and members that are accessed through another instance of the type are not recognized.
 */
/* package */ final class UsedMembers implements TreeWalker.Visitor {

  private final Method method;
  private final TypeMembers members;
  private final MemberGrammar grammar;
  private final Scopes scopes = new Scopes();
  private final Set<String> fields = new HashSet<>();
  private final Set<Method> methods = new HashSet<>();

  private UsedMembers(final Method method, final TypeMembers members, final MemberGrammar grammar) {
    this.method = method;
    this.members = members;
    this.grammar = grammar;
  }

  /* package */ static UsedMembers of(
      final Method method, final TypeMembers members, final MemberGrammar grammar) {
    final UsedMembers used = new UsedMembers(method, members, grammar);
    method.code().forEach(code -> TreeWalker.walk(code, used));
    return used;
  }

  /* package */ Set<String> fields() {
    return fields;
  }

  /* package */ Set<Method> methods() {
    return methods;
  }

  @Override
  public boolean enter(final ParserRuleContext context) {
    if (grammar.isIrrelevant(context)) {
      return false;
    }
    grammar.accessesOf(context).forEach(this::use);
    final TypeMembers nested = grammar.membersOf(context);
    if (nested != null) {
      scopes.enterType(nested);
    } else if (grammar.rebindsSelf(context)) {
      scopes.enterOther();
    } else if (grammar.opensScope(context)) {
      scopes.enter();
      scopes.declare(grammar.scopeVariables(context));
    }
    return true;
  }

  @Override
  public void exit(final ParserRuleContext context) {
    if (grammar.membersOf(context) != null
        || grammar.rebindsSelf(context)
        || grammar.opensScope(context)) {
      scopes.leave();
    }
    scopes.declare(grammar.declaredVariables(context));
  }

  private void use(final Access access) {
    if (!isOwn(access)) {
      return;
    }
    final String name = access.name();
    for (final Method candidate : members.methods()) {
      if (candidate.name().equals(name) && candidate.accepts(access.arguments())) {
        methods.add(candidate);
      }
    }
    if (members.isStateField(name)) {
      fields.add(name);
    }
  }

  private boolean isOwn(final Access access) {
    final String qualifier = access.qualifier();
    if (qualifier == null) {
      return !scopes.hidesVariable(access.name()) && !scopes.hidesMethod(access.name());
    }
    if (qualifier.equals(method.self())) {
      return !scopes.isOther() && !scopes.hidesVariable(qualifier);
    }
    if (members.isAlias(qualifier)) {
      return !scopes.isOther();
    }
    return qualifier.equals(members.typeName()) && !scopes.hidesVariable(qualifier);
  }
}
