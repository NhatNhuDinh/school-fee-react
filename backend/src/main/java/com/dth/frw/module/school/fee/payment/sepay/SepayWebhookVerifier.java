package com.dth.frw.module.school.fee.payment.sepay;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Decides whether a webhook call really came from SePay. The endpoint takes no session, so this is all that
 * stands between the internet and "the money arrived". Every method that has a secret configured has to pass:
 *
 * <ul>
 *   <li><b>API key</b> — {@code Authorization: Apikey <key>}.</li>
 *   <li><b>HMAC-SHA256</b> — {@code X-SePay-Signature: sha256=<hex>} over {@code <timestamp>.<raw body>}, with
 *       {@code X-SePay-Timestamp} in seconds and within the tolerance of now, which is what stops a captured
 *       request being replayed later.</li>
 * </ul>
 *
 * Secrets are compared in constant time. With nothing configured nothing passes.
 */
@Component
public class SepayWebhookVerifier {

    public enum Verdict { OK, NOT_CONFIGURED, REJECTED }

    private final SepayProperties properties;

    public SepayWebhookVerifier(SepayProperties properties) {
        this.properties = properties;
    }

    public Verdict verify(String authorization, String signature, String timestamp, byte[] body) {
        return verify(authorization, signature, timestamp, body, Instant.now());
    }

    Verdict verify(String authorization, String signature, String timestamp, byte[] body, Instant now) {
        SepayProperties.Webhook config = properties.webhook();
        if (!config.configured()) {
            return Verdict.NOT_CONFIGURED;
        }
        if (config.hasApiKey() && !equal("Apikey " + config.apiKey(), authorization)) {
            return Verdict.REJECTED;
        }
        if (config.hasHmac() && !signatureValid(config, signature, timestamp, body, now)) {
            return Verdict.REJECTED;
        }
        return Verdict.OK;
    }

    private static boolean signatureValid(SepayProperties.Webhook config, String signature, String timestamp,
                                          byte[] body, Instant now) {
        if (signature == null || timestamp == null || !signature.startsWith("sha256=")) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(now.getEpochSecond() - seconds) > config.toleranceSeconds()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((seconds + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal());
            return equal(expected, signature.trim().toLowerCase());
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    private static boolean equal(String expected, String given) {
        if (given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
