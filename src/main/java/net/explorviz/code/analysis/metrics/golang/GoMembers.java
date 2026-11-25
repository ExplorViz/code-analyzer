package net.explorviz.code.analysis.metrics.golang;

import java.util.List;
import net.explorviz.code.analysis.antlr.generated.golang.GoParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Members of the types of Go, whose methods are declared anywhere in the same file. */
public final class GoMembers implements MemberGrammar {

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    // Local types have no methods.
    if (!(context instanceof GoParser.TypeDefContext type)
        || type.IDENTIFIER() == null
        || Trees.enclosing(type, GoParser.BlockContext.class) != null) {
      return null;
    }
    final String name = type.IDENTIFIER().getText();
    final TypeMembers members = new TypeMembers(name);
    if (type.type_() != null
        && type.type_().typeLit() != null
        && type.type_().typeLit().structType() != null) {
      type.type_().typeLit().structType().fieldDecl().forEach(field -> addFields(members, field));
    }
    if (Trees.root(type) instanceof GoParser.SourceFileContext file) {
      file.methodDecl().forEach(method -> addMethod(members, name, method));
    }
    return members;
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    if (!(context instanceof GoParser.PrimaryExprContext)) {
      return List.of();
    }
    final List<String> tokens = Trees.firstTokens(context, 3);
    return tokens.size() == 3 && ".".equals(tokens.get(1))
        ? List.of(Access.of(tokens.get(0), tokens.get(2)))
        : List.of();
  }

  @Override
  public boolean opensScope(final ParserRuleContext context) {
    return context instanceof GoParser.BlockContext
        || context instanceof GoParser.FunctionLitContext
        || context instanceof GoParser.IfStmtContext
        || context instanceof GoParser.ForStmtContext
        || context instanceof GoParser.SwitchStmtContext
        || context instanceof GoParser.CommClauseContext;
  }

  @Override
  public List<String> declaredVariables(final ParserRuleContext context) {
    return switch (context) {
      case GoParser.ShortVarDeclContext ctx -> namesOf(ctx.identifierList());
      case GoParser.VarSpecContext ctx -> namesOf(ctx.identifierList());
      default -> List.of();
    };
  }

  private static List<String> namesOf(final GoParser.IdentifierListContext names) {
    return names == null
        ? List.of()
        : names.IDENTIFIER().stream().map(TerminalNode::getText).toList();
  }

  private static void addFields(final TypeMembers members, final GoParser.FieldDeclContext field) {
    if (field.identifierList() != null) {
      for (final TerminalNode name : field.identifierList().IDENTIFIER()) {
        members.addField(name.getText());
      }
    } else if (field.embeddedField() != null && field.embeddedField().typeName() != null) {
      members.addField(Trees.tokens(field.embeddedField().typeName()).getLast());
    }
  }

  private static void addMethod(
      final TypeMembers members, final String typeName, final GoParser.MethodDeclContext method) {
    if (method.IDENTIFIER() == null
        || method.receiver() == null
        || method.receiver().parameters() == null
        || method.receiver().parameters().parameterDecl().isEmpty()) {
      return;
    }
    final GoParser.ParameterDeclContext receiver = method.receiver().parameters().parameterDecl(0);
    if (receiver.type_() == null || !typeName.equals(nameOf(receiver.type_()))) {
      return;
    }
    final String self =
        receiver.identifierList() == null
            ? null
            : receiver.identifierList().IDENTIFIER(0).getText();
    final GoParser.BlockContext body = method.block();
    members.addMethod(
        Method.of(
            method.IDENTIFIER().getText(),
            self,
            Method.Arguments.ANY,
            body != null
                && body.statementList() != null
                && !body.statementList().statement().isEmpty(),
            body));
  }

  /** Returns the name of a type without pointers, parentheses and type arguments. */
  private static String nameOf(final GoParser.Type_Context type) {
    return Trees.find(type, GoParser.TypeNameContext.class).stream()
        .findFirst()
        .map(ParserRuleContext::getText)
        .orElse(null);
  }
}
