package com.dth.frw.module.school.fee.payment.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dth.frw.module.school.fee.payment.sepay.SepayWebhookVerifier.Verdict;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Who may tell the application that money arrived. No database, no Spring: this is the whole gate. */
class SepayWebhookVerifierTest {

    private static final byte[] BODY = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-11-13T07:00:00Z");

    private static SepayWebhookVerifier with(String apiKey, String hmacSecret) {
        return new SepayWebhookVerifier(new SepayProperties(
                new SepayProperties.Webhook(apiKey, hmacSecret, 300), null, null, null, null, null));
    }

    private static String sign(String secret, long seconds, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((seconds + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(body);
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
    }

    @Test
    void withNothingConfiguredNothingPasses() {
        assertEquals(Verdict.NOT_CONFIGURED, with(null, null).verify("Apikey x", null, null, BODY, NOW));
        assertEquals(Verdict.NOT_CONFIGURED, with(" ", "").verify(null, null, null, BODY, NOW));
    }

    @Test
    void theApiKeyMustMatchExactly() {
        SepayWebhookVerifier verifier = with("secret-key", null);
        assertEquals(Verdict.OK, verifier.verify("Apikey secret-key", null, null, BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify("Apikey secret-kez", null, null, BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify("Bearer secret-key", null, null, BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify("apikey secret-key", null, null, BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify(null, null, null, BODY, NOW));
    }

    @Test
    void aSignedRequestPassesAndATamperedBodyDoesNot() throws Exception {
        SepayWebhookVerifier verifier = with(null, "hmac-secret");
        long ts = NOW.getEpochSecond();
        String good = sign("hmac-secret", ts, BODY);

        assertEquals(Verdict.OK, verifier.verify(null, good, String.valueOf(ts), BODY, NOW));
        assertEquals(Verdict.REJECTED,
                verifier.verify(null, good, String.valueOf(ts), "{\"id\":2}".getBytes(StandardCharsets.UTF_8), NOW));
        assertEquals(Verdict.REJECTED, verifier.verify(null, sign("other", ts, BODY), String.valueOf(ts), BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify(null, good.substring(7), String.valueOf(ts), BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify(null, good, null, BODY, NOW));
    }

    @Test
    void aSignedRequestFromThePastIsAReplayAndIsRefused() throws Exception {
        SepayWebhookVerifier verifier = with(null, "hmac-secret");
        long old = NOW.getEpochSecond() - 301;
        assertEquals(Verdict.REJECTED, verifier.verify(null, sign("hmac-secret", old, BODY), String.valueOf(old), BODY, NOW));
        long inside = NOW.getEpochSecond() - 299;
        assertEquals(Verdict.OK, verifier.verify(null, sign("hmac-secret", inside, BODY), String.valueOf(inside), BODY, NOW));
    }

    @Test
    void whenBothMethodsAreConfiguredBothMustPass() throws Exception {
        SepayWebhookVerifier verifier = with("k", "s");
        long ts = NOW.getEpochSecond();
        String sig = sign("s", ts, BODY);

        assertEquals(Verdict.OK, verifier.verify("Apikey k", sig, String.valueOf(ts), BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify("Apikey wrong", sig, String.valueOf(ts), BODY, NOW));
        assertEquals(Verdict.REJECTED, verifier.verify("Apikey k", null, null, BODY, NOW));
    }
}
