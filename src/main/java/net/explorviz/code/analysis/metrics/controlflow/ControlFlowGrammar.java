package net.explorviz.code.analysis.metrics.controlflow;

import org.antlr.v4.runtime.ParserRuleContext;

/** Tells which rules of a grammar affect the control flow. */
public interface ControlFlowGrammar {

  /** Returns the construct that the rule stands for, {@link Construct#NONE} if there is none. */
  Construct constructOf(ParserRuleContext context);

  /**
   * Returns the number of short-circuit operators that are a direct part of the rule, i.e. logical
   * and, logical or and null-coalescing.
   */
  int countShortCircuits(ParserRuleContext context);
}
