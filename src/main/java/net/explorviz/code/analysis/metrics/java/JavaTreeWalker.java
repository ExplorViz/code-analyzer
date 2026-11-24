package net.explorviz.code.analysis.metrics.java;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Walks parse trees depth-first while skipping selected subtrees. The walk is iterative because
 * long expressions, e.g. string concatenations, result in trees as deep as the expression is long.
 */
public final class JavaTreeWalker {

  private JavaTreeWalker() {}

  /**
   * Notifies the listener about every rule below and including {@code root}.
   *
   * @param listener the listener to notify
   * @param root the tree to walk, may be {@code null}
   * @param skip the subtrees to skip entirely
   */
  public static void walk(
      final JavaParserListener listener,
      final ParserRuleContext root,
      final Predicate<ParseTree> skip) {
    if (root == null || skip.test(root)) {
      return;
    }
    final Deque<Frame> stack = new ArrayDeque<>();
    enter(listener, root, stack);
    while (!stack.isEmpty()) {
      final Frame frame = stack.peek();
      if (frame.nextChild < frame.context.getChildCount()) {
        final ParseTree child = frame.context.getChild(frame.nextChild++);
        if (child instanceof ParserRuleContext childContext && !skip.test(child)) {
          enter(listener, childContext, stack);
        }
      } else {
        frame.context.exitRule(listener);
        listener.exitEveryRule(frame.context);
        stack.pop();
      }
    }
  }

  /** Notifies the listener about every rule below and including {@code root}. */
  public static void walk(final JavaParserListener listener, final ParserRuleContext root) {
    walk(listener, root, tree -> false);
  }

  /**
   * Walks the code that belongs to a function itself. The bodies of local and anonymous classes are
   * skipped because their functions are measured on their own. Lambdas are part of the function.
   */
  public static void walkFunction(final JavaParserListener listener, final ParserRuleContext root) {
    walk(listener, root, JavaTreeWalker::isTypeBody);
  }

  /** Whether the tree is the body of a class, interface, enum or record. */
  public static boolean isTypeBody(final ParseTree tree) {
    return tree instanceof JavaParser.ClassBodyContext
        || tree instanceof JavaParser.InterfaceBodyContext
        || tree instanceof JavaParser.EnumBodyDeclarationsContext
        || tree instanceof JavaParser.EnumConstantsContext
        || tree instanceof JavaParser.RecordBodyContext;
  }

  private static void enter(
      final JavaParserListener listener,
      final ParserRuleContext context,
      final Deque<Frame> stack) {
    listener.enterEveryRule(context);
    context.enterRule(listener);
    stack.push(new Frame(context));
  }

  private static final class Frame {
    private final ParserRuleContext context;
    private int nextChild;

    private Frame(final ParserRuleContext context) {
      this.context = context;
    }
  }
}
