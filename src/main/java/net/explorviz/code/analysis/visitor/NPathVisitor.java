package net.explorviz.code.analysis.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import net.explorviz.code.analysis.handler.JavaFileDataHandler;

/**
 * Visitor filling a FileData object with NPath data. Only handles constructor and methods bodies.
 * This implementation is a reimplementation of the {@code NPathBaseVisitor} from PMD.
 */
public class NPathVisitor extends GenericVisitorAdapter<Integer, JavaFileDataHandler> {

  @Override
  public Integer visit(final ConstructorDeclaration n, final JavaFileDataHandler data) {
    return super.visit(n, data);
  }

  @Override
  public Integer visit(final MethodDeclaration n, final JavaFileDataHandler data) {
    int ret;
    if (n.getBody().isPresent()) {
      ret = visit(n.getBody().get(), data);
    } else {
      ret = 1;
    }
    return ret;
  }

  @Override
  public Integer visit(final BlockStmt n, final JavaFileDataHandler data) {
    int product = 1;
    for (final Node node : n.getChildNodes()) {
      // Skip comment
      if (node instanceof Comment) {
        continue;
      }
      final Integer childComplexity = node.accept(this, data);
      if (childComplexity != null) {
        product *= childComplexity;
      }
    }

    return product;
  }

  @Override
  public Integer visit(final AssignExpr n, final JavaFileDataHandler arg) {
    return 1;
  }

  @Override
  public Integer visit(final UnaryExpr n, final JavaFileDataHandler arg) {
    return 1;
  }

  @Override
  public Integer visit(final MethodCallExpr n, final JavaFileDataHandler arg) {
    return super.visit(n, arg);
  }

  @Override
  public Integer visit(final ExpressionStmt n, final JavaFileDataHandler arg) {
    return super.visit(n, arg);
  }

  @Override
  public Integer visit(final IfStmt n, final JavaFileDataHandler data) {
    // (npath of if + npath of else (or 1) + bool_comp of if) * npath of next
    int complexity = n.hasElseBlock() ? 0 : 1;
    // filter for statements
    complexity += n.getThenStmt().accept(this, data);
    if (n.hasElseBlock() && n.getElseStmt().isPresent()) {
      complexity += n.getElseStmt().get().accept(this, data);
    }

    final int boolCompIf = booleanExpressionComplexity(n.getCondition());
    return boolCompIf + complexity;
  }

  @Override
  public Integer visit(final WhileStmt n, final JavaFileDataHandler data) {
    // (npath of while + bool_comp of while + 1) * npath of next
    final int boolCompWhile = booleanExpressionComplexity(n.getCondition());

    final int nPathWhile = n.getBody().accept(this, data);

    return boolCompWhile + nPathWhile + 1;
  }

  @Override
  public Integer visit(final DoStmt n, final JavaFileDataHandler data) {
    // (npath of do + bool_comp of do + 1) * npath of next
    final int boolCompDo = booleanExpressionComplexity(n.getCondition());

    final int nPathDo = n.getBody().accept(this, data);

    return boolCompDo + nPathDo + 1;
  }

  @Override
  public Integer visit(final ForStmt n, final JavaFileDataHandler data) {
    // (npath of for + bool_comp of for + 1) * npath of next
    int boolCompForInit = 0;
    for (final Expression e : n.getInitialization()) {
      boolCompForInit += booleanExpressionComplexity(e);
    }
    int boolCompForCompare = 0;
    if (n.getCompare().isPresent()) {
      boolCompForCompare = booleanExpressionComplexity(n.getCompare().get());
    }
    int boolCompForUpdate = 0;
    for (final Expression e : n.getUpdate()) {
      boolCompForUpdate += booleanExpressionComplexity(e);
    }
    final int nPathFor = n.getBody().accept(this, data);

    return boolCompForInit + boolCompForCompare + boolCompForUpdate + nPathFor + 1;
  }

  @Override
  public Integer visit(final ReturnStmt n, final JavaFileDataHandler data) {
    // return statements are valued at 1, or the value of the boolean expression
    if (n.getExpression().isEmpty()) {
      return 1;
    }
    int boolCompReturn = booleanExpressionComplexity(n.getExpression().get());
    // int conditionalExpressionComplexity = multiply(n.getExpression().get(), data);
    final int conditionalExpressionComplexity = 0;

    if (conditionalExpressionComplexity > 1) {
      boolCompReturn += conditionalExpressionComplexity;
    }
    return boolCompReturn > 0 ? conditionalExpressionComplexity : 1;
  }

  // @Override
  // public Integer visit(final BreakStmt n, final JavaFileDataHandler arg) {
  //   return 1;
  // }

  // @Override
  // public Integer visit(final SwitchExpr n, final JavaFileDataHandler data) {
  //   // bool_comp of switch + sum(npath(case_range))
  //   return super.visit(n, data);
  // }

  @Override
  public Integer visit(final SwitchStmt n, final JavaFileDataHandler data) {
    // bool_comp of switch + sum(npath(case_range))

    final int boolCompSwitch = booleanExpressionComplexity(n.getSelector());

    //
    int npath = 0;
    //
    int caseRange = 0;
    for (final SwitchEntry entry : n.getEntries()) {
      if (entry.getType().equals(SwitchEntry.Type.STATEMENT_GROUP)
          || entry.getType().equals(SwitchEntry.Type.THROWS_STATEMENT)) {
        npath += caseRange;
        caseRange = 1;
      } else if (entry.getType().equals(SwitchEntry.Type.EXPRESSION)
          || entry.getType().equals(SwitchEntry.Type.BLOCK)) {
        npath += caseRange;
        final int complexity = entry.accept(this, data);
        caseRange = complexity;
      } else {
        final int complexity = entry.accept(this, data);
        caseRange *= complexity;
      }
    }
    // add in npath of last label
    npath += caseRange;
    return boolCompSwitch + npath;
  }

  @Override
  public Integer visit(final SwitchEntry n, final JavaFileDataHandler data) {
    // this is the <default> case
    if (n.getLabels().isEmpty()) {
      return 1;
    }
    return n.getLabels().size();
  }

  // @Override
  // public Integer visit(final BinaryExpr n, final JavaFileDataHandler data) {
  //   if (n.getOperator().equals(BinaryExpr.Operator.AND) || n.getOperator()
  //       .equals(BinaryExpr.Operator.OR)) {
  //     Integer i = super.visit(n, data);
  //     return i == null ? 1 : i + 1;
  //   }
  //   Integer i = super.visit(n, data);
  //   return i == null ? 0 : i;
  // }

  @Override
  public Integer visit(final ConditionalExpr n, final JavaFileDataHandler data) {
    // bool comp of guard clause + complexity of last two children (= total - 1)
    final int boolCompTernary = booleanExpressionComplexity(n.getCondition());
    final int thenValue = n.getThenExpr().accept(this, data);
    final int elseValue = n.getElseExpr().accept(this, data);
    return boolCompTernary + thenValue + elseValue + 2;
  }

  @Override
  public Integer visit(final TryStmt n, final JavaFileDataHandler data) {
    /*
     * This scenario was not addressed by the original paper. Based on the
     * principles outlined in the paper, as well as the Checkstyle NPath
     * implementation, this code will add the complexity of the try to the
     * complexities of the catch and finally blocks.
     */
    int sum = 0;
    for (final Node node : n.getChildNodes()) {
      sum += node.accept(this, data);
    }
    return sum;
  }

  private int booleanExpressionComplexity(final Expression expr) {
    if (expr == null) {
      return 0;
    }

    if (expr.isBinaryExpr()) {
      final boolean isAndOperator =
          expr.asBinaryExpr().getOperator().equals(BinaryExpr.Operator.AND);
      final boolean isOrOperator = expr.asBinaryExpr().getOperator().equals(BinaryExpr.Operator.OR);
      if (isAndOperator || isOrOperator) {
        int complexity = 1;
        complexity += booleanExpressionComplexity(expr.asBinaryExpr().getLeft());
        complexity += booleanExpressionComplexity(expr.asBinaryExpr().getRight());
        return complexity;
      } else {
        return 0;
      }

    } else {
      return 0;
    }
  }
}
