package net.explorviz.code.analysis.metrics.java;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.TreeWalker;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck;
import com.puppycrawl.tools.checkstyle.checks.metrics.NPathComplexityCheck;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Complexities that Checkstyle reports for the methods, constructors and initializer blocks of a
 * file, by the position of the function.
 */
final class CheckstyleReference implements AuditListener {

  private static final Pattern MESSAGE = Pattern.compile("(\\w+) Complexity is (-?[\\d,]+) .*");

  private final Map<Position, BigInteger> cyclomaticComplexities = new TreeMap<>();
  private final Map<Position, BigInteger> npathComplexities = new TreeMap<>();

  private CheckstyleReference() {}

  static CheckstyleReference of(final Path file) throws CheckstyleException {
    final DefaultConfiguration treeWalker = new DefaultConfiguration(TreeWalker.class.getName());
    treeWalker.addChild(reportEverything(CyclomaticComplexityCheck.class));
    treeWalker.addChild(reportEverything(NPathComplexityCheck.class));
    final DefaultConfiguration configuration = new DefaultConfiguration("root");
    configuration.addProperty("localeLanguage", "en");
    configuration.addProperty("localeCountry", "US");
    configuration.addProperty("tabWidth", "1");
    configuration.addChild(treeWalker);

    final CheckstyleReference reference = new CheckstyleReference();
    final Checker checker = new Checker();
    checker.setModuleClassLoader(CheckstyleReference.class.getClassLoader());
    checker.configure(configuration);
    checker.addListener(reference);
    checker.process(List.of(file.toFile()));
    checker.destroy();
    return reference;
  }

  Map<Position, BigInteger> cyclomaticComplexities() {
    return cyclomaticComplexities;
  }

  Map<Position, BigInteger> npathComplexities() {
    return npathComplexities;
  }

  private static DefaultConfiguration reportEverything(final Class<?> check) {
    final DefaultConfiguration configuration = new DefaultConfiguration(check.getName());
    configuration.addProperty("max", String.valueOf(Integer.MIN_VALUE));
    return configuration;
  }

  @Override
  public void addError(final AuditEvent event) {
    final Matcher message = MESSAGE.matcher(event.getMessage());
    if (!message.matches()) {
      throw new IllegalStateException(event.getFileName() + ": " + event.getMessage());
    }
    final Map<Position, BigInteger> complexities =
        "NPath".equals(message.group(1)) ? npathComplexities : cyclomaticComplexities;
    complexities.put(
        new Position(event.getLine(), event.getColumn()),
        new BigInteger(message.group(2).replace(",", "")));
  }

  @Override
  public void addException(final AuditEvent event, final Throwable throwable) {
    throw new IllegalStateException(event.getFileName(), throwable);
  }

  @Override
  public void auditStarted(final AuditEvent event) {}

  @Override
  public void auditFinished(final AuditEvent event) {}

  @Override
  public void fileStarted(final AuditEvent event) {}

  @Override
  public void fileFinished(final AuditEvent event) {}

  /** Position of the first token of a function, both one-based. */
  record Position(int line, int column) implements Comparable<Position> {
    @Override
    public int compareTo(final Position other) {
      return line == other.line
          ? Integer.compare(column, other.column)
          : Integer.compare(line, other.line);
    }
  }
}
