package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for Go source files. */
public class GoFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  private final Stack<String> classStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  public GoFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
  }

  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    // A second type with the same fqn (e.g. a class redefined in an if/else) must not replace
    // the first one, so it is registered under a unique key.
    final String key = uniqueClassKey(fqn);
    this.classDataMap.put(key, handler);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(key);
    } else {
      final String parentClassFqn = this.classStack.peek();
      final ClassDataHandler parent = this.classDataMap.get(parentClassFqn);
      if (parent != null) {
        parent.addInnerClass(key, handler);
      }
    }

    this.classStack.push(key);
  }

  /** Returns {@code fqn}, or {@code fqn#n} with the smallest free {@code n >= 2}. */
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

  public String getCurrentClassFqnOrNull() {
    return classStack.isEmpty() ? null : classStack.peek();
  }

  public ClassDataHandler getCurrentClassData() {
    final String fqn = getCurrentClassFqnOrNull();
    return fqn != null ? classDataMap.get(fqn) : null;
  }

  public ClassDataHandler getClassData(final String fqn) {
    return classDataMap.get(fqn);
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

  public String buildFqn(final String name) {
    final String pkg = getPackageName();
    if (pkg != null && !pkg.isEmpty()) {
      return pkg + "." + name;
    }
    return name;
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

    builder.setLanguage(Language.GO);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: Go\n");
    result.append("Package: ").append(builder.getPackageName()).append("\n");
    result.append("Imports: ").append(builder.getImportNamesList()).append("\n");
    result.append("Types: ").append(classDataMap.size()).append("\n");
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
