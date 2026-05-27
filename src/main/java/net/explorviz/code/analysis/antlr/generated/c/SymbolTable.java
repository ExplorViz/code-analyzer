package net.explorviz.code.analysis.antlr.generated.c;

import java.util.HashSet;
import java.util.Stack;

public class SymbolTable {
  private final Stack<Symbol> scopeStack = new Stack<>();
  private int blockCounter = 0;

  public SymbolTable() {
    final Symbol globalScope = createSymbol("global", TypeClassification.Global_);
    scopeStack.push(globalScope);

    define(createSymbol("auto", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("constexpr", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("extern", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("register", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("static", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("thread_local", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("_Thread_local", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("typedef", TypeClassification.StorageClassSpecifier_));

    define(createSymbol("enum", TypeClassification.EnumSpecifier_));

    define(createSymbol("struct", TypeClassification.StorageClassSpecifier_));
    define(createSymbol("union", TypeClassification.StorageClassSpecifier_));

    define(createSymbol("const", TypeClassification.TypeQualifier_));
    define(createSymbol("restrict", TypeClassification.TypeQualifier_));
    define(createSymbol("volatile", TypeClassification.TypeQualifier_));
    define(createSymbol("_Atomic", TypeClassification.TypeQualifier_, TypeClassification.AtomicTypeSpecifier_));

    define(createSymbol("void", TypeClassification.TypeSpecifier_));
    define(createSymbol("char", TypeClassification.TypeSpecifier_));
    define(createSymbol("short", TypeClassification.TypeSpecifier_));
    define(createSymbol("int", TypeClassification.TypeSpecifier_));
    define(createSymbol("long", TypeClassification.TypeSpecifier_));
    define(createSymbol("float", TypeClassification.TypeSpecifier_));
    define(createSymbol("double", TypeClassification.TypeSpecifier_));
    define(createSymbol("signed", TypeClassification.TypeSpecifier_));
    define(createSymbol("unsigned", TypeClassification.TypeSpecifier_));
    define(createSymbol("_BitInt", TypeClassification.TypeSpecifier_));
    define(createSymbol("bool", TypeClassification.TypeSpecifier_));
    define(createSymbol("_Bool", TypeClassification.TypeSpecifier_));
    define(createSymbol("_Complex", TypeClassification.TypeSpecifier_));
    define(createSymbol("_Decimal32", TypeClassification.TypeSpecifier_));
    define(createSymbol("_Decimal64", TypeClassification.TypeSpecifier_));
    define(createSymbol("_Decimal128", TypeClassification.TypeSpecifier_));
    define(createSymbol("__m128", TypeClassification.TypeSpecifier_));
    define(createSymbol("__m128d", TypeClassification.TypeSpecifier_));
    define(createSymbol("__m128i", TypeClassification.TypeSpecifier_));
    define(createSymbol("__extension__", TypeClassification.TypeSpecifier_));

    define(createSymbol("inline", TypeClassification.FunctionSpecifier_));
    define(createSymbol("_Noreturn", TypeClassification.FunctionSpecifier_));
    define(createSymbol("__inline__", TypeClassification.FunctionSpecifier_));
  }

  private Symbol createSymbol(final String name, final TypeClassification... classifications) {
    final Symbol symbol = new Symbol();
    symbol.setName(name);
    final HashSet<TypeClassification> classSet = new HashSet<>();
    for (final TypeClassification c : classifications) {
      classSet.add(c);
    }
    symbol.setClassification(classSet);
    symbol.setPredefined(true);
    return symbol;
  }

  public void enterScope(final Symbol newScope) {
    final Symbol current = scopeStack.peek();
    if (newScope == current) {
      return;
    }
    scopeStack.push(newScope);
  }

  public void exitScope() {
    scopeStack.pop();
    if (scopeStack.isEmpty()) {
      throw new RuntimeException("Cannot exit global scope");
    }
  }

  public Symbol currentScope() {
    if (scopeStack.isEmpty()) {
      return null;
    }
    return scopeStack.peek();
  }

  public boolean define(final Symbol symbol) {
    final Symbol currentScope = currentScope();
    return defineInScope(currentScope, symbol);
  }

  public boolean defineInScope(final Symbol currentScope, final Symbol symbol) {
    if (currentScope.getMembers().containsKey(symbol.getName())) {
      return false;
    }
    symbol.setParent(currentScope);
    currentScope.getMembers().put(symbol.getName(), symbol);
    return true;
  }

  public Symbol resolve(final String name) {
    return resolve(name, null);
  }

  public Symbol resolve(final String name, final Symbol startScope) {
    if (startScope == null) {
      for (int i = scopeStack.size() - 1; i >= 0; i--) {
        final Symbol scope = scopeStack.get(i);
        final Symbol symbol = scope.getMembers().get(name);
        if (symbol != null) {
          return symbol;
        }
      }
      return null;
    }
    return startScope.getMembers().get(name);
  }

  public Symbol pushBlockScope() {
    final Symbol blockScope = new Symbol();
    blockScope.setName("block" + (++blockCounter));
    final HashSet<TypeClassification> classSet = new HashSet<>();
    classSet.add(TypeClassification.Block_);
    blockScope.setClassification(classSet);
    blockScope.setPredefined(true);
    enterScope(blockScope);
    return blockScope;
  }

  public void popBlockScope() {
    exitScope();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    toStringHelper(sb, scopeStack.get(0), 0);
    return sb.toString();
  }

  private void toStringHelper(final StringBuilder sb, final Symbol scope, final int depth) {
    final String indent = " ".repeat(depth);
    for (final var entry : scope.getMembers().entrySet()) {
      final Symbol sym = entry.getValue();
      if (!sym.isPredefined()) {
        sb.append(indent).append(sym.toString()).append("\n");
      }
      if (sym.getClassification().contains(TypeClassification.Block_)
          || sym.getClassification().contains(TypeClassification.Function_)) {
        toStringHelper(sb, sym, depth + 1);
      }
    }
  }
}
