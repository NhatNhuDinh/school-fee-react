package com.dth.frw.module.school.fee.notification.service;

import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.FeeLine;
import com.dth.frw.module.school.fee.notification.service.FeeNotificationViews.MailContext;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the notifications that are due.
 *
 * <p>A poller, not a queue: every few seconds it looks for a notification whose time has come,
 * claims it with one conditional {@code UPDATE} (so two nodes cannot both send it), then works
 * through its pending recipients a chunk at a time. After each chunk the counters are rewritten
 * from the recipient rows, so a screen that is watching sees the progress, and the claim's
 * {@code updated_at} is touched so a node that died mid-send can be told from one that is
 * merely busy. A claim that has gone quiet is handed back and the unsent recipients are picked
 * up again.
 *
 * <p>It works in SQL and holds no transaction across a send: a mail server that takes ten
 * seconds must not hold a database connection for ten seconds, and one recipient failing must
 * leave the others' results written. It reads rows directly and so is not narrowed by
 * row-level scope; it only ever works on notifications that were scheduled by somebody who was.
 */
@Component
public class FeeNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(FeeNotificationDispatcher.class);
    private static final int ERROR_LIMIT = 1_000;
    /** A claim not touched for this long is taken to belong to a node that has gone. */
    private static final String STALE_AFTER = "5 minutes";

    private final JdbcTemplate jdbc;
    private final FeeNotificationService service;
    private final FeeNotificationEmailRenderer renderer;
    private final FeeNotificationProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailUsername;

    public FeeNotificationDispatcher(JdbcTemplate jdbc,
                                     FeeNotificationService service,
                                     FeeNotificationEmailRenderer renderer,
                                     FeeNotificationProperties properties,
                                     ObjectProvider<JavaMailSender> mailSender,
                                     @Value("${spring.mail.username:}") String mailUsername) {
        this.jdbc = jdbc;
        this.service = service;
        this.renderer = renderer;
        this.properties = properties;
        this.mailSender = mailSender;
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
    }

    @Scheduled(fixedDelayString = "${school-fee.notification.poll-interval-ms:5000}", initialDelay = 10_000)
    public void poll() {
        if (!properties.dispatchEnabled()) {
            return;
        }
        try {
            dispatchDue();
        } catch (RuntimeException e) {
            // The poller must outlive any one bad row.
            log.error("Gửi thông báo thu tiền lỗi", e);
        }
    }

    /** Runs one round: hand back stale claims, then send every notification that is due. Public for tests. */
    public int dispatchDue() {
        jdbc.update("update fee_notifications set status = 'SCHEDULED' where status = 'SENDING'"
                + " and updated_at < now() - interval '" + STALE_AFTER + "'");
        List<Long> due = jdbc.queryForList("""
                select id from fee_notifications
                where status = 'SCHEDULED' and send_at <= now()
                order by send_at, id limit 5
                """, Long.class);
        int handled = 0;
        for (Long id : due) {
            // One conditional update is the whole lock.
            int claimed = jdbc.update(
                    "update fee_notifications set status = 'SENDING', updated_at = now() where id = ? and status = 'SCHEDULED'", id);
            if (claimed == 1) {
                send(id);
                handled++;
            }
        }
        return handled;
    }

    // ---- one notification

    private void send(Long notificationId) {
        try {
            NotificationRow notification = loadNotification(notificationId);
            if (notification == null) {
                return;
            }
            List<RecipientRow> chunk;
            while (!(chunk = nextChunk(notificationId)).isEmpty()) {
                for (RecipientRow recipient : chunk) {
                    deliver(notification, recipient);
                }
                service.refreshCounts(notificationId);
                jdbc.update("update fee_notifications set updated_at = now() where id = ?", notificationId);
            }
            finish(notificationId);
        } catch (RuntimeException e) {
            log.error("Thông báo {} gửi dở dang vì lỗi", notificationId, e);
            jdbc.update("update fee_notifications set status = 'FAILED', error_message = ?, updated_at = now() where id = ?",
                    limit(e.getMessage()), notificationId);
        }
    }

    private void deliver(NotificationRow notification, RecipientRow recipient) {
        try {
            JavaMailSender sender = mailSender.getIfAvailable();
            if (sender == null || usernameBlank()) {
                throw new IllegalStateException("SMTP chưa được cấu hình");
            }
            MailContext context = new MailContext(notification.schoolName(), properties.senderUnit(),
                    notification.subject(), notification.content(), notification.deadline(),
                    recipient.studentName(), recipient.studentCode(), recipient.className(),
                    service.readLines(recipient.linesJson()), recipient.total(), recipient.orderCode(),
                    recipient.bankNumber(), recipient.bankCode(), recipient.bankHolder(),
                    recipient.qrBaseUrl(), recipient.qrTemplate(), recipient.qrShowInfo());
            String html = renderer.render(context, "cid:" + FeeNotificationEmailRenderer.LOGO_CONTENT_ID);

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress(), notification.schoolName(), "UTF-8"));
            helper.setTo(recipient.email());
            helper.setSubject(notification.subject());
            helper.setText(html, true);
            helper.addInline(FeeNotificationEmailRenderer.LOGO_CONTENT_ID,
                    new ByteArrayResource(renderer.logoBytes()), "image/png");
            sender.send(message);

            jdbc.update("update fee_notification_recipients set status = 'SENT', sent_at = now(), "
                    + "attempts = attempts + 1, error_message = null where id = ?", recipient.id());
        } catch (Exception e) {
            log.warn("Không gửi được thông báo cho {}: {}", recipient.email(), e.getMessage());
            jdbc.update("update fee_notification_recipients set status = 'FAILED', attempts = attempts + 1, "
                    + "error_message = ? where id = ?", limit(rootMessage(e)), recipient.id());
        }
    }

    private void finish(Long notificationId) {
        service.refreshCounts(notificationId);
        jdbc.update("""
                update fee_notifications set updated_at = now(), error_message = null,
                    status = case
                        when sent_count = 0 and (failed_count > 0 or missing_email_count > 0) then 'FAILED'
                        when failed_count > 0 or missing_email_count > 0 then 'PARTIAL_FAILURE'
                        else 'COMPLETED' end
                where id = ? and status = 'SENDING'
                """, notificationId);
    }

    // ---- reads

    private List<RecipientRow> nextChunk(Long notificationId) {
        return jdbc.query("""
                select r.id, r.student_name, r.student_code, r.class_name, r.email, r.fee_lines_json, r.total_amount,
                       p.order_code, p.bank_account_number, p.bank_code, p.bank_account_holder,
                       p.viet_qr_base_url, p.viet_qr_template, p.viet_qr_show_info
                from fee_notification_recipients r
                left join fee_payment_requests p on p.id = r.payment_request_id
                where r.notification_id = ? and r.status = 'PENDING'
                order by r.id limit ?
                """, (rs, i) -> new RecipientRow(rs.getLong("id"), rs.getString("student_name"),
                rs.getString("student_code"), rs.getString("class_name"), rs.getString("email"),
                rs.getString("fee_lines_json"), rs.getBigDecimal("total_amount"), rs.getString("order_code"),
                rs.getString("bank_account_number"), rs.getString("bank_code"), rs.getString("bank_account_holder"),
                rs.getString("viet_qr_base_url"), rs.getString("viet_qr_template"), rs.getString("viet_qr_show_info")),
                notificationId, properties.dispatchBatchSize());
    }

    private NotificationRow loadNotification(Long id) {
        List<NotificationRow> rows = jdbc.query("""
                select n.subject, n.content, n.deadline, o.name as school_name
                from fee_notifications n join organizations o on o.id = n.organization_id
                where n.id = ?
                """, (rs, i) -> new NotificationRow(rs.getString("subject"), rs.getString("content"),
                rs.getDate("deadline").toLocalDate(), rs.getString("school_name")), id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    // ---- small things

    private boolean usernameBlank() {
        return mailUsername.isEmpty();
    }

    private String fromAddress() {
        return properties.from() != null && !properties.from().isBlank() ? properties.from() : mailUsername;
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= ERROR_LIMIT ? value : value.substring(0, ERROR_LIMIT);
    }

    private record NotificationRow(String subject, String content, LocalDate deadline, String schoolName) {
    }

    private record RecipientRow(long id, String studentName, String studentCode, String className, String email,
                                String linesJson, BigDecimal total, String orderCode, String bankNumber,
                                String bankCode, String bankHolder, String qrBaseUrl, String qrTemplate,
                                String qrShowInfo) {
    }

}
