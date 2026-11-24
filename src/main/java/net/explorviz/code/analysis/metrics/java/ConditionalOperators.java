package net.explorviz.code.analysis.metrics.java;

import java.util.ArrayDeque;
import java.util.Deque;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import org.antlr.v4.runtime.tree.ParseTree;

/** Operators that make an expression branch, wherever they are nested. */
/* package */ final class ConditionalOperators {

  private ConditionalOperators() {}

  /** Counts one path for each {@code &&} and {@code ||} and two for each ternary operator. */
  /* package */ static int countPaths(final ParseTree root) {
    int paths = 0;
    final Deque<ParseTree> pending = new ArrayDeque<>();
    if (root != null) {
      pending.push(root);
    }
    while (!pending.isEmpty()) {
      final ParseTree tree = pending.pop();
      paths += ownPaths(tree);
      for (int i = 0; i < tree.getChildCount(); i++) {
        pending.push(tree.getChild(i));
      }
    }
    return paths;
  }

  private static int ownPaths(final ParseTree tree) {
    if (tree instanceof JavaParser.TernaryExpressionContext) {
      return 2;
    }
    final boolean isShortCircuit =
        tree instanceof JavaParser.BinaryOperatorExpressionContext binary
            && (binary.AND() != null || binary.OR() != null);
    return isShortCircuit ? 1 : 0;
  }
}
