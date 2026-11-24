package net.explorviz.code.analysis.metrics.java;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import net.explorviz.code.analysis.antlr.generated.java.JavaParserBaseListener;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Measures every function of a file in a single pass. Functions are methods, constructors and
 * initializer blocks, including their modifiers and annotations.
 *
 * @param <T> the type of the measured value
 */
/* package */ abstract class FunctionListener<T> extends JavaParserBaseListener {

  private final Map<ParserRuleContext, T> values = new IdentityHashMap<>();

  /** Called before anything that belongs to the function. */
  /* package */ abstract void enterFunction();

  /** Called after everything that belongs to the function, returns the value of the function. */
  /* package */ abstract T exitFunction();

  /** Returns the measured values by the declaration of their function. */
  /* package */ Map<ParserRuleContext, T> values() {
    return Collections.unmodifiableMap(values);
  }

  @Override
  public void enterEveryRule(final ParserRuleContext ctx) {
    if (findDeclaration(ctx) != null) {
      enterFunction();
    }
  }

  @Override
  public void exitEveryRule(final ParserRuleContext ctx) {
    final ParserRuleContext declaration = findDeclaration(ctx);
    if (declaration != null) {
      values.put(declaration, exitFunction());
    }
  }

  /**
   * Returns the function that is declared by the given rule. The rule also contains the modifiers.
   */
  private static ParserRuleContext findDeclaration(final ParserRuleContext ctx) {
    return switch (ctx) {
      case JavaParser.CompactConstructorDeclarationContext constructor -> constructor;
      case JavaParser.ClassBodyDeclarationContext member when member.block() != null -> member;
      case JavaParser.ClassBodyDeclarationContext member -> find(member.memberDeclaration());
      case JavaParser.InterfaceBodyDeclarationContext member ->
          find(member.interfaceMemberDeclaration());
      default -> null;
    };
  }

  private static ParserRuleContext find(final JavaParser.MemberDeclarationContext member) {
    if (member == null) {
      return null;
    }
    if (member.genericMethodDeclaration() != null) {
      return member.genericMethodDeclaration().methodDeclaration();
    }
    if (member.genericConstructorDeclaration() != null) {
      return member.genericConstructorDeclaration().constructorDeclaration();
    }
    return member.methodDeclaration() == null
        ? member.constructorDeclaration()
        : member.methodDeclaration();
  }

  private static ParserRuleContext find(final JavaParser.InterfaceMemberDeclarationContext member) {
    if (member == null) {
      return null;
    }
    if (member.genericInterfaceMethodDeclaration() != null) {
      return member.genericInterfaceMethodDeclaration().interfaceCommonBodyDeclaration();
    }
    return member.interfaceMethodDeclaration() == null
        ? null
        : member.interfaceMethodDeclaration().interfaceCommonBodyDeclaration();
  }
}
