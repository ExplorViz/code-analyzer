package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for TypeScript and JavaScript files. */
public class TypeScriptFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  /** Keys into {@link #classDataMap} of the enclosing types, innermost on top. */
  private final Stack<String> classStack;

  private final Map<String, ClassDataHandler> classDataMap;
  private final Map<String, String> classKeyToFqn;
  private final Stack<String> namespaceStack;

  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  private boolean inClassContext = false;
  private String fileModulePath = "";
  private String declaredModulePath = "";

  public TypeScriptFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.classKeyToFqn = new HashMap<>();
    this.namespaceStack = new Stack<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
    this.fileModulePath = deriveFileModulePath(fileName);
    if (!fileModulePath.isEmpty()) {
      setPackageName(fileModulePath);
    }
  }

  private static String deriveFileModulePath(final String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return "";
    }
    final String normalized = filePath.replace('\\', '/');
    final int lastSlash = normalized.lastIndexOf('/');
    if (lastSlash <= 0) {
      return "";
    }
    return normalized.substring(0, lastSlash).replace('/', '.');
  }

  public void enterNamespace(final String name) {
    namespaceStack.push(name);
    declaredModulePath = getCurrentModulePath();
    updatePackageName();
  }

  public void leaveNamespace() {
    if (!namespaceStack.isEmpty()) {
      namespaceStack.pop();
    }
    updatePackageName();
  }

  private void updatePackageName() {
    if (!namespaceStack.isEmpty()) {
      setPackageName(getCurrentModulePath());
    } else if (!declaredModulePath.isEmpty()) {
      setPackageName(declaredModulePath);
    } else if (!fileModulePath.isEmpty()) {
      setPackageName(fileModulePath);
    }
  }

  public String getCurrentModulePath() {
    final StringBuilder modulePath = new StringBuilder(fileModulePath);
    for (final String namespace : namespaceStack) {
      if (!modulePath.isEmpty()) {
        modulePath.append('.');
      }
      modulePath.append(namespace);
    }
    return modulePath.toString();
  }

  /** Builds a fully qualified name for a top-level or nested type, method, or function. */
  public String buildFqn(final String name) {
    final String parentClassFqn = getCurrentClassFqnOrNull();
    if (parentClassFqn != null) {
      return parentClassFqn + "." + name;
    }
    final String modulePath = getCurrentModulePath();
    if (!modulePath.isEmpty()) {
      return modulePath + "." + name;
    }
    return name;
  }

  public String buildMethodFqn(final String methodName, final List<String> parameterTypes) {
    return buildFqn(methodName)
        + "#"
        + net.explorviz.code.analysis.types.Verification.parameterHash(parameterTypes);
  }

  /**
   * Registers a type and makes it the current class. If a type with the same FQN was already
   * registered in this file (e.g. an interface and a type alias of the same name, or two same-named
   * classes in different blocks), the new type is stored under a unique key {@code fqn#n} so that
   * neither type is lost. The emitted class name is not affected.
   */
  public void enterClass(final String name, final String fqn) {
    String key = fqn;
    int suffix = 1;
    while (this.classDataMap.containsKey(key)) {
      suffix++;
      key = fqn + "#" + suffix;
    }

    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    this.classDataMap.put(key, handler);
    this.classKeyToFqn.put(key, fqn);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(key);
    } else {
      final ClassDataHandler parent = this.classDataMap.get(this.classStack.peek());
      if (parent != null) {
        parent.addInnerClass(key, handler);
      }
    }

    this.classStack.push(key);
    this.inClassContext = true;
  }

  public void leaveClass() {
    if (!classStack.isEmpty()) {
      this.classStack.pop();
    }
    this.inClassContext = !classStack.isEmpty();
  }

  /** Returns the FQN of the innermost enclosing type (without a duplicate suffix), or null. */
  public String getCurrentClassFqnOrNull() {
    return classStack.isEmpty() ? null : classKeyToFqn.get(classStack.peek());
  }

  public ClassDataHandler getCurrentClassData() {
    return classStack.isEmpty() ? null : classDataMap.get(classStack.peek());
  }

  public boolean isInClassContext() {
    return inClassContext;
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

    final Language language =
        fileName.endsWith(".ts") || fileName.endsWith(".tsx")
            ? Language.TYPESCRIPT
            : Language.JAVASCRIPT;
    builder.setLanguage(language);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result
        .append("Language: ")
        .append(fileName.endsWith(".ts") ? "TypeScript" : "JavaScript")
        .append("\n");
    result.append("Package/Module: ").append(builder.getPackageName()).append("\n");
    result.append("Imports: ").append(builder.getImportNamesList()).append("\n");
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
