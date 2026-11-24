package metrics;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** Every construct that matters for the complexities, on its own and nested in the others. */
public abstract class ComplexityConstructs<T extends Comparable<? super T>> {

  static final boolean FLAG = Boolean.TRUE ? Boolean.FALSE : Boolean.TRUE;
  static int counter;
  final Supplier<Integer> field = () -> { if (FLAG && counter > 0) { return 1; } return 2; };
  final Runnable anonymous = FLAG ? new Runnable() {
    @Override public void run() { if (FLAG) { f(); } else { g(); } }
  } : null;

  static {
    if (FLAG) { counter = FLAG || counter > 1 ? 1 : 2; }
  }

  {
    for (int i = 0; i < 3; i++) { counter += i; }
  }

  ComplexityConstructs() { }

  <S> ComplexityConstructs(S s) { this(); if (s == null) { f(); } }

  ComplexityConstructs(int i) { if (i > 0 && i < 5) f(); else if (i > 7) g(); else { f(); g(); } }

  abstract void withoutBody();

  native int nativeMethod();

  void empty() { }

  void straight() { f(); g(); }

  int returnsValue() { return counter; }

  boolean returnsOperators(boolean a, boolean b) { return a && b || !a && counter > 1; }

  void emptyReturn() { if (FLAG) { return; } f(); }

  @SuppressWarnings(FLAG_NAME + (1 > 0 ? "a" : "b"))
  <S> S annotated(@Deprecated S s) { return s; }

  static final String FLAG_NAME = "x";

  void ifs(boolean a, boolean b) {
    if (a) f();
    if (b) { f(); } else { g(); }
    if (a) { if (b) { f(); } } else if (b) { f(); } else { if (a || b) g(); }
    if (a) ; else ;
  }

  void loops(int n, List<String> list) {
    for (int i = 0, j = n > 1 ? 1 : 2; i < n && j > 0 || FLAG; i++, j = FLAG ? i : j) { f(); }
    for (;;) { if (FLAG) break; else continue; }
  }

  void moreLoops(int n, List<String> list) {
    for (String s : FLAG ? list : null) { if (s == null) f(); }
    while (n > 0 && FLAG) { n--; }
    do { if (n > 1) { n = FLAG ? 1 : 2; } } while (n > 0 || FLAG);
    outer:
    for (String s : list) { for (String t : list) { if (s == t) continue outer; } }
  }

  int doWithReturn(int n) {
    do { if (n > 2) { return n > 5 ? 1 : 2; } n++; } while (n < 10 && FLAG);
    return n;
  }

  int ternaries(boolean a, boolean b) {
    int x = a ? 1 : 2;
    int y = a ? b ? 1 : 2 : b ? 3 : 4;
    int z = (a && b ? 1 : 2) + (a || b ? 3 : 4);
    h(a ? 1 : 2, b ? 3 : 4);
    boolean c = a && b || a;
    h((a ? b : a) ? 1 : 2, x);
    return a ? x : b ? y : z;
  }

  void tries(boolean a) throws Exception {
    try { f(); } finally { if (a) g(); }
    try { if (a) f(); } catch (RuntimeException e) { if (a && FLAG) g(); } finally { f(); }
    try { f(); } catch (IllegalStateException | IllegalArgumentException e) { g(); }
    catch (Exception e) { throw a ? e : new Exception(e); }
    try (AutoCloseable c = a ? null : open(); AutoCloseable d = open()) { f(); }
    try (AutoCloseable c = open()) { try { f(); } catch (Exception e) { f(); } } catch (Error e) { }
  }

  int tryReturns() {
    try { return counter; } catch (RuntimeException e) { return FLAG ? 1 : 2; } finally { f(); }
  }

  void locks(boolean a) {
    synchronized (a ? this : "x") { if (a) f(); }
    { if (a) f(); }
    assert a && FLAG : a ? "x" : "y";
    throw a ? new IllegalStateException() : new IllegalArgumentException();
  }

  void oldSwitches(int n, boolean a) {
    switch (n) { }
    switch (n) { case 1: f(); break; case 2: g(); break; default: f(); }
    switch (n) { case 1: case 2: f(); case 3: if (a) g(); break; default: if (a) f(); else g(); }
    switch (a ? 1 : 2) { default: case 1: f(); break; case 2: case 3: }
    switch (n) { case 1: f(); default: }
    switch (n) { case 1: { f(); } case 2: { } case 3: ; }
    switch (n) { case 1: default: f(); }
  }

  void mixedSwitches(int n, boolean a) {
    switch (n) { case 1, 2: f(); break; case 3: case 4, 5: g(); break; case 6: default: }
    switch (n) { case 1, 2: case 3: default: if (a && FLAG) f(); }
    switch (n) { case 1 -> f(); case 2, 3 -> { if (a) f(); } default -> throw new Error(); }
    switch (n) { case 1 -> f(); case 2 -> { } }
    switch (n) { case 1 -> { switch (n) { case 2 -> f(); default -> g(); } } default -> f(); }
  }

  int switchExpressions(int n, boolean a) {
    int x = switch (n) { case 1 -> a ? 1 : 2; case 2, 3 -> { yield a ? 1 : 2; } default -> 3; };
    int y = switch (n) { case 1: yield 1; case 2: case 3: yield a && FLAG ? 1 : 2; default: yield 4; };
    h(switch (n) { case 1 -> 1; default -> 2; }, a ? x : y);
    return switch (n) {
      case 1 -> { if (a && FLAG) { yield 1; } yield a ? 2 : 3; }
      default -> { for (int i = 0; i < n; i++) { f(); } yield 4; }
    };
  }

  int patterns(Object o, boolean a) {
    if (o instanceof String s && s.isEmpty()) { f(); }
    if (o instanceof String && a) { f(); }
    switch (o) {
      case Integer i when i > 1 && a || i < -5 -> f();
      case Integer i when (a ? i > 1 : i > 2) -> g();
      case String s -> { if (a) f(); }
      case Box(Integer x) -> g();
      case null, default -> f();
    }
    switch (o) {
      case String s when a: f(); break;
      case String s: g(); break;
      case null: f(); break;
      default: g();
    }
    return switch (o) {
      case String s when s.isEmpty() || a -> a ? 1 : 2;
      case Box(Object x) when FLAG -> 2;
      case null -> 3;
      case Object x -> 4;
    };
  }

  int lambdas(List<Integer> list, boolean a) {
    list.forEach(x -> f());
    list.forEach(x -> { if (x > 1 && a) { f(); } else { g(); } });
    Function<Integer, Integer> g = x -> { if (a) { return x > 1 ? 1 : 2; } return 3; };
    Function<Integer, Integer> k = x -> a ? x : 2;
    return list.stream().map(x -> { if (a || x > 2) { return a ? 1 : 2; } return 2; }).toList().size();
  }

  Runnable localClasses(boolean a, boolean b) {
    if (a) f();
    Runnable r = new Runnable() {
      final int inner = a && b ? 1 : 2;
      { if (a) f(); }
      @Override public void run() { if (a) { f(); } else { g(); } }
      int value() { return a ? 1 : 2; }
    };
    if (b) g();
    class Local {
      boolean x = a || b;
      void run() { while (x) { f(); } }
    }
    record Pair(int x, int y) {
      Pair { if (x > y || FLAG) { throw new IllegalArgumentException(); } }
      Pair(int x) { this(x, FLAG ? 1 : 2); }
    }
    enum Color {
      RED { @Override int v() { return FLAG ? 1 : 2; } }, GREEN;
      int v() { return FLAG && counter > 1 ? 3 : 4; }
    }
    interface Shape { default int area() { return FLAG ? 1 : 2; } static void s() { if (FLAG) ; } }
    if (a && b) f();
    return new Runnable() {
      @Override public void run() { if (a) { return; } else { f(); } int z = b ? 1 : 2; }
      int value() { return a && b ? 1 : 2; }
    };
  }

  void classesInBranches(boolean a) {
    if (a) {
      f();
    } else {
      new Object() { int m() { if (FLAG) { f(); } else { g(); } return 1; } };
    }
    if (a) {
      interface Local { default void m() { f(); } void n(); }
    } else {
      interface Local { default void m() { f(); } void n(); }
    }
    switch (counter) {
      case 1: new Object() { void m() { switch (counter) { case 1: f(); default: g(); } } }; break;
      case 2: new Object() { void m() { f(); } }; break;
      default: new Object() { void m() { if (FLAG) f(); else g(); } };
    }
    try { new Object() { int m() { return FLAG ? 1 : 2; } }; } catch (Exception e) { f(); }
    while (new Object() { boolean m() { return FLAG || a; } }.m()) { if (a) f(); }
  }

  void manyPaths(boolean a) {
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
    if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f(); if (a) f();
  }

  static void f() { }

  static void g() { }

  java.util.List<String> qualified() { return FLAG ? null : List.of(); }

  static int h(int a, int b) { return a; }

  static AutoCloseable open() { return null; }

  @interface Config {
    int value() default FLAG ? 1 : 2;
    int CONSTANT = FLAG ? 1 : 2;
  }

  interface Service {
    void abstractMethod();
    <S> S generic(S s);
    @Deprecated
    default <S> S genericDefault(S s) { return s == null ? null : s; }
    static int staticMethod() { if (FLAG) { return 1; } else { return 2; } }
    private void privateMethod() { if (FLAG) f(); }
  }

  enum Level {
    LOW(FLAG ? 1 : 2) { @Override int score() { if (FLAG) return 1; return 2; } },
    HIGH(3);
    Level(int i) { if (i > 1 && i < 3) f(); }
    int score() { return FLAG ? 1 : 2; }
  }

  record Box(Object value) { }

  record Point(int x, Integer y) {
    static int count;
    static { if (FLAG) f(); }
    Point { if (x < 0 || y < 0) { throw new IllegalArgumentException(); } }
    int sum() { return x > 0 ? x + y : y; }
  }
}
