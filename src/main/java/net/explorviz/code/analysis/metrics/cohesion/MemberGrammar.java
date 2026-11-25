package net.explorviz.code.analysis.metrics.cohesion;

import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

/** Tells which rules of a grammar declare and use members. */
public interface MemberGrammar {

  /** Returns the members of the type that the rule declares, or {@code null} if it is no type. */
  TypeMembers membersOf(ParserRuleContext context);

  /** Returns the uses of names that the rule itself stands for. */
  List<Access> accessesOf(ParserRuleContext context);

  /**
   * Whether the own object is a different one inside of the rule. Not required for rules that
   * {@link #membersOf} knows.
   */
  default boolean rebindsSelf(final ParserRuleContext context) {
    return false;
  }

  /** Whether names that are declared inside of the rule are unknown outside of it. */
  default boolean opensScope(final ParserRuleContext context) {
    return false;
  }

  /** Returns the variables that are known inside of a rule that opens a scope. */
  default List<String> scopeVariables(final ParserRuleContext context) {
    return List.of();
  }

  /** Returns the variables that are known in the scope after the rule. */
  default List<String> declaredVariables(final ParserRuleContext context) {
    return List.of();
  }

  /** Whether the rule and everything below never uses members, e.g. annotations. */
  default boolean isIrrelevant(final ParserRuleContext context) {
    return false;
  }
}
