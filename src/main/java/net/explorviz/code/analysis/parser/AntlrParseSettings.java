package net.explorviz.code.analysis.parser;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Shared ANTLR parse wall-clock budget for all language parser services.
 */
@ApplicationScoped
public class AntlrParseSettings {

  @ConfigProperty(name = "explorviz.gitanalysis.parse-timeout-ms",
      defaultValue = "" + AntlrParserUtils.DEFAULT_PARSE_TIMEOUT_MS)
  /* default */ long parseTimeoutMs = AntlrParserUtils.DEFAULT_PARSE_TIMEOUT_MS;

  public long parseTimeoutMs() {
    return parseTimeoutMs;
  }
}
