package net.explorviz.code.analysis.parser;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@QuarkusTest
class JsConfigParsePerfTest {

  private static final String WEBPACK_DEVALL_JS = """
      // Specific configuration for developers
      // Runs a HTTPS server in port 8080, accesible
      // from all hosts that can connect to it.
      // Convenient for connecting external devices (eg, a VR device)
      // USE AT YOUR OWN RISK

      const path = require("path");
      const { merge } = require("webpack-merge");
      const common = require("./webpack.common");

      module.exports = merge(common, {
        mode: "development",
        devtool: "inline-source-map",
        devServer: {
          static: {
            directory: path.join(__dirname, '.'),
          },
          host: "0.0.0.0",
          server: {
            type: 'https',
            options: {
              cert: './babia_cert.pem',
              key: './babia_key.pem'
            }
          },
          devMiddleware: {
            writeToDisk: true,
          }
        }
      });
      """;

  private static final Path BABIA_EASYRTC = Path.of(
      "cloned-repositories/aframe-babia-components/examples/multiuser/easyrtc.js");
  private static final Path BABIA_BOATS = Path.of(
      "cloned-repositories/aframe-babia-components/components/visualizers/babia-boats.js");

  @Inject
  AntlrTypeScriptParserService parser;

  @Test
  void webpackDevallParsesQuickly() {
    final long start = System.nanoTime();
    final var handler = parser.parseFileContent(WEBPACK_DEVALL_JS, "webpack.devall.js", "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println("webpack.devall.js parse took " + ms + " ms, handler=" + (handler != null));
    Assertions.assertNotNull(handler);
    Assertions.assertTrue(ms < 1_000, "Expected parse under 1s, was " + ms + " ms");
  }

  @Test
  @EnabledIf("babiaRepoPresent")
  void easyrtcTimesOutInsteadOfHanging() throws Exception {
    final String content = Files.readString(BABIA_EASYRTC);
    final long budget = AntlrParserUtils.DEFAULT_PARSE_TIMEOUT_MS + 3_000L;
    final long start = System.nanoTime();
    final var handler = parser.parseFileContent(content, "examples/multiuser/easyrtc.js", "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println("easyrtc.js parse took " + ms + " ms, handler=" + (handler != null));
    // Pathological vendored JS: full parse should time out and fall back (null).
    Assertions.assertNull(handler);
    Assertions.assertTrue(ms < budget, "Expected timeout under " + budget + "ms, was " + ms + " ms");
  }

  @Test
  @EnabledIf("babiaRepoPresent")
  void babiaBoatsDoesNotHang() throws Exception {
    final String content = Files.readString(BABIA_BOATS);
    final long budget = AntlrParserUtils.DEFAULT_PARSE_TIMEOUT_MS + 3_000L;
    final long start = System.nanoTime();
    // May succeed or fall back (null) after timeout — must not hang indefinitely.
    parser.parseFileContent(content, "components/visualizers/babia-boats.js", "hash");
    final long ms = (System.nanoTime() - start) / 1_000_000L;
    System.out.println("babia-boats.js parse took " + ms + " ms");
    Assertions.assertTrue(ms < budget, "Expected parse/timeout under " + budget + "ms, was " + ms + " ms");
  }

  static boolean babiaRepoPresent() {
    return Files.isRegularFile(BABIA_EASYRTC) && Files.isRegularFile(BABIA_BOATS);
  }
}
