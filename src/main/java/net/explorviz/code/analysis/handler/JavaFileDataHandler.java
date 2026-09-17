package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.ClassData;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;
import java.util.List;

/**
 * FileData handler for Java files.
 */
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

  public void updateClassWithStaticDeps(
      final String className,
      final List<String> superClasses,
      final List<String> interfaces) {
    System.out.println("updateClassWithStaticDeps called for: " + className);
    System.out.println("superClasses: " + superClasses);
    System.out.println("interfaces: " + interfaces);
    final ClassDataHandler handler = getClassData(className);
    if (handler != null) {
      if (!superClasses.isEmpty()) {
        handler.setSuperClass(superClasses.get(0));
      }
      interfaces.forEach(handler::addImplementedInterface);
    }
  }
  public void enterClass(final String name, final String fqn) {
    final ClassDataHandler handler = new ClassDataHandler();
    handler.setName(name);
    this.classDataMap.put(fqn, handler);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(fqn);
    } else {
      final String parentClassName = this.classStack.peek();
      final ClassDataHandler parent = this.getClassData(parentClassName);
      if (parent != null) {
        parent.addInnerClass(fqn, handler);
      }
    }
    this.classStack.push(fqn);
  }

  public void enterAnonymousClass(final String name, final String fqn) {
    final ClassDataHandler classDataHandler = new ClassDataHandler();
    classDataHandler.setName(name);
    classDataHandler.setIsAnonymousClass();
    this.classDataMap.put(fqn, classDataHandler);

    if (this.classStack.isEmpty()) {
      this.rootClasses.add(fqn);
    } else {
      final String parentClassName = this.classStack.peek();
      final ClassDataHandler parent = this.getClassData(parentClassName);
      if (parent != null) {
        parent.addInnerClass(fqn, classDataHandler);
      }
    }
    this.classStack.push(fqn);
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
    methodStack.pop();
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
    return "stats: methodCount=" + this.getMethodCount() + "\n" + "package: "
        + this.builder.getPackageName() + "\n" + "imports: " + this.builder.getImportNamesList()
        + "\n" + mapData;
  }

  //statische Abhängigkeiten
  /**
   * Registers an outgoing method call from a method in the given class to a target class.
   *
   * @param classFqn        the fully qualified name of the calling class
   * @param methodSimpleName the simple name of the calling method
   * @param targetFqn       the target class name being called
   */
  public void addOutgoingMethodCall(final String classFqn, final String methodSimpleName,
      final String targetFqn) {
    final ClassDataHandler handler = getClassData(classFqn);
    if (handler != null) {
      handler.addOutgoingMethodCallToMethod(methodSimpleName, targetFqn);
    }
  }

  public void clearImports() {
    this.builder.clearImportNames();
  }

  /**
   * Entfernt die vom (parallelen ANTLR-basierten) Struktur-Parser bereits gesetzten
   * EXTENDS/IMPLEMENTS/Field-Daten für eine Klasse, falls diese Klasse laut
   * Package-Filter nicht analysiert werden soll.
   *
   * @param classFqn die fully qualified name der Klasse
   */
  public void clearStaticDepsForClass(final String classFqn) {
    final ClassDataHandler handler = getClassData(classFqn);
    if (handler != null) {
      handler.clearSuperClass();
      handler.clearImplementedInterfaces();
      handler.clearFields();
    }
  }

  public void addFieldType(final String classFqn, final String fieldType) {
    final ClassDataHandler handler = getClassData(classFqn);
    if (handler != null) {
      handler.addFieldType(fieldType);
    }
  }


}
