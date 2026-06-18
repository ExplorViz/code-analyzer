package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/**
 * FileData handler for C++ files.
 */
public class CppFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  private final Stack<String> classStack;
  private final Stack<String> namespaceStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;
  private final boolean isCppFile;

  public CppFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.namespaceStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
    this.isCppFile = fileName.endsWith(".cpp") || fileName.endsWith(".cxx")
        || fileName.endsWith(".cc") || fileName.endsWith(".hpp") || fileName.endsWith(".hxx");
  }

  public void enterNamespace(final String name) {
    namespaceStack.push(name);
  }

  public void leaveNamespace() {
    if (!namespaceStack.isEmpty()) {
      namespaceStack.pop();
    }
  }

  /**
   * Returns the current namespace as a '::' separated string.
   */
  public String getCurrentNamespace() {
    if (namespaceStack.isEmpty()) {
      return "";
    }
    return String.join("::", namespaceStack);
  }

  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    this.classDataMap.put(fqn, handler);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(fqn);
    } else {
      final String parentClassFqn = this.classStack.peek();
      final ClassDataHandler parent = this.classDataMap.get(parentClassFqn);
      if (parent != null) {
        parent.addInnerClass(fqn, handler);
      }
    }

    this.classStack.push(fqn);
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

  public boolean isInClassContext() {
    return !classStack.isEmpty();
  }

  public MethodDataHandler addGlobalFunction(final String name, final String returnType) {
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    globalFunctionHandlers.add(handler);
    return handler;
  }

  public int getGlobalFunctionCount() {
    return globalFunctionHandlers.size();
  }

  @Override
  public int getClassCount() {
    return classDataMap.size();
  }

  public int getTotalFunctionCount() {
    int count = globalFunctionHandlers.size();
    for (final ClassDataHandler classData : classDataMap.values()) {
      count += classData.getMethodCount();
    }
    return count;
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

    // Set namespace as package name
    if (builder.getPackageName().isEmpty() && !namespaceStack.isEmpty()) {
      builder.setPackageName(getCurrentNamespace());
    }

    builder.setLanguage(Language.CPP);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: C/C++\n");
    result.append("Namespace: ").append(builder.getPackageName()).append("\n");
    result.append("Includes: ").append(builder.getImportNamesList()).append("\n");
    result.append("Classes: ").append(classDataMap.size()).append("\n");
    result.append("Global Functions: ").append(globalFunctionHandlers.size()).append("\n");

    for (final Map.Entry<String, ClassDataHandler> entry : classDataMap.entrySet()) {
      result.append("  Class ").append(entry.getKey()).append(":\n");
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
