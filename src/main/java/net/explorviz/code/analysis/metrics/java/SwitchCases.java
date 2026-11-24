package net.explorviz.code.analysis.metrics.java;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Groups the labels of a switch that share their code.
 *
 * <p>The grammar has two representations: {@code switchLabel} for colon cases with one constant
 * each, and {@code switchLabeledRule} for everything else. The latter does not group colon cases.
 */
/* package */ final class SwitchCases {

  private SwitchCases() {}

  /* package */ static int countCases(final JavaParser.SwitchBlockStatementGroupContext group) {
    return (int) group.switchLabel().stream().filter(label -> label.CASE() != null).count();
  }

  /** Counts the constants and patterns of an arrow case or the case labels of a colon group. */
  /* package */ static int countCases(final JavaParser.SwitchLabeledRuleContext first) {
    if (first.ARROW() != null) {
      return countConstants(first);
    }
    final List<JavaParser.SwitchLabeledRuleContext> rules = rulesOfSwitch(first);
    int count = 0;
    for (int i = rules.indexOf(first); i < rules.size(); i++) {
      if (rules.get(i).CASE() != null) {
        count++;
      }
      if (isLastOfGroup(rules.get(i))) {
        break;
      }
    }
    return count;
  }

  /* package */ static boolean isFirstOfGroup(final JavaParser.SwitchLabeledRuleContext rule) {
    final List<JavaParser.SwitchLabeledRuleContext> rules = rulesOfSwitch(rule);
    final int index = rules.indexOf(rule);
    return index <= 0 || isLastOfGroup(rules.get(index - 1));
  }

  /* package */ static boolean isLastOfGroup(final JavaParser.SwitchLabeledRuleContext rule) {
    if (rule.COLON() == null || hasCode(rule.switchRuleOutcome())) {
      return true;
    }
    final List<JavaParser.SwitchLabeledRuleContext> rules = rulesOfSwitch(rule);
    return rules.indexOf(rule) == rules.size() - 1;
  }

  private static int countConstants(final JavaParser.SwitchLabeledRuleContext rule) {
    if (rule.expressionList() != null) {
      return rule.expressionList().expression().size();
    }
    return rule.NULL_LITERAL() == null ? rule.casePattern().size() : 1;
  }

  private static boolean hasCode(final JavaParser.SwitchRuleOutcomeContext outcome) {
    return outcome != null && (outcome.block() != null || !outcome.blockStatement().isEmpty());
  }

  private static List<JavaParser.SwitchLabeledRuleContext> rulesOfSwitch(
      final JavaParser.SwitchLabeledRuleContext rule) {
    final ParserRuleContext parent = rule.getParent();
    return parent instanceof JavaParser.SwitchExpressionContext switchExpression
        ? switchExpression.switchLabeledRule()
        : List.of(rule);
  }
}
