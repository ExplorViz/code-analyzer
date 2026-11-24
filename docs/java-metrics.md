# Java code metrics

The ANTLR analysis of Java files adds these metrics next to the size metrics (`sloc`, `lineCount`, ...).

| Metric key | Attached to | Meaning |
| --- | --- | --- |
| `cyclomatic_complexity` | function | Cyclomatic complexity |
| `cyclomatic_complexity` | class, file | Sum over the functions (for classes: weighted methods per class) |
| `cyclomatic_complexity_weighted` | class, file | Average per function, 0 without functions |
| `nestedBlockDepth` | function | Deepest nesting of control structures |
| `npath_complexity` | function | Number of acyclic execution paths |
| `LCOM4` | class | Number of unrelated groups of methods |

Functions are methods and constructors. Abstract and native methods have a cyclomatic complexity of 1, an NPath complexity of 0 and no nested block depth.
Lambdas belong to the function around them. Functions of nested, local and anonymous classes only count for their own class and for the file.
Initializer blocks and field initializers are not exported.

The cyclomatic and NPath complexities of functions are the ones that Checkstyle 14.4.0 reports (`CyclomaticComplexity`, `NPathComplexity`).
`CheckstyleComparisonTest` compares all functions of this repository and of `ComplexityConstructs.java`.
Checkstyle has no counterpart of the other metrics.

## Cyclomatic complexity

McCabe (1976), extended by short-circuit operators: 1 plus one for every `if`, `for`, `while`, `do`, `catch`, `?:`, `&&`, `||`, `case` and `when`.
`case 1, 2` is one `case`, `default` does not count.

## Nested block depth

The body of a function has depth 1. `if`, loops, `switch`, `try`, `synchronized` and lambdas with a block body add a level, with or without braces.
`else if` stays on the level of its `if`.

## NPath complexity

Nejmeh (1988) as implemented by Checkstyle. Consecutive statements multiply, alternatives add up.

| Construct | Paths |
| --- | --- |
| `if (c) a else b` | NP(c) + NP(a) + NP(b), without `else` NP(b) = 1 |
| Loops | NP(c) + NP(body) + 1 |
| `c ? a : b` | NP(c) + NP(a) + NP(b) + 2 |
| `switch` | NP(selector) + 1 + sum of NP(case) - 1 + number of labels for each case, where `default` is no label |
| `when g` | NP(g) + 1 |
| `return e` | NP(e), at least 1 |
| `try` | (NP(try) + sum of NP(`catch`) + 1 for each `catch`) x NP(`finally`) + 1, where a try or `catch` block without branches and returns has NP 0 |
| Conditions and returned values | Number of `&&` and `\|\|`, two for each `?:` |

The table is simplified. These properties of Checkstyle are reproduced on purpose:

- Functions without branches and returns have 0 instead of 1 paths.
- The paths of the code before a `try` count one more.
- A method of a local or anonymous class discards the paths of the code before it in the function around it.
- Operators of a condition, returned value or ternary also count for everything nested in it, such as lambdas and classes.
- Ternary operators and returns up to the end of such an expression are not counted again. For `do` this includes the loop body.
- Values are not limited to 64 bit, but to the largest double.

## LCOM4

Hitz and Montazeri (1995): the number of connected components of methods, where two methods are connected if they use a common field or one calls the other.
1 is cohesive, higher values hint at several responsibilities, 0 means that there are no methods with code.

- Constructors and initializers are ignored.
- `static final` fields, interface fields and enum constants are not shared state.
- Methods without code are no component of their own, but connect the methods that call them.
- Types are not resolved: overloads are told apart by the number of arguments, accesses through other instances (`other.x`) are not recognized, and inherited members are unknown.
