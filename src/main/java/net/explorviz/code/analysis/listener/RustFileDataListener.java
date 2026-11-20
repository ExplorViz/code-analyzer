package net.explorviz.code.analysis.listener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.explorviz.code.analysis.antlr.generated.rust.RustParser;
import net.explorviz.code.analysis.antlr.generated.rust.RustParserBaseListener;
import net.explorviz.code.analysis.handler.ClassDataHandler;
import net.explorviz.code.analysis.handler.MethodDataHandler;
import net.explorviz.code.analysis.handler.RustFileDataHandler;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** ANTLR listener for extracting file data from Rust source code. */
public class RustFileDataListener extends RustParserBaseListener implements CommonFileDataListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(RustFileDataListener.class);

  private final RustFileDataHandler fileDataHandler;
  private final CommonTokenStream tokens;
  private int functionCount = 0;
  private int variableCount = 0;

  /** Fqns of the types of the enclosing {@code impl} blocks, innermost first. */
  private final Deque<String> implTypeStack = new ArrayDeque<>();

  /**
   * Members of {@code impl} blocks in source order. They are attached to their type at the end of
   * the walk, so that an {@code impl} may precede the type declaration.
   */
  private final List<PendingImplMember> pendingImplMembers = new ArrayList<>();

  /** A method or implemented trait of an {@code impl} block, attached in {@link #exitCrate}. */
  private record PendingImplMember(
      String typeFqn,
      String traitName,
      String functionName,
      String functionKey,
      String returnType,
      MethodDataHandler fallback,
      Consumer<MethodDataHandler> populate) {}

  public RustFileDataListener(
      final RustFileDataHandler fileDataHandler, final CommonTokenStream tokens) {
    this.fileDataHandler = fileDataHandler;
    this.tokens = tokens;
  }

  @Override
  public void enterCrate(final RustParser.CrateContext ctx) {
    final int sloc = getSloc(tokens);
    final int cloc = getCloc();

    fileDataHandler.addMetric(SLOC, String.valueOf(sloc));
    fileDataHandler.addMetric(CLOC, String.valueOf(cloc));

    LOGGER
        .atTrace()
        .addArgument(fileDataHandler.getFileName())
        .addArgument(sloc)
        .log("{} - SLOC: {}");
  }

  @Override
  public void exitCrate(final RustParser.CrateContext ctx) {
    resolveImplMembers();
    fileDataHandler.addMetric(FUNCTION_COUNT, String.valueOf(functionCount));
    fileDataHandler.addMetric(VARIABLE_COUNT, String.valueOf(variableCount));
    addImportAndClassCountMetrics(fileDataHandler);
  }

  @Override
  public void enterModule(final RustParser.ModuleContext ctx) {
    // `mod name;` only refers to another file and declares nothing here.
    if (ctx.identifier() != null && ctx.LCURLYBRACE() != null) {
      fileDataHandler.enterModule(ctx.identifier().getText());
    }
  }

  @Override
  public void exitModule(final RustParser.ModuleContext ctx) {
    if (ctx.identifier() != null && ctx.LCURLYBRACE() != null) {
      fileDataHandler.leaveModule();
    }
  }

  @Override
  public void enterUseDeclaration(final RustParser.UseDeclarationContext ctx) {
    if (ctx.useTree() != null) {
      fileDataHandler.addImport(ctx.useTree().getText());
    }
  }

  @Override
  public void enterStructStruct(final RustParser.StructStructContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String structName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(structName);
    fileDataHandler.enterClass(structName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitStructStruct(final RustParser.StructStructContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterTupleStruct(final RustParser.TupleStructContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String structName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(structName);
    fileDataHandler.enterClass(structName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitTupleStruct(final RustParser.TupleStructContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumeration(final RustParser.EnumerationContext ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String enumName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(enumName);
    fileDataHandler.enterClass(enumName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsEnum();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitEnumeration(final RustParser.EnumerationContext ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterEnumItem(final RustParser.EnumItemContext ctx) {
    if (ctx.identifier() != null) {
      final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
      if (classData != null) {
        classData.addEnumConstant(ctx.identifier().getText());
      }
    }
  }

  @Override
  public void enterTrait_(final RustParser.Trait_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String traitName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(traitName);
    fileDataHandler.enterClass(traitName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsInterface();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitTrait_(final RustParser.Trait_Context ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterUnion_(final RustParser.Union_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    final String unionName = ctx.identifier().getText();
    final String fqn = fileDataHandler.buildFqn(unionName);
    fileDataHandler.enterClass(unionName, fqn);

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      classData.setIsStruct();
      classData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
      classData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
    }
  }

  @Override
  public void exitUnion_(final RustParser.Union_Context ctx) {
    if (ctx.identifier() != null) {
      fileDataHandler.leaveClass();
    }
  }

  @Override
  public void enterInherentImpl(final RustParser.InherentImplContext ctx) {
    implTypeStack.push(implTypeFqn(ctx.type_()));
  }

  @Override
  public void exitInherentImpl(final RustParser.InherentImplContext ctx) {
    implTypeStack.pop();
  }

  @Override
  public void enterTraitImpl(final RustParser.TraitImplContext ctx) {
    final String typeFqn = implTypeFqn(ctx.type_());
    implTypeStack.push(typeFqn);
    if (!typeFqn.isEmpty() && ctx.typePath() != null) {
      pendingImplMembers.add(
          new PendingImplMember(typeFqn, ctx.typePath().getText(), null, null, null, null, null));
    }
  }

  @Override
  public void exitTraitImpl(final RustParser.TraitImplContext ctx) {
    implTypeStack.pop();
  }

  /** Returns the fqn of the type an {@code impl} block is for, or "" if it has no simple name. */
  private String implTypeFqn(final RustParser.Type_Context typeCtx) {
    final String typeName = extractTypeName(typeCtx);
    return typeName == null ? "" : fileDataHandler.buildFqn(typeName);
  }

  private void resolveImplMembers() {
    for (final PendingImplMember member : pendingImplMembers) {
      final ClassDataHandler classData = fileDataHandler.getClassData(member.typeFqn());
      if (member.traitName() != null) {
        if (classData != null) {
          classData.addImplementedInterface(member.traitName());
        }
      } else if (classData != null) {
        fileDataHandler.removeGlobalFunction(member.fallback());
        member
            .populate()
            .accept(
                classData.addMethod(
                    member.functionName(), member.functionKey(), member.returnType()));
      }
    }
    pendingImplMembers.clear();
  }

  @Override
  public void enterStructField(final RustParser.StructFieldContext ctx) {
    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData == null || ctx.identifier() == null || ctx.type_() == null) {
      return;
    }

    classData.addField(ctx.identifier().getText(), typeText(ctx.type_()), new ArrayList<>());
    variableCount++;
  }

  @Override
  public void enterFunction_(final RustParser.Function_Context ctx) {
    if (ctx.identifier() == null) {
      return;
    }

    functionCount++;
    final String functionName = ctx.identifier().getText();
    final String returnType = extractReturnType(ctx);
    final Consumer<MethodDataHandler> populate =
        methodData -> {
          addFunctionParameters(methodData, ctx);
          if (ctx.start != null && ctx.stop != null) {
            methodData.setLines(ctx.start.getLine(), ctx.stop.getLine());
          }
          methodData.addMetric(SLOC, String.valueOf(getSloc(ctx, tokens)));
          methodData.addMetric(LINE_COUNT, String.valueOf(calculateLoc(ctx)));
        };
    final String functionKey = functionName + "#" + extractParameterTypes(ctx).hashCode();

    final String implTypeFqn = implTypeStack.peek();
    if (implTypeFqn != null) {
      // Added as a file-level function for now, moved to the type once the walk is done.
      final MethodDataHandler fallback =
          fileDataHandler.addGlobalFunction(functionName, returnType);
      populate.accept(fallback);
      if (!implTypeFqn.isEmpty()) {
        pendingImplMembers.add(
            new PendingImplMember(
                implTypeFqn, null, functionName, functionKey, returnType, fallback, populate));
      }
      return;
    }

    final ClassDataHandler classData = fileDataHandler.getCurrentClassData();
    if (classData != null) {
      populate.accept(classData.addMethod(functionName, functionKey, returnType));
    } else {
      populate.accept(fileDataHandler.addGlobalFunction(functionName, returnType));
    }
  }

  @Override
  public void enterLetStatement(final RustParser.LetStatementContext ctx) {
    variableCount++;
  }

  @Override
  public void enterConstantItem(final RustParser.ConstantItemContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  @Override
  public void enterStaticItem(final RustParser.StaticItemContext ctx) {
    if (ctx.identifier() != null) {
      variableCount++;
    }
  }

  private String extractReturnType(final RustParser.Function_Context ctx) {
    if (ctx.functionReturnType() != null && ctx.functionReturnType().type_() != null) {
      return typeText(ctx.functionReturnType().type_());
    }
    return "void";
  }

  private List<String> extractParameterTypes(final RustParser.Function_Context ctx) {
    final List<String> paramTypes = new ArrayList<>();
    if (ctx.functionParameters() == null) {
      return paramTypes;
    }

    for (final RustParser.FunctionParamContext param : ctx.functionParameters().functionParam()) {
      if (param.type_() != null) {
        paramTypes.add(typeText(param.type_()));
      } else if (param.functionParamPattern() != null
          && param.functionParamPattern().type_() != null) {
        paramTypes.add(typeText(param.functionParamPattern().type_()));
      }
    }
    return paramTypes;
  }

  private void addFunctionParameters(
      final MethodDataHandler methodData, final RustParser.Function_Context ctx) {
    if (ctx.functionParameters() == null) {
      return;
    }

    for (final RustParser.FunctionParamContext param : ctx.functionParameters().functionParam()) {
      if (param.functionParamPattern() != null) {
        final RustParser.FunctionParamPatternContext pattern = param.functionParamPattern();
        final String paramType = pattern.type_() != null ? typeText(pattern.type_()) : "unknown";
        final List<String> modifiers = new ArrayList<>();
        final RustParser.IdentifierPatternContext identifierPattern =
            asIdentifierPattern(pattern.pattern());
        final String paramName;
        if (identifierPattern != null && identifierPattern.pattern() == null) {
          // `mut b` / `ref b`: the binding is `b`, `mut` and `ref` are modifiers.
          if (identifierPattern.KW_REF() != null) {
            modifiers.add("ref");
          }
          if (identifierPattern.KW_MUT() != null) {
            modifiers.add("mut");
          }
          paramName = identifierPattern.identifier().getText();
        } else {
          paramName = pattern.pattern() != null ? typeText(pattern.pattern()) : "";
        }
        methodData.addParameter(paramName, paramType, modifiers);
      } else if (param.type_() != null) {
        methodData.addParameter("", typeText(param.type_()), new ArrayList<>());
      }
    }
  }

  /** Returns the identifier pattern if {@code pattern} is a plain binding like {@code mut b}. */
  private static RustParser.IdentifierPatternContext asIdentifierPattern(
      final RustParser.PatternContext pattern) {
    ParseTree node = pattern;
    while (node != null && !(node instanceof RustParser.IdentifierPatternContext)) {
      node = node.getChildCount() == 1 ? node.getChild(0) : null;
    }
    return (RustParser.IdentifierPatternContext) node;
  }

  /**
   * Returns the simple name of the type an {@code impl} block is for, without generic arguments,
   * path, references or pointers: {@code Wrapper} for {@code &'a mut crate::m::Wrapper<T>}. Returns
   * null for types without such a name, e.g. tuples or slices.
   */
  private static String extractTypeName(final RustParser.Type_Context typeCtx) {
    if (typeCtx == null) {
      return null;
    }
    RustParser.TypeNoBoundsContext type = typeCtx.typeNoBounds();
    while (type != null) {
      if (type.referenceType() != null) {
        type = type.referenceType().typeNoBounds();
      } else if (type.rawPointerType() != null) {
        type = type.rawPointerType().typeNoBounds();
      } else if (type.parenthesizedType() != null) {
        type = type.parenthesizedType().type_().typeNoBounds();
      } else if (type.typePath() != null) {
        return lastPathSegment(type.typePath());
      } else if (type.traitObjectTypeOneBound() != null) {
        // A plain path like `Wrapper<T>` is parsed as a trait object without `dyn`.
        return lastPathSegment(type.traitObjectTypeOneBound().traitBound().typePath());
      } else {
        return null;
      }
    }
    return null;
  }

  private static String lastPathSegment(final RustParser.TypePathContext typePath) {
    if (typePath == null) {
      return null;
    }
    final List<RustParser.TypePathSegmentContext> segments = typePath.typePathSegment();
    return segments.get(segments.size() - 1).pathIdentSegment().getText();
  }

  /**
   * Returns the source text of {@code ctx} with a single space between adjacent words, e.g. {@code
   * &mut dyn Write} instead of the glued {@code &mutdynWrite} of {@code getText()}. Punctuation
   * stays unspaced, e.g. {@code HashMap<String,Vec<i32>>}.
   */
  private String typeText(final ParserRuleContext ctx) {
    if (ctx == null || ctx.start == null || ctx.stop == null || tokens == null) {
      return ctx == null ? "" : ctx.getText();
    }
    final StringBuilder text = new StringBuilder();
    String previous = null;
    for (int i = ctx.start.getTokenIndex(); i <= ctx.stop.getTokenIndex(); i++) {
      final Token token = tokens.get(i);
      if (token.getChannel() != Token.DEFAULT_CHANNEL || token.getType() == Token.EOF) {
        continue;
      }
      final String current = token.getText();
      if (previous != null && endsWithWordChar(previous) && startsWithWordChar(current)) {
        text.append(' ');
      }
      text.append(current);
      previous = current;
    }
    return text.toString();
  }

  private static boolean startsWithWordChar(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    final char first = text.charAt(0);
    return Character.isLetterOrDigit(first) || first == '_' || first == '\'' || first == '$';
  }

  private static boolean endsWithWordChar(final String text) {
    if (text.isEmpty()) {
      return false;
    }
    final char last = text.charAt(text.length() - 1);
    return Character.isLetterOrDigit(last) || last == '_';
  }

  private int getCloc() {
    if (tokens == null) {
      return 0;
    }

    int commentLines = 0;
    for (int i = 0; i < tokens.size(); i++) {
      final var token = tokens.get(i);
      if (token.getChannel() != 0) {
        final String tokenText = token.getText();
        if (tokenText != null) {
          if (tokenText.trim().startsWith("//")) {
            commentLines++;
          } else if (tokenText.trim().startsWith("/*")) {
            final long newlines = tokenText.chars().filter(ch -> ch == '\n').count();
            commentLines += (int) newlines + 1;
          }
        }
      }
    }
    return commentLines;
  }
}
