package com.dth.frw.module.school.fee.payment.service;

import com.dth.frw.module.security.auth.service.AuthorizationService;
import com.dth.frw.shared.messaging.PostgresEventBus;
import com.dth.frw.shared.security.Permissions;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the cashier's open screen that the transfer on it has been paid, the moment it is, instead of the
 * screen asking every few seconds.
 *
 * <p>Delivery only, in the same spirit as {@code NotificationPublisher}: the request row and the receipts are
 * the record, and they are complete without this. A closed socket costs the screen its instant update and
 * nothing else — it asks once when it reconnects and when the window regains focus. The message carries
 * just the request id; the client reads the request itself, so no receipt or amount travels over a socket
 * whose subscriber was authorised once.
 *
 * <p>It goes to the account that asked for the transfer, on {@code /user/queue/fee_payments}, which the
 * interceptor guards with {@code fee_payments:read} like every destination. The permission is asked again per
 * message here: a socket outlives the grant that justified it.
 */
@Component
public class TransferPaymentPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferPaymentPublisher.class);

    /** Where the client subscribes, minus the {@code /user} prefix Spring adds. */
    public static final String DESTINATION = "/queue/fee_payments";

    /** What this feature's messages are labelled with on the shared bus. */
    static final String KIND = "transfer-paid";

    static final String REVIEW_KIND = "bank-review";

    /** The set of bank lines waiting for a person changed in this school: the badge and the list are out of date. */
    public record ReviewChanged(long organizationId) {
    }

    /** A transfer request became paid; {@code recipientId} is whoever asked for it. */
    public record TransferRequestPaid(long requestId, long recipientId) {
    }

    private final SimpMessagingTemplate messaging;
    private final AuthorizationService authorizationService;
    private final PostgresEventBus bus;
    private final JdbcTemplate jdbc;

    public TransferPaymentPublisher(SimpMessagingTemplate messaging, AuthorizationService authorizationService,
                                    PostgresEventBus bus, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.messaging = messaging;
        this.authorizationService = authorizationService;
        this.bus = bus;
    }

    /** After the commit: announcing from inside the transaction would name a payment the client cannot read yet. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPaid(TransferRequestPaid paid) {
        deliver(paid.requestId(), paid.recipientId());
        bus.publish(KIND, "%d:%d".formatted(paid.requestId(), paid.recipientId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReview(ReviewChanged changed) {
        deliverReview(changed.organizationId());
        bus.publish(REVIEW_KIND, String.valueOf(changed.organizationId()));
    }

    /** The same events, raised on another node: the cashier's socket may be open on this one. */
    @EventListener
    public void onRemote(PostgresEventBus.RemoteEvent remote) {
        if (REVIEW_KIND.equals(remote.kind())) {
            try {
                deliverReview(Long.parseLong(remote.payload().trim()));
            } catch (RuntimeException e) {
                log.warn("Ignoring unrecognised review message: {}", remote.payload());
            }
            return;
        }
        if (!KIND.equals(remote.kind())) {
            return;
        }
        String[] parts = remote.payload().split(":");
        try {
            deliver(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        } catch (RuntimeException e) {
            log.warn("Ignoring unrecognised transfer message: {}", remote.payload());
        }
    }

    private void deliver(long requestId, long recipientId) {
        if (!authorizationService.has(recipientId, Permissions.FEE_PAYMENTS_READ)) {
            log.debug("Not delivering transfer {} — recipient {} may not read fee payments", requestId, recipientId);
            return;
        }
        messaging.convertAndSendToUser(String.valueOf(recipientId), DESTINATION,
                Map.of("type", "transfer-paid", "requestId", requestId));
    }

    /** Every account of the school that may read thu tiền and has the screen open learns the list changed. */
    private void deliverReview(long organizationId) {
        List<Long> users = jdbc.queryForList("select id from users where organization_id = ?", Long.class, organizationId);
        for (Long user : users) {
            if (authorizationService.has(user, Permissions.FEE_PAYMENTS_READ)) {
                messaging.convertAndSendToUser(String.valueOf(user), DESTINATION, Map.of("type", "review-changed"));
            }
        }
    }
}
