package net.explorviz.code.analysis.metrics.java.lcom4;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.explorviz.code.analysis.antlr.generated.java.JavaParser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Fields and methods that a type declares itself. Constants ({@code static final} fields, fields of
 * interfaces and enum constants) are not part of the type's state, but still hide outer variables.
 */
/* package */ final class ClassMembers {

  private final Set<String> stateFields = new HashSet<>();
  private final Set<String> fieldNames = new HashSet<>();
  private final Set<String> recordComponents = new HashSet<>();
  private final List<Method> methods = new ArrayList<>();
  private String typeName;

  /** Collects the members of a type declaration or anonymous class. */
  /* package */ static ClassMembers of(final ParserRuleContext type) {
    final ClassMembers members = new ClassMembers();
    switch (type) {
      case JavaParser.ClassDeclarationContext ctx -> {
        members.setTypeName(ctx.identifier());
        members.addClassBody(ctx.classBody());
      }
      case JavaParser.ClassCreatorRestContext ctx -> members.addClassBody(ctx.classBody());
      case JavaParser.EnumConstantContext ctx -> members.addClassBody(ctx.classBody());
      case JavaParser.EnumDeclarationContext ctx -> {
        members.setTypeName(ctx.identifier());
        members.addEnum(ctx);
      }
      case JavaParser.RecordDeclarationContext ctx -> {
        members.setTypeName(ctx.identifier());
        members.addRecord(ctx);
      }
      case JavaParser.InterfaceDeclarationContext ctx -> {
        members.setTypeName(ctx.identifier());
        members.addInterface(ctx);
      }
      default -> {
        // Other types, i.e. annotation types, have no methods with code.
      }
    }
    return members;
  }

  /** The simple name of the type, {@code null} for anonymous classes. */
  /* package */ String typeName() {
    return typeName;
  }

  /* package */ List<Method> methods() {
    return methods;
  }

  /** The names of all fields including constants. */
  /* package */ Set<String> fieldNames() {
    return fieldNames;
  }

  /* package */ boolean isStateField(final String name) {
    return stateFields.contains(name);
  }

  /** Whether the name is a record component, which has an implicit accessor method. */
  /* package */ boolean isRecordComponent(final String name) {
    return recordComponents.contains(name);
  }

  /* package */ boolean declaresMethod(final String name) {
    return methods.stream().anyMatch(method -> method.name().equals(name));
  }

  private void setTypeName(final JavaParser.IdentifierContext identifier) {
    if (identifier != null) {
      typeName = identifier.getText();
    }
  }

  private void addClassBody(final JavaParser.ClassBodyContext body) {
    if (body != null) {
      body.classBodyDeclaration().forEach(this::addClassMember);
    }
  }

  private void addEnum(final JavaParser.EnumDeclarationContext ctx) {
    if (ctx.enumConstants() != null) {
      for (final JavaParser.EnumConstantContext constant : ctx.enumConstants().enumConstant()) {
        if (constant.identifier() != null) {
          fieldNames.add(constant.identifier().getText());
        }
      }
    }
    if (ctx.enumBodyDeclarations() != null) {
      ctx.enumBodyDeclarations().classBodyDeclaration().forEach(this::addClassMember);
    }
  }

  private void addRecord(final JavaParser.RecordDeclarationContext ctx) {
    if (ctx.recordHeader() != null && ctx.recordHeader().recordComponentList() != null) {
      for (final JavaParser.RecordComponentContext component :
          ctx.recordHeader().recordComponentList().recordComponent()) {
        if (component.identifier() != null) {
          final String name = component.identifier().getText();
          recordComponents.add(name);
          addField(name, false);
        }
      }
    }
    if (ctx.recordBody() != null) {
      ctx.recordBody().classBodyDeclaration().forEach(this::addClassMember);
    }
  }

  private void addInterface(final JavaParser.InterfaceDeclarationContext ctx) {
    if (ctx.interfaceBody() == null) {
      return;
    }
    for (final JavaParser.InterfaceBodyDeclarationContext declaration :
        ctx.interfaceBody().interfaceBodyDeclaration()) {
      final JavaParser.InterfaceMemberDeclarationContext member =
          declaration.interfaceMemberDeclaration();
      if (member == null) {
        continue;
      }
      if (member.interfaceMethodDeclaration() != null) {
        addInterfaceMethod(member.interfaceMethodDeclaration().interfaceCommonBodyDeclaration());
      } else if (member.genericInterfaceMethodDeclaration() != null) {
        addInterfaceMethod(
            member.genericInterfaceMethodDeclaration().interfaceCommonBodyDeclaration());
      } else if (member.constDeclaration() != null) {
        for (final JavaParser.ConstantDeclaratorContext constant :
            member.constDeclaration().constantDeclarator()) {
          if (constant.identifier() != null) {
            addField(constant.identifier().getText(), true);
          }
        }
      }
    }
  }

  private void addInterfaceMethod(final JavaParser.InterfaceCommonBodyDeclarationContext ctx) {
    if (ctx != null && ctx.identifier() != null) {
      methods.add(new Method(ctx.identifier().getText(), ctx.formalParameters(), ctx.methodBody()));
    }
  }

  private void addClassMember(final JavaParser.ClassBodyDeclarationContext declaration) {
    final JavaParser.MemberDeclarationContext member = declaration.memberDeclaration();
    if (member == null) {
      return;
    }
    if (member.methodDeclaration() != null) {
      addMethod(member.methodDeclaration());
    } else if (member.genericMethodDeclaration() != null) {
      addMethod(member.genericMethodDeclaration().methodDeclaration());
    } else if (member.fieldDeclaration() != null) {
      addFields(member.fieldDeclaration(), isConstant(declaration.modifier()));
    }
  }

  private void addMethod(final JavaParser.MethodDeclarationContext ctx) {
    if (ctx != null && ctx.identifier() != null) {
      methods.add(new Method(ctx.identifier().getText(), ctx.formalParameters(), ctx.methodBody()));
    }
  }

  private void addFields(final JavaParser.FieldDeclarationContext ctx, final boolean constant) {
    if (ctx.variableDeclarators() == null) {
      return;
    }
    for (final JavaParser.VariableDeclaratorContext declarator :
        ctx.variableDeclarators().variableDeclarator()) {
      final JavaParser.VariableDeclaratorIdContext id = declarator.variableDeclaratorId();
      if (id != null && id.identifier() != null) {
        addField(id.identifier().getText(), constant);
      }
    }
  }

  private void addField(final String name, final boolean constant) {
    fieldNames.add(name);
    if (!constant) {
      stateFields.add(name);
    }
  }

  private static boolean isConstant(final List<JavaParser.ModifierContext> modifiers) {
    return hasModifier(modifiers, "static") && hasModifier(modifiers, "final");
  }

  private static boolean hasModifier(
      final List<JavaParser.ModifierContext> modifiers, final String name) {
    return modifiers.stream().anyMatch(modifier -> name.equals(modifier.getText()));
  }
}
