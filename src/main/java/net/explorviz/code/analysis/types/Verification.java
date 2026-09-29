package net.explorviz.code.analysis.types;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import java.util.ArrayList;
import java.util.List;

/** Class to hold some hashing methods. */
public final class Verification {

  private Verification() {}

  /**
   * Calculates the hash for a parameter list provided as String List.
   *
   * @param list a list of Types
   * @return the hash of the types as hexadecimal string
   */
  public static String parameterHash(final List<String> list) {
    return Integer.toHexString(list.hashCode());
  }

  /**
   * Calculates the hash for a parameter list provided as {@link NodeList}.
   *
   * @param parameterList a list of Parameters
   * @return the hash of the parameters as hexadecimal string
   */
  public static String parameterHash(final NodeList<Parameter> parameterList) {
    final List<String> tempList = new ArrayList<>();
    for (final Parameter parameter : parameterList) {
      tempList.add(parameter.getType().asString());
    }
    return Integer.toHexString(tempList.hashCode());
  }
}
