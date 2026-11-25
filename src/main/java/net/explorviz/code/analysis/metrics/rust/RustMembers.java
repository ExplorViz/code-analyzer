package net.explorviz.code.analysis.metrics.rust;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.metrics.Trees;
import net.explorviz.code.analysis.metrics.cohesion.Access;
import net.explorviz.code.analysis.metrics.cohesion.MemberGrammar;
import net.explorviz.code.analysis.metrics.cohesion.Method;
import net.explorviz.code.analysis.metrics.cohesion.TypeMembers;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Members of the types of Rust, whose methods are declared by the {@code impl} blocks of the same
 * module and file. Associated functions that create the type are constructors.
 */
public final class RustMembers implements MemberGrammar {

  private static final String SELF = "self";
  private static final String SELF_TYPE = "Self";

  @Override
  public TypeMembers membersOf(final ParserRuleContext context) {
    return switch (context) {
      case RustParser.StructStructContext ctx ->
          implemented(ctx, ctx.identifier(), Trees.find(ctx, RustParser.StructFieldContext.class));
      case RustParser.Union_Context ctx ->
          implemented(ctx, ctx.identifier(), Trees.find(ctx, RustParser.StructFieldContext.class));
      case RustParser.EnumerationContext ctx -> implemented(ctx, ctx.identifier(), List.of());
      case RustParser.TupleStructContext ctx -> tuple(ctx);
      case RustParser.Trait_Context ctx when ctx.identifier() != null -> {
        final TypeMembers members = new TypeMembers(ctx.identifier().getText(), SELF_TYPE);
        ctx.associatedItem().forEach(item -> addMethod(members, "", item));
        yield members;
      }
      default -> null;
    };
  }

  @Override
  public List<Access> accessesOf(final ParserRuleContext context) {
    return switch (context) {
      case RustParser.FieldExpressionContext ctx -> accessOf(ctx, ".");
      case RustParser.MethodCallExpressionContext ctx -> accessOf(ctx, ".");
      case RustParser.TupleIndexingExpressionContext ctx -> accessOf(ctx, ".");
      case RustParser.PathInExpressionContext ctx -> accessOf(ctx, "::");
      case RustParser.TokenTreeContext ctx -> macroAccessesOf(ctx);
      default -> List.of();
    };
  }

  @Override
  public boolean rebindsSelf(final ParserRuleContext context) {
    return context instanceof RustParser.Function_Context
        || context instanceof RustParser.ImplementationContext;
  }

  @Override
  public boolean isIrrelevant(final ParserRuleContext context) {
    return context instanceof RustParser.OuterAttributeContext;
  }

  private static TypeMembers tuple(final RustParser.TupleStructContext ctx) {
    final TypeMembers members = implemented(ctx, ctx.identifier(), List.of());
    if (members != null && ctx.tupleFields() != null) {
      for (int i = 0; i < ctx.tupleFields().tupleField().size(); i++) {
        members.addField(String.valueOf(i));
      }
    }
    return members;
  }

  private static TypeMembers implemented(
      final ParserRuleContext type,
      final RustParser.IdentifierContext identifier,
      final List<RustParser.StructFieldContext> fields) {
    if (identifier == null) {
      return null;
    }
    final String name = identifier.getText();
    final TypeMembers members = new TypeMembers(name, SELF_TYPE);
    for (final RustParser.StructFieldContext field : fields) {
      if (field.identifier() != null) {
        members.addField(field.identifier().getText());
      }
    }
    final ParserRuleContext module = Trees.enclosing(type, RustParser.ModuleContext.class);
    for (final RustParser.ImplementationContext implementation :
        Trees.find(Trees.root(type), RustParser.ImplementationContext.class)) {
      if (name.equals(nameOf(typeOf(implementation)))
          && Trees.enclosing(implementation, RustParser.ModuleContext.class) == module) {
        associatedItemsOf(implementation).forEach(item -> addMethod(members, name, item));
      }
    }
    return members;
  }

  private static RustParser.Type_Context typeOf(
      final RustParser.ImplementationContext implementation) {
    if (implementation.inherentImpl() != null) {
      return implementation.inherentImpl().type_();
    }
    return implementation.traitImpl() == null ? null : implementation.traitImpl().type_();
  }

  private static List<RustParser.AssociatedItemContext> associatedItemsOf(
      final RustParser.ImplementationContext implementation) {
    return implementation.inherentImpl() == null
        ? implementation.traitImpl().associatedItem()
        : implementation.inherentImpl().associatedItem();
  }

  /** Returns the simple name of a type without references, path and type arguments. */
  private static String nameOf(final RustParser.Type_Context type) {
    final List<RustParser.TypePathContext> paths =
        Trees.find(type, RustParser.TypePathContext.class);
    if (paths.isEmpty() || paths.getFirst().typePathSegment().isEmpty()) {
      return null;
    }
    return Trees.text(paths.getFirst().typePathSegment().getLast().pathIdentSegment());
  }

  private static void addMethod(
      final TypeMembers members,
      final String typeName,
      final RustParser.AssociatedItemContext item) {
    final RustParser.Function_Context function = item.function_();
    if (function == null || function.identifier() == null) {
      return;
    }
    final boolean hasSelf =
        function.functionParameters() != null && function.functionParameters().selfParam() != null;
    if (!hasSelf && isConstructor(function, typeName)) {
      return;
    }
    final RustParser.BlockExpressionContext body = function.blockExpression();
    members.addMethod(
        Method.of(
            function.identifier().getText(),
            hasSelf ? SELF : null,
            Method.Arguments.ANY,
            body != null && body.statements() != null,
            body));
  }

  /** Whether the function returns the type, possibly wrapped as in {@code Option<Self>}. */
  private static boolean isConstructor(
      final RustParser.Function_Context function, final String typeName) {
    if (function.functionReturnType() == null) {
      return false;
    }
    final List<String> returned = Trees.tokens(function.functionReturnType());
    return returned.contains(SELF_TYPE) || returned.contains(typeName);
  }

  private static List<Access> accessOf(final ParserRuleContext context, final String operator) {
    final List<String> tokens = Trees.firstTokens(context, 3);
    return tokens.size() == 3 && operator.equals(tokens.get(1))
        ? List.of(Access.of(tokens.get(0), tokens.get(2)))
        : List.of();
  }

  /** The arguments of macros are sequences of tokens. */
  private static List<Access> macroAccessesOf(final RustParser.TokenTreeContext tree) {
    final List<String> tokens =
        tree.tokenTreeToken().stream().map(ParserRuleContext::getText).toList();
    final List<Access> accesses = new ArrayList<>();
    for (int i = 0; i + 2 < tokens.size(); i++) {
      final boolean isMember =
          ".".equals(tokens.get(i + 1)) && (i == 0 || !".".equals(tokens.get(i - 1)));
      if (isMember || "::".equals(tokens.get(i + 1))) {
        accesses.add(Access.of(tokens.get(i), tokens.get(i + 2)));
      }
    }
    return accesses;
  }
}
