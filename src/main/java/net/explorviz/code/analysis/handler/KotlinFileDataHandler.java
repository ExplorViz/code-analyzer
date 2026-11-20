package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for Kotlin source files. */
public class KotlinFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  private final Stack<String> classStack;
  private final Stack<String> packageStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<MethodDataHandler> globalFunctionHandlers;
  private final List<String> rootClasses;

  public KotlinFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.packageStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.globalFunctionHandlers = new ArrayList<>();
    this.rootClasses = new ArrayList<>();
  }

  public void enterPackage(final String name) {
    packageStack.push(name);
    setPackageName(getCurrentPackage());
  }

  public void leavePackage() {
    if (!packageStack.isEmpty()) {
      packageStack.pop();
    }
    setPackageName(getCurrentPackage());
  }

  public String getCurrentPackage() {
    if (packageStack.isEmpty()) {
      return "";
    }
    return String.join(".", packageStack);
  }

  /**
   * Registers a class and makes it the current class. Types with the same FQN, e.g. local classes
   * with the same name in different functions, are stored under the unique key {@code fqn#n} so
   * that neither declaration is lost. The key is used for the class stack, so members and nested
   * classes are attached to the right declaration; the emitted class name stays {@code name}.
   */
  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);

    String key = fqn;
    int suffix = 2;
    while (this.classDataMap.containsKey(key)) {
      key = fqn + "#" + suffix;
      suffix++;
    }
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
    final String packageName = getCurrentPackage();
    final String currentClassFqn = getCurrentClassFqnOrNull();

    if (currentClassFqn != null) {
      return currentClassFqn + "." + name;
    }
    if (!packageName.isEmpty()) {
      return packageName + "." + name;
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

    if (builder.getPackageName().isEmpty() && !packageStack.isEmpty()) {
      builder.setPackageName(getCurrentPackage());
    }

    builder.setLanguage(Language.KOTLIN);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder result = new StringBuilder(INITIAL_STRING_CAPACITY);

    result.append("Language: Kotlin\n");
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
