package net.explorviz.code.analysis.metrics;

import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Walks parse trees depth-first. The walk is iterative because long expressions, e.g. string
 * concatenations, result in trees as deep as the expression is long.
 */
public final class TreeWalker {

  private TreeWalker() {}

  /** Walks all rules below and including {@code root}, which may be {@code null}. */
  public static void walk(final ParserRuleContext root, final Visitor visitor) {
    if (root == null || !visitor.enter(root)) {
      return;
    }
    final Deque<Frame> stack = new ArrayDeque<>();
    stack.push(new Frame(root));
    while (!stack.isEmpty()) {
      final Frame frame = stack.peek();
      if (frame.nextChild < frame.context.getChildCount()) {
        final ParseTree child = frame.context.getChild(frame.nextChild++);
        if (child instanceof ParserRuleContext childContext && visitor.enter(childContext)) {
          stack.push(new Frame(childContext));
        }
      } else {
        visitor.exit(frame.context);
        stack.pop();
      }
    }
  }

  /** Is notified about the rules of a tree. */
  public interface Visitor {

    /** Returns whether to walk the rule. If not, it is not exited either. */
    boolean enter(ParserRuleContext context);

    /** Called after everything below the rule. */
    default void exit(final ParserRuleContext context) {
      // Not every visitor cares.
    }
  }

  private static final class Frame {
    private final ParserRuleContext context;
    private int nextChild;

    private Frame(final ParserRuleContext context) {
      this.context = context;
    }
  }
}
