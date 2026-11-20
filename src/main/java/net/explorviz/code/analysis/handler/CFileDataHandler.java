package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for C source files. */
public class CFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  /** Keys (into {@link #classDataMap}) of the types currently being visited. */
  private final Stack<String> classStack;

  /** Types by unique key; the key is the FQN, or FQN#n for a repeated FQN. */
  private final Map<String, ClassDataHandler> classDataMap;

  /** The FQN of each type key in {@link #classDataMap}. */
  private final Map<String, String> classFqns;

  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  public CFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.classDataMap = new LinkedHashMap<>();
    this.classFqns = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
  }

  /**
   * Enters a type definition. A type whose FQN is already registered in this file (e.g. variants in
   * {@code #if}/{@code #else} branches) is kept as a separate type under a unique key.
   */
  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    final String key = uniqueClassKey(fqn);
    this.classDataMap.put(key, handler);
    this.classFqns.put(key, fqn);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(key);
    } else {
      final ClassDataHandler parent = this.classDataMap.get(this.classStack.peek());
      if (parent != null) {
        parent.addInnerClass(key, handler);
      }
    }

    this.classStack.push(key);
  }

  private String uniqueClassKey(final String fqn) {
    if (!this.classDataMap.containsKey(fqn)) {
      return fqn;
    }
    int suffix = 2;
    while (this.classDataMap.containsKey(fqn + "#" + suffix)) {
      suffix++;
    }
    return fqn + "#" + suffix;
  }

  public void leaveClass() {
    if (!classStack.isEmpty()) {
      this.classStack.pop();
    }
  }

  /** Returns the FQN of the type currently being visited, or null outside of types. */
  public String getCurrentClassFqnOrNull() {
    return classStack.isEmpty() ? null : classFqns.get(classStack.peek());
  }

  public ClassDataHandler getCurrentClassData() {
    return classStack.isEmpty() ? null : classDataMap.get(classStack.peek());
  }

  @Override
  public int getClassCount() {
    return classDataMap.size();
  }

  public boolean isInClassContext() {
    return !classStack.isEmpty();
  }

  public MethodDataHandler addGlobalFunction(final String name, final String returnType) {
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    globalFunctionHandlers.add(handler);
    return handler;
  }

  @Override
  public FileData getProtoBufObject() {
    builder.clearClasses();
    for (final String rootClassFqn : rootClasses) {
      final ClassDataHandler handler = classDataMap.get(rootClassFqn);
      if (handler != null) {
        builder.addClasses(handler.getProtoBufObject());
      }
    }

    builder.clearFunctions();
    for (final MethodDataHandler handler : globalFunctionHandlers) {
      builder.addFunctions(handler.getProtoBufObject());
    }

    builder.setLanguage(Language.C);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: C\n");
    result.append("Includes: ").append(builder.getImportNamesList()).append("\n");
    result.append("Structs/Enums: ").append(classDataMap.size()).append("\n");
    result.append("Global Functions: ").append(globalFunctionHandlers.size()).append("\n");

    for (final Map.Entry<String, ClassDataHandler> entry : classDataMap.entrySet()) {
      result.append("  Type ").append(entry.getKey()).append(":\n");
      result.append(entry.getValue().toString()).append("\n");
    }

    if (!globalFunctionHandlers.isEmpty()) {
      result.append("Global Functions:\n");
      for (final MethodDataHandler handler : globalFunctionHandlers) {
        result.append("  - ").append(handler.getProtoBufObject().getName()).append("\n");
      }
    }

    for (final Map.Entry<String, Double> entry : builder.getMetricsMap().entrySet()) {
      result.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
    }

    return result.toString();
  }
}
