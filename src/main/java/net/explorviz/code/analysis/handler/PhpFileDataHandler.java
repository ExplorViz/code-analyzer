package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for PHP source files. */
public class PhpFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  private final Stack<String> classStack;
  private final Stack<String> namespaceStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  public PhpFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.namespaceStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
  }

  /**
   * Enters a braced namespace ({@code namespace A { ... }}). The first namespace of the file
   * becomes its package name and stays so after the namespace block is left.
   */
  public void enterNamespace(final String name) {
    namespaceStack.push(name);
    recordPackageName();
  }

  public void leaveNamespace() {
    if (!namespaceStack.isEmpty()) {
      namespaceStack.pop();
    }
  }

  /**
   * Sets the namespace declared by an unbraced declaration ({@code namespace A;}), which applies
   * until the next namespace declaration or the end of the file.
   */
  public void setNamespace(final String name) {
    namespaceStack.clear();
    namespaceStack.push(name);
    recordPackageName();
  }

  private void recordPackageName() {
    if (getPackageName().isEmpty()) {
      setPackageName(getCurrentNamespace());
    }
  }

  public String getCurrentNamespace() {
    if (namespaceStack.isEmpty()) {
      return "";
    }
    return String.join(".", namespaceStack);
  }

  /** Enters a named class, interface, trait or enum. */
  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);

    // Two types with the same fqn (e.g. conditional declarations) must not replace each other.
    final String key = uniqueKey(fqn);
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

  /**
   * Enters an anonymous class ({@code new class { ... }}). It becomes an inner class of the
   * enclosing class, or a root class at file level, so that its members are not attributed to the
   * enclosing class.
   */
  public void enterAnonymousClass(final String name) {
    enterClass(name, buildFqn(name));
    getCurrentClassData().setIsAnonymousClass();
  }

  private String uniqueKey(final String fqn) {
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
    }
  }

  public ClassDataHandler getCurrentClassData() {
    if (classStack.isEmpty()) {
      return null;
    }
    return classDataMap.get(classStack.peek());
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
    final String namespace = getCurrentNamespace();
    final String currentClassFqn = classStack.isEmpty() ? null : classStack.peek();

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

    builder.setLanguage(Language.PHP);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: PHP\n");
    result.append("Namespace: ").append(builder.getPackageName()).append("\n");
    result.append("Uses: ").append(builder.getImportNamesList()).append("\n");
    result.append("Types: ").append(classDataMap.size()).append("\n");
    result.append("Global Functions: ").append(globalFunctionHandlers.size()).append("\n");

    for (final Map.Entry<String, ClassDataHandler> entry : classDataMap.entrySet()) {
      result.append("  Type ").append(entry.getKey()).append(":\n");
      result.append(entry.getValue().toString()).append("\n");
    }

    return result.toString();
  }
}
