package com.dth.frw.module.school.fee.payment.sepay;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.fee.notification.service.FeePaymentProperties;
import com.dth.frw.module.school.fee.payment.service.FeePaymentService;
import com.dth.frw.module.school.fee.payment.service.FeePaymentService.SettleResult;
import com.dth.frw.module.school.fee.payment.service.FeePaymentService.Source;
import com.dth.frw.module.school.fee.payment.service.FeeTransferRequestService;
import com.dth.frw.module.school.fee.payment.service.FeeTransferRequestService.BankPayment;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What happens to a line the bank reports. Whether it came by webhook or by a manual check, it goes through
 * {@link #ingest}, and the order matters:
 *
 * <ol>
 *   <li><b>Record it first, in its own commit.</b> The line is written as {@code RECEIVED} before anything is
 *       decided, so a crash while deciding leaves a row to retry instead of money nobody knows about.</li>
 *   <li><b>Once.</b> A unique key on the line's content, not on the way it arrived, makes a retried webhook, or
 *       the same line seen again by a check, a no-op.</li>
 *   <li><b>Decide in one transaction.</b> A line is paid only when it is money in, into our account, carrying
 *       the code of exactly one request, for exactly the amount asked, for debts that are still owed. Anything
 *       else becomes {@code NEEDS_REVIEW} with a reason for a person to read — never a guess, never a part payment.</li>
 * </ol>
 */
@Service
public class BankTransactionService {

    private static final Logger log = LoggerFactory.getLogger(BankTransactionService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int CODE_SUFFIX = 14;
    private static final int MAX_ATTEMPTS = 5;

    public enum Outcome { DUPLICATE, MATCHED, NEEDS_REVIEW, IGNORED }

    public record IngestResult(Outcome outcome, Long transactionId) {
    }

    public record CheckSummary(int fetched, int created, int matched, int needsReview, int duplicates) {
    }

    /** A bank line as the review screen lists it. */
    public record Row(long id, OffsetDateTime time, BigDecimal amount, String content, String code,
                      String referenceCode, Long sepayId, String source, String status, String reason, String note,
                      Long suggestedEnrollmentId, String suggestedStudentCode, String suggestedStudentName,
                      String suggestedClassName, String handledBy, OffsetDateTime handledAt, boolean undoable) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final TransactionTemplate tx;
    private final AcademicYearService academicYearService;
    private final FeePaymentService payments;
    private final FeeTransferRequestService transferRequests;
    private final FeePaymentProperties paymentProperties;
    private final SepayProperties sepay;
    private final SepayClient client;
    private final Pattern codePattern;
    private final ApplicationEventPublisher events;

    public BankTransactionService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named,
                                  PlatformTransactionManager transactionManager,
                                  AcademicYearService academicYearService, FeePaymentService payments,
                                  FeeTransferRequestService transferRequests,
                                  FeePaymentProperties paymentProperties, SepayProperties sepay,
                                  SepayClient client, ApplicationEventPublisher events) {
        this.events = events;
        this.jdbc = jdbc;
        this.named = named;
        this.tx = new TransactionTemplate(transactionManager);
        this.academicYearService = academicYearService;
        this.payments = payments;
        this.transferRequests = transferRequests;
        this.paymentProperties = paymentProperties;
        this.sepay = sepay;
        this.client = client;
        this.codePattern = Pattern.compile(Pattern.quote(paymentProperties.orderCodePrefix().toUpperCase(Locale.ROOT))
                + "[0-9A-Z]{" + CODE_SUFFIX + "}");
    }

    // ---- ingest

    /** Takes one bank line through the steps above. Throws only when something is broken, so the caller can ask for a retry. */
    public IngestResult ingest(SepayPayload payload, String source) {
        String key = payload.dedupeKey();
        Long id = insertIfNew(payload, source, key);
        if (id == null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "select id, status from bank_transactions where dedupe_key = ? or (?::bigint is not null and sepay_id = ?)",
                    key, payload.id(), payload.id());
            if (existing.isEmpty()) {
                throw new IllegalStateException("bank transaction vanished between insert and lookup");
            }
            long existingId = ((Number) existing.get(0).get("id")).longValue();
            // The same line seen by the other source: remember how it is known there too.
            jdbc.update("update bank_transactions set sepay_id = coalesce(sepay_id, ?), sepay_uuid = coalesce(sepay_uuid, ?) "
                    + "where id = ?", payload.id(), payload.uuid(), existingId);
            if (!"RECEIVED".equals(existing.get(0).get("status"))) {
                return new IngestResult(Outcome.DUPLICATE, existingId);
            }
            id = existingId;
        }
        return new IngestResult(decide(id), id);
    }

    private Outcome decide(long id) {
        try {
            return tx.execute(status -> process(id));
        } catch (RuntimeException e) {
            jdbc.update("update bank_transactions set attempts = attempts + 1 where id = ?", id);
            throw e;
        }
    }

    private Long insertIfNew(SepayPayload p, String source, String key) {
        List<Long> ids = jdbc.query("""
                insert into bank_transactions (source, sepay_id, sepay_uuid, dedupe_key, gateway, account_number,
                    transaction_time, amount, transfer_type, content, code, reference_code, raw_payload)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing returning id
                """, (rs, n) -> rs.getLong(1), source, p.id(), p.uuid(), key, p.gateway(), p.accountNumber(),
                Timestamp.from(p.transactionDate().atZone(VN).toInstant()), p.amount(), p.transferType(),
                p.content(), p.code(), p.referenceCode(), p.raw());
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Outcome process(long id) {
        Map<String, Object> row = jdbc.queryForMap("select * from bank_transactions where id = ? for update", id);
        String status = (String) row.get("status");
        if (!"RECEIVED".equals(status)) {
            return "MATCHED".equals(status) || "RESOLVED".equals(status) ? Outcome.MATCHED
                    : "IGNORED".equals(status) ? Outcome.IGNORED : Outcome.NEEDS_REVIEW;
        }
        BigDecimal amount = (BigDecimal) row.get("amount");
        LocalDate paidOn = ((Timestamp) row.get("transaction_time")).toInstant().atZone(VN).toLocalDate();

        if (!"in".equals(row.get("transfer_type"))) {
            return finish(id, "IGNORED", "NOT_INCOMING", "Giao dịch tiền ra, không xử lý", null, null, null, null);
        }
        String ours = SepayPayload.digits(paymentProperties.bankAccount().number());
        if (!ours.isEmpty() && !ours.equals(SepayPayload.digits((String) row.get("account_number")))) {
            return finish(id, "IGNORED", "WRONG_ACCOUNT", "Không phải tài khoản thu của trường", null, null, null, null);
        }

        Set<String> codes = codesOf((String) row.get("content"), (String) row.get("code"));
        if (codes.isEmpty()) {
            return finish(id, "NEEDS_REVIEW", "NO_CODE", "Nội dung chuyển khoản không có mã thanh toán", null, null, null, null);
        }
        List<Long> transferIds = transferRequests.findByCodes(codes);
        List<Long> emailIds = named.queryForList(
                "select id from fee_payment_requests where replace(order_code, '-', '') in (:codes)",
                new MapSqlParameterSource("codes", codes), Long.class);
        // A counter request that borrowed an e-mail's code is that e-mail seen twice, not two requests: while the
        // counter's can still take the money it answers for both, and once it cannot the e-mail answers alone.
        if (!transferIds.isEmpty() && !emailIds.isEmpty()) {
            List<Long> answering = new ArrayList<>();
            List<Long> mailLeft = new ArrayList<>(emailIds);
            for (Long transferId : transferIds) {
                Long mail = transferRequests.linkedPaymentRequest(transferId);
                if (mail != null && mailLeft.contains(mail)) {
                    if (transferRequests.takesPayment(transferId)) {
                        mailLeft.remove(mail);
                        answering.add(transferId);
                    }
                } else {
                    answering.add(transferId);
                }
            }
            transferIds = answering;
            emailIds = mailLeft;
        }
        int found = transferIds.size() + emailIds.size();
        if (found == 0) {
            return finish(id, "NEEDS_REVIEW", "UNKNOWN_CODE", "Mã " + String.join(", ", codes) + " không khớp yêu cầu nào",
                    null, null, null, null);
        }
        if (found > 1) {
            return finish(id, "NEEDS_REVIEW", "AMBIGUOUS_CODE", "Mã khớp nhiều hơn một yêu cầu thanh toán", null, null, null, null);
        }

        if (!transferIds.isEmpty()) {
            long requestId = transferIds.get(0);
            Long org = jdbc.queryForObject("select organization_id from fee_transfer_requests where id = ?", Long.class, requestId);
            BankPayment result = transferRequests.payFromBank(requestId, amount, paidOn, id);
            return result.ok()
                    ? finish(id, "MATCHED", null, result.note(), org, requestId, null, result.enrollmentId())
                    : finish(id, "NEEDS_REVIEW", result.reason(), result.note(), org, requestId, null, result.enrollmentId());
        }
        return processEmailRequest(id, emailIds.get(0), amount, paidOn);
    }

    /** A request that went out in an e-mail: the parent paid from the QR in the message. */
    private Outcome processEmailRequest(long txId, long requestId, BigDecimal amount, LocalDate paidOn) {
        Map<String, Object> request = jdbc.queryForMap("select * from fee_payment_requests where id = ? for update", requestId);
        long organizationId = ((Number) request.get("organization_id")).longValue();
        long batchStudentId = ((Number) request.get("batch_student_id")).longValue();
        Long enrollmentId = jdbc.queryForObject("select enrollment_id from fee_collection_batch_students where id = ?",
                Long.class, batchStudentId);
        String status = (String) request.get("status");
        BigDecimal asked = (BigDecimal) request.get("requested_amount");

        if ("PAID".equals(status)) {
            return finish(txId, "NEEDS_REVIEW", "ALREADY_PAID", "Yêu cầu thanh toán này đã được thanh toán trước đó",
                    organizationId, null, requestId, enrollmentId);
        }
        if (!"PENDING".equals(status)) {
            return finish(txId, "NEEDS_REVIEW", "REQUEST_CANCELLED", "Yêu cầu thanh toán chưa có hiệu lực hoặc đã bị hủy",
                    organizationId, null, requestId, enrollmentId);
        }
        if (asked.compareTo(amount) != 0) {
            BigDecimal diff = amount.subtract(asked);
            return finish(txId, "NEEDS_REVIEW", "AMOUNT_MISMATCH", (diff.signum() < 0 ? "Thiếu " : "Thừa ")
                    + diff.abs().toPlainString() + " so với số tiền yêu cầu " + asked.toPlainString(),
                    organizationId, null, requestId, enrollmentId);
        }
        List<Long> open = jdbc.queryForList("select id from student_fee_receivables where batch_student_id = ? "
                + "and receivable_amount > collected_amount order by id", Long.class, batchStudentId);
        if (open.isEmpty()) {
            return finish(txId, "NEEDS_REVIEW", "ALREADY_PAID", "Học sinh đã đóng đủ các khoản trong đợt này",
                    organizationId, null, requestId, enrollmentId);
        }
        long yearId = ((Number) request.get("academic_year_id")).longValue();
        SettleResult settled = payments.settleReceivables(academicYearService.get(yearId), open, asked, "TRANSFER", paidOn,
                (String) request.get("order_code"), new Source("SePay (tự động)", null, null, txId));
        if (!settled.ok()) {
            return finish(txId, "NEEDS_REVIEW", "DEBT_CHANGED", settled.problem(), organizationId, null, requestId, enrollmentId);
        }
        jdbc.update("update fee_payment_requests set status = 'PAID' where id = ?", requestId);
        return finish(txId, "MATCHED", null, null, organizationId, null, requestId, enrollmentId);
    }

    private Outcome finish(long id, String status, String reason, String note, Long organizationId,
                           Long transferRequestId, Long paymentRequestId, Long enrollmentId) {
        Long org = organizationId != null ? organizationId : defaultOrganization();
        jdbc.update("""
                update bank_transactions set status = ?, reason = ?, note = ?, organization_id = coalesce(?, organization_id),
                    transfer_request_id = ?, payment_request_id = ?, suggested_enrollment_id = ? where id = ?
                """, status, reason, note, org, transferRequestId, paymentRequestId, enrollmentId, id);
        if (org != null && "NEEDS_REVIEW".equals(status)) {
            events.publishEvent(new com.dth.frw.module.school.fee.payment.service.TransferPaymentPublisher.ReviewChanged(org));
        }
        return switch (status) {
            case "MATCHED" -> Outcome.MATCHED;
            case "IGNORED" -> Outcome.IGNORED;
            default -> Outcome.NEEDS_REVIEW;
        };
    }

    /**
     * Whose a line is when nothing matched it. A deployment is one school's, so normally the only school there is;
     * {@code school-fee.sepay.organization-code} names one explicitly for a database that holds several.
     */
    private Long defaultOrganization() {
        String code = sepay.organizationCode();
        if (code != null && !code.isBlank()) {
            List<Long> named = jdbc.queryForList("select id from organizations where code = ?", Long.class, code.trim());
            return named.isEmpty() ? null : named.get(0);
        }
        List<Long> all = jdbc.queryForList("select id from organizations limit 2", Long.class);
        return all.size() == 1 ? all.get(0) : null;
    }

    /** Every order code in the memo or in the code SePay extracted, however the bank punctuated it. */
    Set<String> codesOf(String content, String code) {
        Set<String> codes = new LinkedHashSet<>();
        for (String text : new String[] {SepayPayload.alnum(content), SepayPayload.alnum(code)}) {
            Matcher matcher = codePattern.matcher(text);
            while (matcher.find()) {
                codes.add(matcher.group());
            }
        }
        return codes;
    }

    // ---- catching up

    /** "Kiểm tra giao dịch": read the last days of incoming lines from SePay and take each through {@link #ingest}. */
    public CheckSummary check(Long yearId, Integer days) {
        academicYearService.get(yearId);
        int window = days == null || days < 1 ? sepay.checkWindowDays() : Math.min(days, 31);
        LocalDateTime from = LocalDate.now(VN).minusDays(window).atStartOfDay();
        String ours = SepayPayload.digits(paymentProperties.bankAccount().number());
        int fetched = 0;
        int created = 0;
        int matched = 0;
        int review = 0;
        int duplicates = 0;
        for (SepayPayload payload : client.incomingSince(from)) {
            if (!ours.isEmpty() && !ours.equals(SepayPayload.digits(payload.accountNumber()))) {
                continue;
            }
            fetched++;
            IngestResult result = ingest(payload, "API_CHECK");
            switch (result.outcome()) {
                case DUPLICATE -> duplicates++;
                case MATCHED -> {
                    created++;
                    matched++;
                }
                case NEEDS_REVIEW -> {
                    created++;
                    review++;
                }
                case IGNORED -> created++;
            }
        }
        retryReceived();
        return new CheckSummary(fetched, created, matched, review, duplicates);
    }

    /**
     * Lines left in {@code RECEIVED}: a deciding transaction that failed. Tried again every minute; after
     * {@value #MAX_ATTEMPTS} failures a line goes to review rather than looping for ever.
     */
    @Scheduled(fixedDelayString = "${school-fee.sepay.retry-interval-ms:60000}", initialDelay = 30_000)
    public void retryReceived() {
        List<Map<String, Object>> stuck = jdbc.queryForList("select id, attempts from bank_transactions "
                + "where status = 'RECEIVED' and created_at < now() - interval '20 seconds' order by id limit 50");
        for (Map<String, Object> row : stuck) {
            long id = ((Number) row.get("id")).longValue();
            try {
                if (((Number) row.get("attempts")).intValue() >= MAX_ATTEMPTS) {
                    tx.executeWithoutResult(s -> finish(id, "NEEDS_REVIEW", "PROCESSING_ERROR",
                            "Xử lý tự động thất bại nhiều lần, cần kiểm tra thủ công", null, null, null, null));
                } else {
                    decide(id);
                }
            } catch (RuntimeException e) {
                log.warn("bank transaction {} could not be decided: {}", id, e.toString());
            }
        }
    }

    // ---- the review screen

    private static final String ALL_STATUSES = "('NEEDS_REVIEW', 'MATCHED', 'RESOLVED', 'IGNORED')";
    private static final String DONE_STATUSES = "('MATCHED', 'RESOLVED', 'IGNORED')";
    private static final Set<String> LISTED = Set.of("NEEDS_REVIEW", "MATCHED", "RESOLVED", "IGNORED");

    /** What a line is joined to, so the search can read the student it was suggested for. */
    private static final String LINE_JOINS = """
             from bank_transactions t
             left join student_enrollments en on en.id = t.suggested_enrollment_id
             left join students st on st.id = en.student_id
             left join school_classes sc on sc.id = en.school_class_id
            """;

    private record Filter(StringBuilder where, MapSqlParameterSource args) {
    }

    /** The conditions the date range and the search box put on a list, shared by the rows and the tab counts. */
    private Filter filter(Long org, LocalDate from, LocalDate to, String q) {
        MapSqlParameterSource args = new MapSqlParameterSource("org", org);
        StringBuilder where = new StringBuilder(" where t.organization_id = :org");
        if (from != null) {
            where.append(" and t.transaction_time >= :from");
            args.addValue("from", Timestamp.from(from.atStartOfDay(VN).toInstant()));
        }
        if (to != null) {
            where.append(" and t.transaction_time < :to");
            args.addValue("to", Timestamp.from(to.plusDays(1).atStartOfDay(VN).toInstant()));
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(t.content) like :q or lower(t.reference_code) like :q or t.sepay_id::text like :q"
                    + " or lower(st.full_name) like :q or lower(st.student_code) like :q)");
            args.addValue("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
        return new Filter(where, args);
    }

    public List<Row> list(Long yearId, String tab, LocalDate from, LocalDate to, String q) {
        return list(yearId, tab, from, to, q, null);
    }

    /**
     * The lines of the review screen. {@code tab}: {@code all} (every line), {@code processed} (matched, assigned or
     * set aside) or {@code review} (waiting for a person). {@code status} narrows either one to a single status.
     */
    public List<Row> list(Long yearId, String tab, LocalDate from, LocalDate to, String q, String status) {
        Long org = academicYearService.get(yearId).getOrganization().getId();
        Filter f = filter(org, from, to, q);
        f.where().append(" and t.status in ").append("review".equals(tab) ? "('NEEDS_REVIEW')"
                : "all".equals(tab) ? ALL_STATUSES : DONE_STATUSES);
        if (status != null && LISTED.contains(status)) {
            f.where().append(" and t.status = :status");
            f.args().addValue("status", status);
        }
        return named.query("""
                select t.id, t.transaction_time, t.amount, t.content, t.code, t.reference_code, t.sepay_id, t.source,
                       t.status, t.reason, t.note, t.suggested_enrollment_id, st.student_code, st.full_name, sc.name,
                       t.handled_by_name, t.handled_at,
                       (t.status = 'RESOLVED' and t.reason = 'MANUAL_ASSIGNED')
                           or (t.status = 'IGNORED' and t.reason = 'MANUAL_IGNORED')
                """ + LINE_JOINS + f.where() + " order by t.transaction_time desc, t.id desc limit 300", f.args(),
                (rs, n) -> new Row(rs.getLong(1), rs.getObject(2, OffsetDateTime.class), rs.getBigDecimal(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), (Long) rs.getObject(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), (Long) rs.getObject(12), rs.getString(13),
                        rs.getString(14), rs.getString(15), rs.getString(16), rs.getObject(17, OffsetDateTime.class),
                        rs.getBoolean(18)));
    }

    /** How many lines each tab holds under the same date range and search, so the tab labels match the list. */
    public Map<String, Long> counts(Long yearId, LocalDate from, LocalDate to, String q) {
        Long org = academicYearService.get(yearId).getOrganization().getId();
        Filter f = filter(org, from, to, q);
        return named.queryForObject("select count(*) filter (where t.status in " + ALL_STATUSES + "), "
                + "count(*) filter (where t.status in " + DONE_STATUSES + "), "
                + "count(*) filter (where t.status = 'NEEDS_REVIEW') " + LINE_JOINS + f.where(), f.args(),
                (rs, n) -> Map.of("all", rs.getLong(1), "processed", rs.getLong(2), "review", rs.getLong(3)));
    }

    public long reviewCount(Long yearId) {
        Long org = academicYearService.get(yearId).getOrganization().getId();
        Long count = jdbc.queryForObject(
                "select count(*) from bank_transactions where organization_id = ? and status = 'NEEDS_REVIEW'", Long.class, org);
        return count == null ? 0 : count;
    }

    /** "Bỏ qua": nothing to collect for this line (a refund to make, a transfer meant for something else). */
    @Transactional
    public void ignore(Long yearId, Long id, String note) {
        academicYearService.get(yearId);
        Map<String, Object> row = lockForReview(yearId, id);
        jdbc.update("update bank_transactions set status = 'IGNORED', reason = 'MANUAL_IGNORED', note = ?, "
                + "handled_by_name = ?, handled_at = now() where id = ?",
                note == null || note.isBlank() ? row.get("note") : note.trim(), currentName(), id);
        events.publishEvent(new com.dth.frw.module.school.fee.payment.service.TransferPaymentPublisher.ReviewChanged(((Number) row.get("organization_id")).longValue()));
    }

    /**
     * "Gán thủ công": the accountant says which student's khoản thu this line pays for. Money is still
     * taken whole, so the chosen khoản must come to exactly the line's amount — a person can point at the
     * right debts, not make the figures agree.
     */
    @Transactional
    public void assign(Long yearId, Long id, Long enrollmentId, List<Long> receivableIds) {
        AcademicYear year = academicYearService.get(yearId);
        Map<String, Object> row = lockForReview(yearId, id);
        if (receivableIds == null || receivableIds.isEmpty()) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", Map.of("receivableIds", "Chọn ít nhất một khoản thu"));
        }
        Collection<Long> ids = new HashSet<>(receivableIds);
        Long belonging = named.queryForObject("""
                select count(*) from student_fee_receivables r
                join fee_collection_batch_students bs on bs.id = r.batch_student_id
                where r.id in (:ids) and bs.enrollment_id = :enrollment
                """, new MapSqlParameterSource("ids", ids).addValue("enrollment", enrollmentId), Long.class);
        if (belonging == null || belonging != ids.size()) {
            throw new ConflictException("Có khoản thu không thuộc học sinh đã chọn");
        }
        BigDecimal amount = (BigDecimal) row.get("amount");
        LocalDate paidOn = ((Timestamp) row.get("transaction_time")).toInstant().atZone(VN).toLocalDate();
        String content = (String) row.get("content");
        SettleResult result = payments.settleReceivables(year, ids, amount, "TRANSFER", paidOn,
                content == null ? null : content.substring(0, Math.min(60, content.length())),
                new Source(currentName(), CurrentUser.id().orElse(null), null, id));
        if (!result.ok()) {
            throw new ConflictException(result.problem() + ". Các khoản đã chọn phải đúng bằng số tiền giao dịch ("
                    + amount.toPlainString() + ")");
        }
        jdbc.update("update bank_transactions set status = 'RESOLVED', reason = 'MANUAL_ASSIGNED', note = null, handled_by_name = ?, "
                + "handled_at = now(), suggested_enrollment_id = ? where id = ?", currentName(), enrollmentId, id);
        events.publishEvent(new com.dth.frw.module.school.fee.payment.service.TransferPaymentPublisher.ReviewChanged(((Number) row.get("organization_id")).longValue()));
    }

    /**
     * "Hoàn tác": takes back what a person did to a line, for when it was done by mistake. Setting a line aside has
     * nothing to give back; assigning it made a receipt, which is cancelled so the student owes what they owed
     * before. Either way the line returns to review. A line the system matched itself is not undone from here:
     * nobody pressed anything, and its receipt is cancelled where receipts are.
     */
    @Transactional
    public void undo(Long yearId, Long id) {
        Long org = academicYearService.get(yearId).getOrganization().getId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from bank_transactions where id = ? and organization_id = ? for update", id, org);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("giao dịch", id);
        }
        String status = (String) rows.get(0).get("status");
        String reason = (String) rows.get(0).get("reason");
        if ("RESOLVED".equals(status) && "MANUAL_ASSIGNED".equals(reason)) {
            List<Long> receipts = jdbc.queryForList(
                    "select id from fee_payments where bank_transaction_id = ? and status = 'ACTIVE'", Long.class, id);
            if (!receipts.isEmpty()) {
                payments.cancel(yearId, receipts);
            }
        } else if (!("IGNORED".equals(status) && "MANUAL_IGNORED".equals(reason))) {
            throw new ConflictException("Chỉ hoàn tác được giao dịch đã gán thủ công hoặc đã bỏ qua");
        }
        jdbc.update("update bank_transactions set status = 'NEEDS_REVIEW', reason = 'UNDONE', "
                + "handled_by_name = null, handled_at = null where id = ?", id);
        events.publishEvent(new com.dth.frw.module.school.fee.payment.service.TransferPaymentPublisher.ReviewChanged(org));
    }

    private Map<String, Object> lockForReview(Long yearId, Long id) {
        Long org = academicYearService.get(yearId).getOrganization().getId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from bank_transactions where id = ? and organization_id = ? for update", id, org);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("giao dịch", id);
        }
        if (!"NEEDS_REVIEW".equals(rows.get(0).get("status"))) {
            throw new ConflictException("Giao dịch này đã được xử lý, hãy tải lại màn hình");
        }
        return rows.get(0);
    }

    private String currentName() {
        return jdbc.query("select coalesce(nullif(full_name, ''), username) from users where id = ?",
                rs -> rs.next() ? rs.getString(1) : null, CurrentUser.id().orElse(-1L));
    }
}
