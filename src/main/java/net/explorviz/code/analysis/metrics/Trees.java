package net.explorviz.code.analysis.metrics;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Queries on parse trees. */
public final class Trees {

  private Trees() {}

  /** Returns the number of tokens of the given type that are a direct child of the rule. */
  public static int count(final ParserRuleContext context, final int tokenType) {
    return context.getTokens(tokenType).size();
  }

  /** Returns the text of the first tokens of the rule, at most {@code limit}. */
  public static List<String> firstTokens(final ParserRuleContext context, final int limit) {
    final List<String> tokens = new ArrayList<>();
    addTokens(context, limit, tokens);
    return tokens;
  }

  /** Returns the text of all tokens of the rule. */
  public static List<String> tokens(final ParserRuleContext context) {
    return firstTokens(context, Integer.MAX_VALUE);
  }

  /** Returns the text or {@code null} if there is no context. */
  public static String text(final ParserRuleContext context) {
    return context == null ? null : context.getText();
  }

  /** Returns the closest rule around the given one that is an instance of one of the types. */
  public static ParserRuleContext enclosing(
      final ParserRuleContext context, final Class<?>... types) {
    ParserRuleContext parent = context.getParent();
    while (parent != null && !isInstance(parent, types)) {
      parent = parent.getParent();
    }
    return parent;
  }

  /** Returns the outermost rule. */
  public static ParserRuleContext root(final ParserRuleContext context) {
    ParserRuleContext root = context;
    while (root.getParent() != null) {
      root = root.getParent();
    }
    return root;
  }

  /**
   * Returns the rules of the given type below and including {@code root}, in source order.
   *
   * @param root the rule to search, may be {@code null}
   * @param type the type to search for
   * @param skipped the types of the rules below {@code root} that are not searched
   */
  public static <T extends ParserRuleContext> List<T> find(
      final ParserRuleContext root, final Class<T> type, final Class<?>... skipped) {
    final List<T> found = new ArrayList<>();
    TreeWalker.walk(
        root,
        context -> {
          if (context != root && isInstance(context, skipped)) {
            return false;
          }
          if (type.isInstance(context)) {
            found.add(type.cast(context));
          }
          return true;
        });
    return found;
  }

  private static boolean isInstance(final ParserRuleContext context, final Class<?>... types) {
    for (final Class<?> type : types) {
      if (type.isInstance(context)) {
        return true;
      }
    }
    return false;
  }

  private static void addTokens(final ParseTree tree, final int limit, final List<String> tokens) {
    final List<ParseTree> pending = new ArrayList<>();
    pending.add(tree);
    while (!pending.isEmpty() && tokens.size() < limit) {
      final ParseTree next = pending.removeLast();
      if (next instanceof TerminalNode) {
        tokens.add(next.getText());
      }
      for (int i = next.getChildCount() - 1; i >= 0; i--) {
        pending.add(next.getChild(i));
      }
    }
  }
}
