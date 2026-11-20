package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for C# source files. */
public class CSharpFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  /** Keys into {@link #classDataMap} of the enclosing types, innermost on top. */
  private final Stack<String> classStack;

  /** Fully qualified names of the enclosing types, parallel to {@link #classStack}. */
  private final Stack<String> classFqnStack;

  private final Stack<String> namespaceStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  /** Namespace of the file's first declaration, see {@link #noteDeclaration()}. */
  private String fileNamespace;

  /** First namespace declared in the file, the fallback if it declares nothing. */
  private String firstNamespace;

  public CSharpFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.classFqnStack = new Stack<>();
    this.namespaceStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
  }

  public void enterNamespace(final String name) {
    namespaceStack.push(name);
    if (firstNamespace == null) {
      firstNamespace = getCurrentNamespace();
    }
    setPackageName(getCurrentNamespace());
  }

  /**
   * Leaves the innermost namespace. While inside a namespace the package name is that namespace;
   * after the last one is left it becomes the file's namespace instead of the empty string.
   */
  public void leaveNamespace() {
    if (!namespaceStack.isEmpty()) {
      namespaceStack.pop();
    }
    final String packageName =
        namespaceStack.isEmpty() ? getFileNamespace() : getCurrentNamespace();
    if (packageName.isEmpty()) {
      builder.clearPackageName();
    } else {
      setPackageName(packageName);
    }
  }

  /**
   * Returns the namespace of the file: the namespace of its first type or function declaration, or,
   * if it has none, the first namespace it declares.
   */
  public String getFileNamespace() {
    if (fileNamespace != null) {
      return fileNamespace;
    }
    return firstNamespace != null ? firstNamespace : "";
  }

  private void noteDeclaration() {
    if (fileNamespace == null) {
      fileNamespace = getCurrentNamespace();
    }
  }

  public String getCurrentNamespace() {
    if (namespaceStack.isEmpty()) {
      return "";
    }
    return String.join(".", namespaceStack);
  }

  /**
   * Enters a type declaration. A type whose fqn is already registered, e.g. {@code Result} and
   * {@code Result<T>}, is stored under a unique key so that neither declaration is lost.
   *
   * @param name the simple name of the type
   * @param fqn the fully qualified name of the type
   */
  public void enterClass(final String name, final String fqn) {
    noteDeclaration();
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    final String key = uniqueClassKey(fqn);
    this.classDataMap.put(key, handler);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(key);
    } else {
      final ClassDataHandler parent = this.classDataMap.get(this.classStack.peek());
      if (parent != null) {
        parent.addInnerClass(key, handler);
      }
    }

    this.classStack.push(key);
    this.classFqnStack.push(fqn);
  }

  private String uniqueClassKey(final String fqn) {
    if (!classDataMap.containsKey(fqn)) {
      return fqn;
    }
    int suffix = 2;
    while (classDataMap.containsKey(fqn + "#" + suffix)) {
      suffix++;
    }
    return fqn + "#" + suffix;
  }

  public void leaveClass() {
    if (!classStack.isEmpty()) {
      this.classStack.pop();
      this.classFqnStack.pop();
    }
  }

  public String getCurrentClassFqnOrNull() {
    return classFqnStack.isEmpty() ? null : classFqnStack.peek();
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
    noteDeclaration();
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    globalFunctionHandlers.add(handler);
    return handler;
  }

  public String buildFqn(final String name) {
    final String namespace = getCurrentNamespace();
    final String currentClassFqn = getCurrentClassFqnOrNull();

    if (currentClassFqn != null) {
      return currentClassFqn + "." + name;
    }
    if (!namespace.isEmpty()) {
      return namespace + "." + name;
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

    builder.setLanguage(Language.CSHARP);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: C#\n");
    result.append("Namespace: ").append(builder.getPackageName()).append("\n");
    result.append("Usings: ").append(builder.getImportNamesList()).append("\n");
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
