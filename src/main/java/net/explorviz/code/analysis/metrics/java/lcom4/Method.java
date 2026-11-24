package net.explorviz.code.analysis.metrics.java.lcom4;

import java.util.ArrayList;
import java.util.List;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;

/**
 * A method declared by a class. Compared by identity since signatures are not resolved.
 *
 * <p>{@code body} is {@code null} for methods without a body.
 */
/* package */ final class Method {

  private final String name;
  private final JavaParser.FormalParametersContext parameters;
  private final JavaParser.BlockContext body;
  private final int parameterCount;
  private final boolean varargs;

  /* package */ Method(
      final String name,
      final JavaParser.FormalParametersContext parameters,
      final JavaParser.MethodBodyContext methodBody) {
    this.name = name;
    this.parameters = parameters;
    this.body = methodBody == null ? null : methodBody.block();
    final List<JavaParser.FormalParameterContext> declared = declaredParameters(parameters);
    this.parameterCount = declared.size();
    this.varargs = !declared.isEmpty() && isVarargs(declared.getLast());
  }

  /* package */ String name() {
    return name;
  }

  /* package */ JavaParser.FormalParametersContext parameters() {
    return parameters;
  }

  /* package */ JavaParser.BlockContext body() {
    return body;
  }

  /** Whether the method has code. Abstract and empty methods cannot be cohesive with others. */
  /* package */ boolean hasCode() {
    return body != null && !body.blockStatement().isEmpty();
  }

  /* package */ boolean accepts(final int argumentCount) {
    return varargs ? argumentCount >= parameterCount - 1 : argumentCount == parameterCount;
  }

  private static List<JavaParser.FormalParameterContext> declaredParameters(
      final JavaParser.FormalParametersContext parameters) {
    final List<JavaParser.FormalParameterContext> declared = new ArrayList<>();
    if (parameters == null) {
      return declared;
    }
    if (parameters.formalParameter() != null) {
      declared.add(parameters.formalParameter());
    }
    for (final JavaParser.FormalParameterListContext list : parameters.formalParameterList()) {
      declared.addAll(list.formalParameter());
    }
    return declared;
  }

  private static boolean isVarargs(final JavaParser.FormalParameterContext parameter) {
    return parameter.ELLIPSIS() != null;
  }
}
