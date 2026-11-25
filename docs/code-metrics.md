# Code metrics

The ANTLR analysis adds the following metrics next to the size metrics (`sloc`, `lineCount`, ...).
The metrics are available for C, C++, C#, Go, Java, JavaScript, Kotlin, PHP, Python, Rust, Swift and TypeScript.
The definitions are originally written for Java and adapted to [the other languages](#other-languages) accordingly.

| Metric key                       | Attached to | Meaning                                                          |
| -------------------------------- | ----------- | ---------------------------------------------------------------- |
| `cyclomatic_complexity`          | function    | Cyclomatic complexity                                            |
| `cyclomatic_complexity`          | class, file | Sum over the functions (for classes: weighted methods per class) |
| `cyclomatic_complexity_weighted` | class, file | Average per function, 0 without functions                        |
| `nestedBlockDepth`               | function    | Deepest nesting of control structures                            |
| `nestedBlockDepth`               | file        | Maximum of the functions                                         |
| `npath_complexity`               | function    | Number of acyclic execution paths                                |
| `npath_complexity`               | file        | Maximum of the functions, at most 1,000,000,000                  |
| `LCOM4`                          | class       | Number of unrelated groups of methods                            |
| `LCOM4`                          | file        | Maximum of the classes                                           |
| `unmaintainability_index`        | function    | Inverse of the maintainability index, only for Java              |
| `unmaintainability_index`        | class, file | Index of the average function, 0 without functions               |

The metrics have the following properties and assumptions:
- The metrics of classes (and `LCOM4` of files) do not exist in C, whose types have no methods.
- Functions are methods and constructors.
- Abstract and native methods have a cyclomatic complexity of 1, an NPath complexity of 0 and no nested block depth and unmaintainability index.
- Lambdas belong to the function around them.
- Functions of nested, local and anonymous classes only count for their own class and for the file.
- Initializer blocks and field initializers are not exported.
- ExplorViz sums up the values of all files of a commit. Except for `cyclomatic_complexity`, such sums depend on the number of files.

The cyclomatic and NPath complexities of Java functions is matched to metrics that Checkstyle 14.4.0 reports (`CyclomaticComplexity`, `NPathComplexity`).
`CheckstyleComparisonTest` compares all functions of this repository and of `ComplexityConstructs.java`.
Checkstyle has no counterpart of the other metrics.

## Cyclomatic complexity

McCabe (1976), extended by short-circuit operators: 1 plus one for every `if`, `for`, `while`, `do`, `catch`, `?:`, `&&`, `||`, `case` and `when`.
`case 1, 2` is one `case`, `default` does not count.

## Nested block depth

The body of a function has depth 1.
`if`, loops, `switch`, `try`, `synchronized` and lambdas with a block body add a level, with or without braces.
`else if` stays on the level of its `if`.

## NPath complexity

Nejmeh (1988) as implemented by Checkstyle. Consecutive statements multiply, alternatives add up.

| Construct                      | Paths                                                                                                                                       |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `if (c) a else b`              | NP(c) + NP(a) + NP(b), without `else` NP(b) = 1                                                                                             |
| Loops                          | NP(c) + NP(body) + 1                                                                                                                        |
| `c ? a : b`                    | NP(c) + NP(a) + NP(b) + 2                                                                                                                   |
| `switch`                       | NP(selector) + 1 + sum of NP(case) - 1 + number of labels for each case, where `default` is no label                                        |
| `when g`                       | NP(g) + 1                                                                                                                                   |
| `return e`                     | NP(e), at least 1                                                                                                                           |
| `try`                          | (NP(try) + sum of NP(`catch`) + 1 for each `catch`) x NP(`finally`) + 1, where a try or `catch` block without branches and returns has NP 0 |
| Conditions and returned values | Number of `&&` and `\|\|`, two for each `?:`                                                                                                |

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

## Unmaintainability index

100 - MI, i.e. 0 is maintainable and 100 is unmaintainable, where MI is the maintainability index on the scale from 0 to 100:

```
MI = max(0, (171 - 5.2 x ln(V) - 0.23 x CC - 16.2 x ln(LOC)) x 100 / 171)
```

The formula stems from Oman and Hagemeister (1992) and Coleman et al. (1994), without the optional term for comments. Scale and limit are those of Visual Studio.

- V is the Halstead volume, CC the cyclomatic complexity and LOC the number of lines with code, i.e. without comments and blank lines. V and LOC below 1 count as 1.
- Classes and files use the averages of V, CC and LOC over their functions, as in the original definition. Functions without a body are ignored.
- V and LOC start with the return type or name of the function. Like CC, they exclude the bodies of local and anonymous classes, whose functions are measured on their own.
- The Halstead volume is N x log2(n) for N operators and operands, of which n are distinct. Operands are identifiers and literals. Operators are all other tokens, i.e. also keywords, primitive types and separators. Brackets that belong together count once, as do `?:`, `>>` and `>>>`.

As tools count operators and operands differently, values can deviate slightly from those of other tools.

## Other languages

Equivalent code has the same cyclomatic complexity, NPath complexity and nested block depth in all languages.
`ComplexityComparisonTest` compares examples of each language with Java.

### Functions

Functions are the functions that are exported, i.e. property accessors, finalizers and operators of C# are none.
Anonymous functions and those local functions that are not exported belong to the function around them.
This includes local functions of C#, Kotlin, Python and Swift.
Named functions inside of functions are exported in JavaScript, TypeScript, PHP and Rust, and hence count on their own.
A C++ method that is only declared has the metrics of an abstract method.

### Constructs

| Language      | Construct                                                                                                 | Counts as                |
| ------------- | --------------------------------------------------------------------------------------------------------- | ------------------------ |
| All           | `??`, `?:` (Kotlin), `??=`, `and`, `or`                                                                   | `&&`                     |
| All           | `switch`, `when`, `match` and `select`, whose labels are their values and patterns                        | `switch`                 |
| All           | Label `_` without guard, `else` of `when`                                                                 | `default`                |
| All           | Expression body of a function, last expression of a Rust function and only expression of a Swift function | `return`                 |
| C#, Swift     | `when` and `where` of a case, `catch` or `for`                                                            | `when`                   |
| C#, Python    | `lock`, `using`, `fixed` and `with`                                                                       | `synchronized`           |
| Kotlin, Rust  | `if` expression                                                                                           | `if`                     |
| Kotlin, Swift | Lambda with more than a single expression                                                                 | Lambda with a block body |
| Python        | `for` and `if` of a comprehension                                                                         | `when`                   |
| Python        | `else` of a loop                                                                                          | `else`                   |
| Rust          | `loop`, `while let`, `if let`                                                                             | Loop and `if`            |
| Swift         | `guard`                                                                                                   | `if`                     |
| Swift         | Comma between conditions                                                                                  | `&&`                     |

Not counted are `?.`, the `?` of Rust, `goto`, `defer` and the preprocessor of C and C++.

Of the properties of Checkstyle above, the other languages only share the 0 paths of simple functions, the additional path before a `try`, the operators in lambdas and the limit to the largest double.
Functions and types inside of functions affect neither the paths nor the operators around them.

### LCOM4

- Like constructors, these are ignored: destructors, finalizers, `__init__`, `__new__`, `__post_init__` and `__del__` (Python), `init` and `deinit` (Swift), operators (C#) and functions without `self` that return the type (Rust).
- Constants are `const` (C#, Kotlin, PHP), `static readonly` (C#, TypeScript), `static const` and `constexpr` (C++), `static let` (Swift) and upper case class attributes (Python).
- Properties with code are methods (C#, JavaScript, Kotlin, Swift, TypeScript). Overloads are only told apart in C++, C#, Kotlin and Swift.
- Every `self.x` (Python) and `this.x` (JavaScript, TypeScript) that is neither called nor a method is a field, because fields need no declaration.
- Methods of Go, Rust (`impl`) and C++ count for the type if they are in the same file, Swift extensions do not.

### Limitations

- Macro arguments (Rust) and formatted strings (Python) have no control flow, but use members.
- JSX is not analyzed.
- Constructs that the grammars do not know, e.g. `match` (Python), `let else` (Rust) and `||=` (JavaScript), are missing and can affect the code around them.
