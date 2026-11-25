package net.explorviz.code.analysis.metrics.php;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.explorviz.code.analysis.antlr.generated.php.PhpParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;

/** Members of the classes, traits, interfaces and enums of PHP. */
public final class PhpMembers implements MemberGrammar {

  private static final String SELF = "$this";

  private static final Set<String> CONSTRUCTORS = Set.of("__construct", "__destruct");

  /** Rest of an interpolated {@code "$this->x"}, which the grammar parses as text. */
  private static final Pattern INTERPOLATED = Pattern.compile("^\\??->(\\w+)");

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case PhpParser.ClassDeclarationContext ctx ->
          classMembersOf(Trees.text(ctx.identifier()), ctx.classStatement());
      case PhpParser.AnonymousClassContext ctx -> classMembersOf(null, ctx.classStatement());
      case PhpParser.EnumDeclarationContext ctx -> enumMembersOf(ctx);
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case PhpParser.ChainContext ctx -> accessOf(ctx, "->");
      case PhpParser.IndirectTypeRefContext ctx -> accessOf(ctx, "->");
      case PhpParser.ChainBaseContext ctx -> accessOf(ctx, "::");
      case PhpParser.ClassConstantContext ctx -> accessOf(ctx, "::");
      case PhpParser.StringContext ctx -> interpolatedAccessesOf(ctx);
      default -> List.of();
    };
  }

  @Override
  public boolean rebindsSelf(final ParserRuleContext context) {
    return context instanceof PhpParser.FunctionDeclarationContext;
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof PhpParser.AttributesContext;
  }

  private static TypeMembers classMembersOf(
      final String typeName, final List<PhpParser.ClassStatementContext> statements) {
    final TypeMembers members = new TypeMembers(typeName, "self", "static");
    for (final PhpParser.ClassStatementContext statement : statements) {
      if (statement.Function_() != null) {
        final PhpParser.BlockStatementContext body =
            statement.methodBody() == null ? null : statement.methodBody().blockStatement();
        addMethod(members, statement.identifier(), statement.formalParameterList(), body);
      } else if (statement.propertyModifiers() != null) {
        statement.variableInitializer().forEach(variable -> addField(members, variable));
      }
    }
    return members;
  }

  private static TypeMembers enumMembersOf(final PhpParser.EnumDeclarationContext ctx) {
    final TypeMembers members = new TypeMembers(Trees.text(ctx.identifier()), "self", "static");
    for (final PhpParser.EnumItemContext item : ctx.enumItem()) {
      final PhpParser.FunctionDeclarationContext function = item.functionDeclaration();
      if (function != null) {
        addMethod(
            members,
            function.identifier(),
            function.formalParameterList(),
            function.blockStatement());
      }
    }
    return members;
  }

  private static void addField(
      final TypeMembers members, final PhpParser.VariableInitializerContext variable) {
    if (variable != null && variable.VarName() != null) {
      members.addField(withoutDollar(variable.VarName().getText()));
    }
  }

  private static void addMethod(
      final TypeMembers members,
      final PhpParser.IdentifierContext identifier,
      final PhpParser.FormalParameterListContext parameters,
      final PhpParser.BlockStatementContext body) {
    if (identifier == null) {
      return;
    }
    final String name = identifier.getText();
    if (CONSTRUCTORS.contains(name.toLowerCase(Locale.ROOT))) {
      addPromotedFields(members, parameters);
      return;
    }
    members.addMethod(
        Method.of(
            name,
            SELF,
            Method.Arguments.ANY,
            body != null
                && body.innerStatementList() != null
                && !body.innerStatementList().innerStatement().isEmpty(),
            parameters,
            body));
  }

  private static void addPromotedFields(
      final TypeMembers members, final PhpParser.FormalParameterListContext parameters) {
    if (parameters == null) {
      return;
    }
    for (final PhpParser.FormalParameterContext parameter : parameters.formalParameter()) {
      if (!parameter.memberModifier().isEmpty()) {
        addField(members, parameter.variableInitializer());
      }
    }
  }

  private static List<Access> accessOf(final ParserRuleContext context, final String operator) {
    final List<String> tokens = Trees.firstTokens(context, 3);
    return tokens.size() == 3 && operator.equals(tokens.get(1))
        ? List.of(Access.of(tokens.get(0), withoutDollar(tokens.get(2))))
        : List.of();
  }

  private static List<Access> interpolatedAccessesOf(final PhpParser.StringContext string) {
    final List<Access> accesses = new ArrayList<>();
    final List<PhpParser.InterpolatedStringPartContext> parts = string.interpolatedStringPart();
    for (int i = 1; i < parts.size(); i++) {
      final Matcher matcher = INTERPOLATED.matcher(parts.get(i).getText());
      if (SELF.equals(parts.get(i - 1).getText()) && matcher.find()) {
        accesses.add(Access.of(SELF, matcher.group(1)));
      }
    }
    return accesses;
  }

  private static String withoutDollar(final String name) {
    return name.startsWith("$") ? name.substring(1) : name;
  }
}
