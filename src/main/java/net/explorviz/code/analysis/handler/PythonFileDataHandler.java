package net.explorviz.code.analysis.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import net.explorviz.code.proto.FileData;
import net.explorviz.code.proto.Language;

/** File data handler specifically for Python files. */
public class PythonFileDataHandler extends AbstractFileDataHandler {

  private final Stack<String> classStack = new Stack<>();
  private final Map<String, ClassDataHandler> classDataMap = new HashMap<>();
  private final Stack<String> methodStack = new Stack<>();
  private final List<MethodDataHandler> globalFunctionHandlers = new ArrayList<>();
  private final List<String> rootClasses = new ArrayList<>();

  public PythonFileDataHandler(final String fileName) {
    super(fileName);
    builder.setLanguage(Language.PYTHON);
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

  public ClassDataHandler getCurrentClassData() {
    if (classStack.isEmpty()) {
      return null;
    }
    return classDataMap.get(classStack.peek());
  }

  public String getCurrentClassFqn() {
    if (classStack.isEmpty()) {
      return null;
    }
    return classStack.peek();
  }

  public String getCurrentMethodFqn() {
    if (methodStack.isEmpty()) {
      return null;
    }
    return methodStack.peek();
  }

  public MethodDataHandler addGlobalFunction(final String name, final String returnType) {
    final MethodDataHandler handler = new MethodDataHandler(name, returnType);
    globalFunctionHandlers.add(handler);
    return handler;
  }

  @Override
  public int getClassCount() {
    return classDataMap.size();
  }

  @Override
  public FileData getProtoBufObject() {
    // Add all root classes
    builder.clearClasses();
    for (final String rootClassFqn : rootClasses) {
      final ClassDataHandler handler = classDataMap.get(rootClassFqn);
      if (handler != null) {
        builder.addClasses(handler.getProtoBufObject());
      }
    }

    // Add all global functions
    builder.clearFunctions();
    for (final MethodDataHandler handler : globalFunctionHandlers) {
      builder.addFunctions(handler.getProtoBufObject());
    }

    return builder.build();
  }
}
