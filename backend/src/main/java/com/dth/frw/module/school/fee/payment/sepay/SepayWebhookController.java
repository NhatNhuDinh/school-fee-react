package com.dth.frw.module.school.fee.payment.sepay;

import com.dth.frw.module.school.fee.payment.sepay.BankTransactionService.IngestResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code POST /api/sepay/webhook} — where SePay says money arrived. Open in the filter chain (SePay holds no
 * session), so {@link SepayWebhookVerifier} is the only gate and runs first.
 *
 * <p>The answer is the contract with SePay: it wants {@code 200} with {@code {"success": true}} within 30 seconds,
 * and retries on anything else for about half an hour. So:
 *
 * <ul>
 *   <li>A call that is authentic and understood is answered {@code success} <b>whatever became of the money</b>
 *       — paid, set aside for review, a duplicate or ignored. SePay delivered; what we did with it is ours to
 *       show on the review screen, and retrying would change nothing.</li>
 *   <li>A forged or unsigned call gets {@code 401}, a malformed one {@code 400}: neither is worth a retry
 *       and neither is recorded as money.</li>
 *   <li>A failure of our own — the database down, a bug — is a {@code 500}, which is exactly what makes SePay
 *       send it again. The line was already stored as {@code RECEIVED} by then, so nothing is lost either way.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/sepay")
public class SepayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(SepayWebhookController.class);

    private final SepayWebhookVerifier verifier;
    private final BankTransactionService transactions;
    private final ObjectMapper mapper;

    public SepayWebhookController(SepayWebhookVerifier verifier, BankTransactionService transactions, ObjectMapper mapper) {
        this.verifier = verifier;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestHeader(value = "X-SePay-Timestamp", required = false) String timestamp,
            @RequestBody byte[] body) {

        switch (verifier.verify(authorization, signature, timestamp, body)) {
            case NOT_CONFIGURED -> {
                log.error("SePay webhook called but no secret is configured (school-fee.sepay.webhook.*)");
                return reply(HttpStatus.SERVICE_UNAVAILABLE, false, "Webhook chưa được cấu hình");
            }
            case REJECTED -> {
                log.warn("SePay webhook rejected: bad credentials");
                return reply(HttpStatus.UNAUTHORIZED, false, "Unauthorized");
            }
            case OK -> {
                // authentic: read it
            }
        }

        SepayPayload payload;
        try {
            JsonNode node = mapper.readTree(body);
            payload = SepayPayload.fromWebhook(node, new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            payload = null;
        }
        if (payload == null) {
            log.warn("SePay webhook with an unreadable body");
            return reply(HttpStatus.BAD_REQUEST, false, "Invalid payload");
        }

        IngestResult result = transactions.ingest(payload, "WEBHOOK");
        log.info("SePay webhook id={} -> {}", payload.id(), result.outcome());
        return reply(HttpStatus.OK, true, null);
    }

    private static ResponseEntity<Map<String, Object>> reply(HttpStatus status, boolean success, String message) {
        return ResponseEntity.status(status)
                .body(message == null ? Map.of("success", success) : Map.of("success", success, "message", message));
    }
}
