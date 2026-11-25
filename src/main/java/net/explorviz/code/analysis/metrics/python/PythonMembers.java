package net.explorviz.code.analysis.metrics.python;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.explorviz.code.analysis.antlr.generated.python.PythonParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Members of the classes of Python. Since fields need no declaration, all {@code self.x} that are
 * neither called nor methods are fields. Upper case fields of the class are constants.
 */
public final class PythonMembers implements MemberGrammar {

  private static final Set<String> CONSTRUCTORS =
      Set.of("__init__", "__new__", "__post_init__", "__del__");

  private static final Pattern FORMATTED = Pattern.compile("[bBrRuU]*[fF].*", Pattern.DOTALL);

  private static final Pattern ATTRIBUTE =
      Pattern.compile("(?<![\\w.])([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)(\\()?");

  private static final Pattern CONSTANT = Pattern.compile("[A-Z][A-Z0-9_]*");

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    if (!(context instanceof PythonParser.ClassdefContext type)
        || type.name() == null
        || type.suite() == null) {
      return null;
    }
    final TypeMembers members = new TypeMembers(type.name().getText());
    for (final PythonParser.Expr_stmtContext statement :
        Trees.find(
            type.suite(),
            PythonParser.Expr_stmtContext.class,
            PythonParser.FuncdefContext.class,
            PythonParser.ClassdefContext.class)) {
      addClassField(members, statement);
    }
    final List<PythonParser.FuncdefContext> functions =
        Trees.find(
                type.suite(), PythonParser.FuncdefContext.class, PythonParser.ClassdefContext.class)
            .stream()
            .filter(function -> isMethod(function, type))
            .toList();
    functions.forEach(function -> addMethod(members, function));
    functions.forEach(function -> addInstanceFields(members, function));
    return members;
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case PythonParser.ExprContext ctx when ctx.atom() != null -> attributeOf(ctx);
      case PythonParser.AtomContext ctx when !ctx.STRING().isEmpty() -> attributesOf(ctx.STRING());
      default -> List.of();
    };
  }

  private static boolean isMethod(
      final PythonParser.FuncdefContext function, final PythonParser.ClassdefContext type) {
    return Trees.enclosing(
            function, PythonParser.FuncdefContext.class, PythonParser.ClassdefContext.class)
        == type;
  }

  private static void addClassField(
      final TypeMembers members, final PythonParser.Expr_stmtContext statement) {
    if (statement.assign_part() == null || statement.testlist_star_expr() == null) {
      return;
    }
    final List<String> target = Trees.firstTokens(statement.testlist_star_expr(), 2);
    if (target.size() == 1) {
      members.addField(target.getFirst(), CONSTANT.matcher(target.getFirst()).matches());
    }
  }

  private static void addMethod(
      final TypeMembers members, final PythonParser.FuncdefContext function) {
    if (function.name() == null || CONSTRUCTORS.contains(function.name().getText())) {
      return;
    }
    members.addMethod(
        Method.of(
            function.name().getText(),
            selfOf(function),
            Method.Arguments.ANY,
            hasCode(function.suite()),
            function.typedargslist(),
            function.suite()));
  }

  private void addInstanceFields(
      final TypeMembers members, final PythonParser.FuncdefContext function) {
    final String self = selfOf(function);
    if (self == null) {
      return;
    }
    for (final ParserRuleContext context :
        Trees.find(function.suite(), ParserRuleContext.class, PythonParser.ClassdefContext.class)) {
      for (final Access access : accessesOf(context)) {
        if (self.equals(access.qualifier())
            && access.arguments() == Access.NO_CALL
            && !members.declaresMethod(access.name())
            && !members.fieldNames().contains(access.name())) {
          members.addField(access.name());
        }
      }
    }
  }

  /** Returns the name of the first parameter unless the method is static. */
  private static String selfOf(final PythonParser.FuncdefContext function) {
    if (function.getParent() instanceof PythonParser.Class_or_func_def_stmtContext statement
        && statement.decorator().stream()
            .anyMatch(decorator -> "staticmethod".equals(Trees.text(decorator.dotted_name())))) {
      return null;
    }
    return function.typedargslist() == null
        ? null
        : Trees.firstTokens(function.typedargslist(), 1).stream().findFirst().orElse(null);
  }

  /** Whether the body consists of more than {@code pass}, {@code ...} and a docstring. */
  private static boolean hasCode(final PythonParser.SuiteContext body) {
    if (body == null) {
      return false;
    }
    return body.stmt().stream().anyMatch(PythonMembers::hasCode) || hasCode(body.simple_stmt());
  }

  private static boolean hasCode(final PythonParser.StmtContext statement) {
    return statement.compound_stmt() != null || hasCode(statement.simple_stmt());
  }

  private static boolean hasCode(final PythonParser.Simple_stmtContext statement) {
    return statement != null
        && statement.small_stmt().stream()
            .anyMatch(
                small -> !(small instanceof PythonParser.Pass_stmtContext) && !isLiteral(small));
  }

  private static boolean isLiteral(final ParserRuleContext statement) {
    final List<String> tokens = Trees.firstTokens(statement, 2);
    return tokens.size() == 1
        && ("...".equals(tokens.getFirst()) || tokens.getFirst().matches("(?s).*[\"']"));
  }

  private static List<Access> attributeOf(final PythonParser.ExprContext expression) {
    final List<String> tokens = Trees.firstTokens(expression, 5);
    if (expression.AWAIT() != null) {
      tokens.removeFirst();
    }
    if (tokens.size() < 3 || !".".equals(tokens.get(1))) {
      return List.of();
    }
    final boolean isCall = tokens.size() > 3 && "(".equals(tokens.get(3));
    return List.of(accessOf(tokens.get(0), tokens.get(2), isCall));
  }

  /** Formatted strings are single tokens of the grammar. */
  private static List<Access> attributesOf(final List<TerminalNode> strings) {
    final List<Access> accesses = new ArrayList<>();
    for (final TerminalNode string : strings) {
      if (FORMATTED.matcher(string.getText()).matches()) {
        final Matcher matcher = ATTRIBUTE.matcher(string.getText());
        while (matcher.find()) {
          accesses.add(accessOf(matcher.group(1), matcher.group(2), matcher.group(3) != null));
        }
      }
    }
    return accesses;
  }

  private static Access accessOf(final String qualifier, final String name, final boolean isCall) {
    return isCall ? Access.call(qualifier, name, Access.UNKNOWN) : Access.of(qualifier, name);
  }
}
