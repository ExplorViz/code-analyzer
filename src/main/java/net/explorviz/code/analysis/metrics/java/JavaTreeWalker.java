package net.explorviz.code.analysis.metrics.java;

import java.util.function.Predicate;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserListener;
import net.explorviz.code.analysis.metrics.TreeWalker;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Walks parse trees of Java with listeners while skipping selected subtrees. */
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
    TreeWalker.walk(
        root,
        new TreeWalker.Visitor() {
          @Override
          public boolean enter(final ParserRuleContext context) {
            if (skip.test(context)) {
              return false;
            }
            listener.enterEveryRule(context);
            context.enterRule(listener);
            return true;
          }

          @Override
          public void exit(final ParserRuleContext context) {
            context.exitRule(listener);
            listener.exitEveryRule(context);
          }
        });
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
}
