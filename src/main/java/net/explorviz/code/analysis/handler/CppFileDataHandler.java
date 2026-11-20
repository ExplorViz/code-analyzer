package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for C++ files. */
public class CppFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  /** Keys (into {@link #classDataMap}) of the classes currently being visited. */
  private final Stack<String> classStack;

  private final Stack<String> namespaceStack;

  /** Classes by unique key; the key is the FQN, or FQN#n for a repeated FQN. */
  private final Map<String, ClassDataHandler> classDataMap;

  /** The FQN of each class key in {@link #classDataMap}. */
  private final Map<String, String> classFqns;

  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;
  private final boolean isCppFile;

  public CppFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.namespaceStack = new Stack<>();
    this.classDataMap = new LinkedHashMap<>();
    this.classFqns = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
    this.isCppFile =
        fileName.endsWith(".cpp")
            || fileName.endsWith(".cxx")
            || fileName.endsWith(".cc")
            || fileName.endsWith(".hpp")
            || fileName.endsWith(".hxx");
  }

  /**
   * Enters a namespace. The first top-level namespace of the file becomes its package name; nested
   * and later namespaces do not override it.
   */
  public void enterNamespace(final String name) {
    namespaceStack.push(name);
    if (namespaceStack.size() == 1 && builder.getPackageName().isEmpty()) {
      setPackageName(name);
    }
  }

  public void leaveNamespace() {
    if (!namespaceStack.isEmpty()) {
      namespaceStack.pop();
    }
  }

  /** Returns the current namespace as a '::' separated string. */
  public String getCurrentNamespace() {
    if (namespaceStack.isEmpty()) {
      return "";
    }
    return String.join("::", namespaceStack);
  }

  /**
   * Enters a class. A class whose FQN is already registered in this file (e.g. variants in {@code
   * #if}/{@code #else} branches) is kept as a separate class under a unique key.
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

  /** Returns the FQN of the class currently being visited, or null outside of classes. */
  public String getCurrentClassFqnOrNull() {
    return classStack.isEmpty() ? null : classFqns.get(classStack.peek());
  }

  public ClassDataHandler getCurrentClassData() {
    return classStack.isEmpty() ? null : classDataMap.get(classStack.peek());
  }

  /**
   * Finds a class of this file for the qualifier of an out-of-line member definition (e.g. {@code
   * Widget} for {@code void Widget::draw()}). The qualifier is resolved relative to the current
   * namespace first, then as a FQN, then as a suffix of a known FQN. If several classes share the
   * FQN, the first one is returned.
   *
   * @param qualifier the class qualifier, '::' separated
   * @return the class, or null if it is not defined in this file
   */
  public ClassDataHandler findClassData(final String qualifier) {
    final String namespace = getCurrentNamespace();
    if (!namespace.isEmpty()) {
      final ClassDataHandler inNamespace = classDataMap.get(namespace + "::" + qualifier);
      if (inNamespace != null) {
        return inNamespace;
      }
    }
    final ClassDataHandler exact = classDataMap.get(qualifier);
    if (exact != null) {
      return exact;
    }
    for (final Map.Entry<String, String> entry : classFqns.entrySet()) {
      if (entry.getValue().endsWith("::" + qualifier)) {
        return classDataMap.get(entry.getKey());
      }
    }
    return null;
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
