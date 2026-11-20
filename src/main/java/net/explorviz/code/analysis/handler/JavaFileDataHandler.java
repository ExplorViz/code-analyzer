package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** FileData handler for Java files. */
public class JavaFileDataHandler extends AbstractFileDataHandler
    implements ProtoBufConvertable<FileData> {

  private final Stack<String> classStack;
  private final Stack<String> methodStack;
  private final Map<String, ClassDataHandler> classDataMap;
  private final List<String> rootClasses;

  public JavaFileDataHandler(final String fileName) {
    super(fileName);
    this.classStack = new Stack<>();
    this.methodStack = new Stack<>();
    this.classDataMap = new HashMap<>();
    this.rootClasses = new ArrayList<>();
  }

  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    registerClass(fqn, handler);
  }

  public void enterAnonymousClass(final String name, final String fqn) {
    final ClassDataHandler classDataHandler = new ClassDataHandler();
    classDataHandler.setName(name);
    classDataHandler.setIsAnonymousClass();
    registerClass(fqn, classDataHandler);
  }

  /**
   * Registers a class and makes it the current class. Local classes with the same name in different
   * methods share an FQN; such a class is stored under the unique key {@code fqn#n} so that neither
   * declaration is lost. The key is used for the class stack, so members and nested classes are
   * attached to the right declaration.
   */
  private void registerClass(final String fqn, final ClassDataHandler handler) {
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
      final ClassDataHandler parent = this.getClassData(this.classStack.peek());
      if (parent != null) {
        parent.addInnerClass(key, handler);
      }
    }
    this.classStack.push(key);
  }

  public String getCurrentClassFqn() {
    if (classStack.isEmpty()) {
      throw new IllegalStateException("Class stack is empty. Cannot get current class FQN.");
    }
    return this.classStack.peek();
  }

  public ClassDataHandler getClassData(final String className) {
    return this.classDataMap.get(className);
  }

  public ClassDataHandler getCurrentClassData() {
    return this.classDataMap.get(getCurrentClassFqn());
  }

  public void leaveClass() {
    if (classStack.isEmpty()) {
      throw new IllegalStateException("Class stack is empty. Cannot pop current class FQN.");
    }
    this.classStack.pop();
  }

  public void leaveAnonymousClass() {
    leaveClass();
  }

  @Override
  public int getClassCount() {
    return classDataMap.size();
  }

  public int getMethodCount() {
    int methodCount = 0;
    for (final Map.Entry<String, ClassDataHandler> entry : this.classDataMap.entrySet()) {
      methodCount += entry.getValue().getMethodCount();
    }
    return methodCount;
  }

  public String getCurrentMethodFqn() {
    return methodStack.peek();
  }

  public void enterMethod(final String methodFqn) {
    methodStack.push(methodFqn);
  }

  public void leaveMethod() {
    if (!methodStack.isEmpty()) {
      methodStack.pop();
    }
  }

  public boolean isInClassContext() {
    return !classStack.isEmpty();
  }

  public boolean isInMethodContext() {
    return !methodStack.isEmpty();
  }

  public String getCurrentClassFqnOrNull() {
    return classStack.isEmpty() ? null : classStack.peek();
  }

  public Map<String, Double> getMetrics() {
    return builder.getMetricsMap();
  }

  public List<String> getImportNames() {
    return this.builder.getImportNamesList();
  }

  @Override
  public FileData getProtoBufObject() {
    this.builder.clearClasses();
    for (final String rootClassName : this.rootClasses) {
      final ClassDataHandler handler = this.classDataMap.get(rootClassName);
      if (handler != null) {
        this.builder.addClasses(handler.getProtoBufObject());
      }
    }

    // Set language
    builder.setLanguage(Language.JAVA);

    return builder.build();
  }

  @Override
  public String toString() {
    final int INITIAL_STRING_CAPACITY = 500;
    final StringBuilder mapData = new StringBuilder(INITIAL_STRING_CAPACITY);
    for (final Map.Entry<String, ClassDataHandler> entry : this.classDataMap.entrySet()) {
      mapData.append(entry.getKey()).append(": \n");
      mapData.append(entry.getValue().toString());
      mapData.append('\n');
    }
    for (final Map.Entry<String, Double> entry : this.builder.getMetricsMap().entrySet()) {
      mapData.append(entry.getKey()).append(": ");
      mapData.append(entry.getValue()).append('\n');
    }
    return "stats: methodCount="
        + this.getMethodCount()
        + "\n"
        + "package: "
        + this.builder.getPackageName()
        + "\n"
        + "imports: "
        + this.builder.getImportNamesList()
        + "\n"
        + mapData;
  }
}
