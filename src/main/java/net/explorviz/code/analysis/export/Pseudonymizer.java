package net.explorviz.code.analysis.export;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps identifying values to stable pseudonyms with a keyed HMAC-SHA-256. Plain hashes are not used
 * because the input space (e.g., the contributor emails of a public project) is small enough to be
 * replayed. Equal inputs yield equal pseudonyms as long as the secret stays the same.
 */
@Singleton
public class Pseudonymizer {

  private static final Logger LOGGER = LoggerFactory.getLogger(Pseudonymizer.class);
  private static final String ALGORITHM = "HmacSHA256";
  private static final int KEY_BYTES = 32;
  private static final int PSEUDONYM_BYTES = 6;

  private final SecretKeySpec key;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  /** Uses the configured secret, or a random one that only lives as long as this process. */
  @Inject
  public Pseudonymizer(
      @ConfigProperty(name = "explorviz.gitanalysis.anonymization-secret")
          final Optional<String> secret) {
    this(
        secret
            .filter(s -> !s.isBlank())
            .map(s -> s.getBytes(StandardCharsets.UTF_8))
            .orElseGet(Pseudonymizer::randomKey));
  }

  /* package */ Pseudonymizer(final byte[] key) {
    this.key = new SecretKeySpec(key, ALGORITHM);
  }

  private static byte[] randomKey() {
    LOGGER.warn(
        "No anonymization secret configured; pseudonyms will change after a restart. Set "
            + "ANALYSIS_ANONYMIZATION_SECRET to keep them stable across incremental analyses.");
    final byte[] key = new byte[KEY_BYTES];
    new SecureRandom().nextBytes(key);
    return key;
  }

  /** Returns a short hex pseudonym for the case-insensitive, trimmed value. */
  public String pseudonym(final String value) {
    return cache.computeIfAbsent(value.trim().toLowerCase(Locale.ROOT), this::hmac);
  }

  private String hmac(final String value) {
    try {
      final Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      final byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, PSEUDONYM_BYTES);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA-256 is not available", e);
    }
  }
}
