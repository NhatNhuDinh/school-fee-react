package com.dth.frw.module.school.fee.payment.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.fee.payment.sepay.SepayProperties;
import com.dth.frw.module.school.fee.payment.service.FeePaymentService.Source;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Receipt;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.TransferInfo;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A transfer asked for at the counter. Showing the QR is not enough to take money by transfer: the
 * code, the amount and the khoản thu it pays for are stored first, so that when the bank reports a
 * line carrying the code there is something to match it to — and so that "this code, this amount"
 * is the one fact a webhook has to confirm.
 *
 * <p>A request is paid exactly once: by the bank's report ({@code BANK}) or by the cashier saying the
 * money has arrived ({@code MANUAL}). Either way it settles the same receivables for the same amount
 * through {@link FeePaymentService#settleReceivables}, so a transfer receipt cannot differ from a cash one.
 */
@Service
@Transactional(readOnly = true)
public class FeeTransferRequestService {

    private static final int CODE_ATTEMPTS = 5;

    /** The state of a request as the counter and the review screen show it. */
    public record View(long id, long enrollmentId, String studentCode, String studentName, String className,
                       String orderCode, BigDecimal amount, String status, OffsetDateTime createdAt,
                       OffsetDateTime expiresAt, OffsetDateTime paidAt, String paidBy, String createdByName,
                       TransferInfo transfer, List<Receipt> receipts) {
    }

    /** What the bank's report did to a request: paid it, or why not. */
    public record BankPayment(boolean ok, String reason, String note, Long enrollmentId, List<Receipt> receipts) {
        static BankPayment paid(Long enrollmentId, List<Receipt> receipts, String note) {
            return new BankPayment(true, null, note, enrollmentId, receipts);
        }

        static BankPayment refused(String reason, String note, Long enrollmentId) {
            return new BankPayment(false, reason, note, enrollmentId, List.of());
        }
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AcademicYearService academicYearService;
    private final FeePaymentService payments;
    private final SepayProperties sepay;
    private final ApplicationEventPublisher events;

    public FeeTransferRequestService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named,
                                     AcademicYearService academicYearService, FeePaymentService payments,
                                     SepayProperties sepay, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.named = named;
        this.academicYearService = academicYearService;
        this.payments = payments;
        this.sepay = sepay;
        this.events = events;
    }

    // ---- counter

    /**
     * Asks for a transfer of everything the named khoản thu still owe. Asking again for the same khoản thu
     * returns the request already out, so the code does not change under a QR somebody has scanned; an e-mailed
     * request for exactly the same thing lends its code, so both QR codes agree. A different selection is a new
     * request, and the earlier ones stay payable until they expire or their khoản thu are collected.
     */
    @Transactional
    public View create(Long yearId, Long enrollmentId, List<Long> receivableIds) {
        AcademicYear year = academicYearService.get(yearId);
        if (receivableIds == null || receivableIds.isEmpty()) {
            throw invalid("receivableIds", "Chọn ít nhất một khoản thu");
        }
        List<Long> ids = new ArrayList<>(new HashSet<>(receivableIds));
        BigDecimal amount = named.queryForObject("""
                select coalesce(sum(r.receivable_amount - r.collected_amount), 0), count(*)
                from student_fee_receivables r
                join fee_collection_batch_students bs on bs.id = r.batch_student_id
                join fee_collection_batches b on b.id = bs.batch_id
                where r.id in (:ids) and bs.enrollment_id = :enrollment and b.academic_year_id = :year
                  and r.receivable_amount > r.collected_amount
                """, new MapSqlParameterSource("ids", ids).addValue("enrollment", enrollmentId)
                        .addValue("year", yearId), (rs, n) -> rs.getLong(2) == ids.size() ? rs.getBigDecimal(1) : null);
        if (amount == null || amount.signum() <= 0) {
            throw new ConflictException("Có khoản thu đã được thu hoặc không còn trong đợt thu, hãy tải lại màn hình");
        }

        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, enrollmentId);
        expireStale(yearId);

        // The same student owing the same things is the same request: opening the screen again, reloading it or
        // coming back tomorrow shows the code that is already out there, so a QR somebody scanned stays good.
        Set<Long> wanted = new HashSet<>(ids);
        for (Long openId : jdbc.queryForList("select id from fee_transfer_requests where enrollment_id = ? "
                + "and academic_year_id = ? and status in ('PENDING', 'DEFERRED') and expires_at >= now() "
                + "order by id desc", Long.class, enrollmentId, yearId)) {
            if (new HashSet<>(lineIds(openId)).equals(wanted)) {
                return view(yearId, openId);
            }
        }

        String who = jdbc.query("select coalesce(nullif(full_name, ''), username) from users where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, CurrentUser.id().orElse(-1L));
        Timestamp expires = Timestamp.from(Instant.now().plus(sepay.requestTtl()));

        // Asking for exactly what an e-mail asked for: the e-mail's own code, so both QR codes read the same.
        Long requestId = null;
        for (Object[] mail : emailRequestsFor(yearId, enrollmentId, wanted, amount)) {
            requestId = insertRequest(year, enrollmentId, (String) mail[1], amount, expires, who, (Long) mail[0]);
            if (requestId != null) {
                break;
            }
        }
        for (int attempt = 0; attempt < CODE_ATTEMPTS && requestId == null; attempt++) {
            requestId = insertRequest(year, enrollmentId, payments.newOrderCode(), amount, expires, who, null);
        }
        if (requestId == null) {
            throw new ConflictException("Không tạo được mã chuyển khoản, vui lòng thử lại");
        }
        for (Long id : ids) {
            jdbc.update("insert into fee_transfer_request_lines (request_id, receivable_id) values (?, ?)", requestId, id);
        }
        return view(yearId, requestId);
    }

    /** Null when the code is taken (a 1 in 32^14 draw, or an e-mail code an earlier request already used). */
    private Long insertRequest(AcademicYear year, Long enrollmentId, String code, BigDecimal amount, Timestamp expires,
                               String who, Long paymentRequestId) {
        try {
            return jdbc.queryForObject("""
                    insert into fee_transfer_requests (organization_id, academic_year_id, enrollment_id,
                        order_code, amount, expires_at, created_by_id, created_by_name, payment_request_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, Long.class, year.getOrganization().getId(), year.getId(), enrollmentId, code, amount, expires,
                    CurrentUser.id().orElse(null), who, paymentRequestId);
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    /**
     * The e-mailed requests still waiting that ask for exactly these khoản thu: one đợt, everything the student
     * still owes in it, for the amount now due. {@code [id, code]} each, newest first.
     */
    private List<Object[]> emailRequestsFor(Long yearId, Long enrollmentId, Set<Long> wanted, BigDecimal amount) {
        List<Object[]> found = new ArrayList<>();
        jdbc.query("""
                select p.id, replace(p.order_code, '-', ''), p.batch_student_id, p.requested_amount
                from fee_payment_requests p
                join fee_collection_batch_students bs on bs.id = p.batch_student_id
                where bs.enrollment_id = ? and p.academic_year_id = ? and p.status = 'PENDING'
                order by p.id desc
                """, rs -> {
            if (rs.getBigDecimal(4).compareTo(amount) != 0) {
                return;
            }
            Set<Long> owed = new HashSet<>(jdbc.queryForList("select id from student_fee_receivables "
                    + "where batch_student_id = ? and receivable_amount > collected_amount", Long.class, rs.getLong(3)));
            if (owed.equals(wanted)) {
                found.add(new Object[] {rs.getLong(1), rs.getString(2)});
            }
        }, enrollmentId, yearId);
        return found;
    }

    @Transactional
    public View get(Long yearId, Long id) {
        academicYearService.get(yearId);
        expireStale(yearId);
        return view(yearId, id);
    }

    /** The requests the cashier set aside with "Rà soát sau". A QR that was only shown is not listed; it still pays. */
    @Transactional
    public List<View> waiting(Long yearId) {
        academicYearService.get(yearId);
        expireStale(yearId);
        List<Long> ids = jdbc.queryForList("select id from fee_transfer_requests where academic_year_id = ? "
                + "and status = 'DEFERRED' order by created_at desc limit 200", Long.class, yearId);
        List<View> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(view(yearId, id));
        }
        return result;
    }

    public long waitingCount(Long yearId) {
        academicYearService.get(yearId);
        Long count = jdbc.queryForObject("select count(*) from fee_transfer_requests where academic_year_id = ? "
                + "and status = 'DEFERRED' and expires_at >= now()", Long.class, yearId);
        return count == null ? 0 : count;
    }

    /** "Rà soát sau": leave the QR; the request stays payable and shows on the waiting list. */
    @Transactional
    public View defer(Long yearId, Long id) {
        academicYearService.get(yearId);
        int changed = jdbc.update("update fee_transfer_requests set status = 'DEFERRED' where id = ? "
                + "and academic_year_id = ? and status = 'PENDING'", id, yearId);
        if (changed == 0) {
            requireExists(yearId, id);
        }
        return view(yearId, id);
    }

    @Transactional
    public View cancel(Long yearId, Long id) {
        academicYearService.get(yearId);
        int changed = jdbc.update("update fee_transfer_requests set status = 'CANCELLED' where id = ? "
                + "and academic_year_id = ? and status in ('PENDING', 'DEFERRED', 'EXPIRED')", id, yearId);
        if (changed == 0) {
            requireExists(yearId, id);
            throw new ConflictException("Yêu cầu này đã được thanh toán hoặc đã hủy");
        }
        return view(yearId, id);
    }

    /** The cashier has seen the money arrive: the same settlement the bank's report would have made. */
    @Transactional
    public View confirmManually(Long yearId, Long id) {
        AcademicYear year = academicYearService.get(yearId);
        Map<String, Object> request = lock(yearId, id);
        String status = (String) request.get("status");
        if ("PAID".equals(status)) {
            return view(yearId, id);
        }
        if ("CANCELLED".equals(status)) {
            throw new ConflictException("Yêu cầu chuyển khoản này đã bị hủy, hãy tạo yêu cầu mới");
        }
        BigDecimal amount = (BigDecimal) request.get("amount");
        var result = payments.settleReceivables(year, lineIds(id), amount, "TRANSFER", LocalDate.now(),
                (String) request.get("order_code"),
                new Source(currentName(), CurrentUser.id().orElse(null), id, null));
        if (!result.ok()) {
            throw new ConflictException(result.problem() + ". Hãy tải lại màn hình và tạo yêu cầu mới");
        }
        markPaid(id, "MANUAL");
        return view(yearId, id);
    }

    // ---- the bank's report

    /**
     * The bank reported {@code amount} against this request. Pays it when everything still holds —
     * the request is open, the amount is exactly what was asked, the khoản thu are still owed — and
     * otherwise refuses with a reason and changes nothing. Runs inside the caller's transaction.
     */
    @Transactional
    public BankPayment payFromBank(long id, BigDecimal amount, LocalDate paidOn, long bankTransactionId) {
        Map<String, Object> request = jdbc.queryForMap("select * from fee_transfer_requests where id = ? for update", id);
        Long enrollmentId = ((Number) request.get("enrollment_id")).longValue();
        String status = (String) request.get("status");
        BigDecimal asked = (BigDecimal) request.get("amount");
        long yearId = ((Number) request.get("academic_year_id")).longValue();

        if ("PAID".equals(status)) {
            // The cashier confirmed it by hand before the bank's line arrived: that is this line, not a second payment.
            boolean confirmedByHand = "MANUAL".equals(request.get("paid_by")) && asked.compareTo(amount) == 0
                    && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select not exists (select 1 from fee_payments where transfer_request_id = ? "
                            + "and bank_transaction_id is not null)", Boolean.class, id));
            if (confirmedByHand) {
                jdbc.update("update fee_payments set bank_transaction_id = ? where transfer_request_id = ?",
                        bankTransactionId, id);
                return BankPayment.paid(enrollmentId, List.of(), "Khớp với yêu cầu đã được thu ngân xác nhận trước đó");
            }
            return BankPayment.refused("ALREADY_PAID", "Yêu cầu chuyển khoản này đã được thanh toán trước đó", enrollmentId);
        }
        if ("CANCELLED".equals(status)) {
            return BankPayment.refused("REQUEST_CANCELLED", "Yêu cầu chuyển khoản này đã bị hủy", enrollmentId);
        }
        Timestamp expiresAt = (Timestamp) request.get("expires_at");
        if ("EXPIRED".equals(status) || expiresAt == null || expiresAt.toInstant().isBefore(Instant.now())) {
            jdbc.update("update fee_transfer_requests set status = 'EXPIRED' where id = ? and status in ('PENDING', 'DEFERRED')", id);
            return BankPayment.refused("REQUEST_EXPIRED", "Yêu cầu chuyển khoản đã hết hạn", enrollmentId);
        }
        if (asked.compareTo(amount) != 0) {
            BigDecimal diff = amount.subtract(asked);
            return BankPayment.refused("AMOUNT_MISMATCH", (diff.signum() < 0 ? "Thiếu " : "Thừa ")
                    + diff.abs().toPlainString() + " so với số tiền yêu cầu " + asked.toPlainString(), enrollmentId);
        }
        AcademicYear year = academicYearService.get(yearId);
        var result = payments.settleReceivables(year, lineIds(id), asked, "TRANSFER", paidOn,
                (String) request.get("order_code"), new Source("SePay (tự động)", null, id, bankTransactionId));
        if (!result.ok()) {
            return BankPayment.refused("DEBT_CHANGED", result.problem(), enrollmentId);
        }
        markPaid(id, "BANK");
        return BankPayment.paid(enrollmentId, result.receipts(), null);
    }

    // ---- lookup for the matcher

    /** Request ids whose order code is one of the given (already normalised) codes. */
    public List<Long> findByCodes(Set<String> codes) {
        if (codes.isEmpty()) {
            return List.of();
        }
        return named.queryForList("select id from fee_transfer_requests where order_code in (:codes)",
                new MapSqlParameterSource("codes", codes), Long.class);
    }

    /** The e-mailed request whose code this one borrowed; null for a code of its own. */
    public Long linkedPaymentRequest(long id) {
        return jdbc.query("select payment_request_id from fee_transfer_requests where id = ?",
                rs -> rs.next() ? (Long) rs.getObject(1) : null, id);
    }

    /**
     * Whether the request can still be answered as a payment. Cancelled and expired ones cannot; a paid one can,
     * because a transfer the cashier already confirmed by hand is matched to its bank line, not refused.
     */
    public boolean takesPayment(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select status not in ('CANCELLED', 'EXPIRED')
                       and (status = 'PAID' or expires_at >= now())
                from fee_transfer_requests where id = ?
                """, Boolean.class, id));
    }

    // ---- helpers

    private void markPaid(long id, String by) {
        jdbc.update("update fee_transfer_requests set status = 'PAID', paid_at = now(), paid_by = ? where id = ?", by, id);
        // The e-mail whose code this request borrowed is the same demand: it is paid too.
        jdbc.update("update fee_payment_requests set status = 'PAID' where status = 'PENDING' and id = "
                + "(select payment_request_id from fee_transfer_requests where id = ?)", id);
        // Whoever asked for this transfer has its QR on screen: tell them (after the commit) instead of letting them poll.
        Long asker = jdbc.queryForObject("select created_by_id from fee_transfer_requests where id = ?", Long.class, id);
        if (asker != null) {
            events.publishEvent(new TransferPaymentPublisher.TransferRequestPaid(id, asker));
        }
    }

    /**
     * Closes what can no longer be paid: a request past its time, and one whose khoản thu were collected some
     * other way (cash, another request) — its QR would only ever end on the review screen.
     */
    private void expireStale(Long yearId) {
        jdbc.update("update fee_transfer_requests set status = 'EXPIRED' where academic_year_id = ? "
                + "and status in ('PENDING', 'DEFERRED') and expires_at < now()", yearId);
        jdbc.update("""
                update fee_transfer_requests q set status = 'CANCELLED'
                where q.academic_year_id = ? and q.status in ('PENDING', 'DEFERRED')
                  and exists (select 1 from fee_transfer_request_lines l
                              join student_fee_receivables r on r.id = l.receivable_id
                              where l.request_id = q.id and r.collected_amount >= r.receivable_amount)
                """, yearId);
    }

    private Map<String, Object> lock(Long yearId, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from fee_transfer_requests where id = ? and academic_year_id = ? for update", id, yearId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("yêu cầu chuyển khoản", id);
        }
        return rows.get(0);
    }

    private void requireExists(Long yearId, Long id) {
        Boolean exists = jdbc.queryForObject(
                "select exists (select 1 from fee_transfer_requests where id = ? and academic_year_id = ?)",
                Boolean.class, id, yearId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new ResourceNotFoundException("yêu cầu chuyển khoản", id);
        }
    }

    private List<Long> lineIds(long requestId) {
        return jdbc.queryForList("select receivable_id from fee_transfer_request_lines where request_id = ?",
                Long.class, requestId);
    }

    private String currentName() {
        return jdbc.query("select coalesce(nullif(full_name, ''), username) from users where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, CurrentUser.id().orElse(-1L));
    }

    View view(Long yearId, Long id) {
        List<View> rows = jdbc.query("""
                select q.id, q.enrollment_id, st.student_code, st.full_name, sc.name, q.order_code, q.amount, q.status,
                       q.created_at, q.expires_at, q.paid_at, q.paid_by, q.created_by_name
                from fee_transfer_requests q
                join student_enrollments en on en.id = q.enrollment_id
                join students st on st.id = en.student_id
                left join school_classes sc on sc.id = en.school_class_id
                where q.id = ? and q.academic_year_id = ?
                """, (rs, n) -> {
            String status = rs.getString(8);
            BigDecimal amount = rs.getBigDecimal(7);
            String code = rs.getString(6);
            boolean open = "PENDING".equals(status) || "DEFERRED".equals(status);
            List<Receipt> receipts = "PAID".equals(status)
                    ? payments.receipts(yearId, jdbc.queryForList(
                    "select id from fee_payments where transfer_request_id = ? order by id", Long.class, rs.getLong(1)))
                    : List.of();
            return new View(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), code,
                    amount, status, rs.getObject(9, OffsetDateTime.class), rs.getObject(10, OffsetDateTime.class),
                    rs.getObject(11, OffsetDateTime.class), rs.getString(12), rs.getString(13),
                    open ? payments.transferInfo(code, amount) : null, receipts);
        }, id, yearId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("yêu cầu chuyển khoản", id);
        }
        return rows.get(0);
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }

}
