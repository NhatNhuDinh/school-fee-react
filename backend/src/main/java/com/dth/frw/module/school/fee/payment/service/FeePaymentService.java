package com.dth.frw.module.school.fee.payment.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchService;
import com.dth.frw.module.school.fee.notification.service.FeePaymentProperties;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.BatchDebt;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.ClassRow;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Collected;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.DebtLine;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.History;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Receipt;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.ReceiptLine;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentDebts;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentHit;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.TransferInfo;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thu tiền: taking what a student owes at the counter, in cash or by transfer.
 *
 * <p>Money is taken whole. There is no amount to type and no part payment: a receipt
 * settles every khoản thu it names for exactly what was still owed on it. A student
 * who owes in several đợt thu gets one receipt per đợt, because a receipt belongs to one
 * đợt and one student. A transfer is confirmed by hand ("đã nhận tiền") — nothing here
 * reads the bank.
 *
 * <p>Every read and write is scoped by an academic year the caller must be able to see
 * ({@link AcademicYearService#get} answers 404 otherwise), and every statement carries that
 * year, so a receivable id from another school finds nothing.
 */
@Service
@Transactional(readOnly = true)
public class FeePaymentService {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int HISTORY_LIMIT = 500;

    private static final String RECEIPT_SELECT = """
            select p.id, p.receipt_no, p.batch_id, b.name as batch_name, st.student_code, st.full_name,
                   sc.name as class_name, p.method, p.amount, p.paid_on, p.status, p.collector_name,
                   p.transfer_content, p.cancelled_by_name
            from fee_payments p
            join fee_collection_batches b on b.id = p.batch_id
            join fee_collection_batch_students bs on bs.id = p.batch_student_id
            join student_enrollments en on en.id = bs.enrollment_id
            join students st on st.id = en.student_id
            join school_classes sc on sc.id = bs.school_class_id
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AcademicYearService academicYearService;
    private final FeeCollectionBatchService batchService;
    private final FeePaymentProperties paymentProperties;
    private final SecureRandom random = new SecureRandom();

    public FeePaymentService(JdbcTemplate jdbc,
                             NamedParameterJdbcTemplate named,
                             AcademicYearService academicYearService,
                             FeeCollectionBatchService batchService,
                             FeePaymentProperties paymentProperties) {
        this.jdbc = jdbc;
        this.named = named;
        this.academicYearService = academicYearService;
        this.batchService = batchService;
        this.paymentProperties = paymentProperties;
    }

    // ---- by student

    /** Students of the year who owe something, matched on name or code. */
    public List<StudentHit> searchStudents(Long yearId, String term) {
        academicYearService.get(yearId);
        String q = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        MapSqlParameterSource args = new MapSqlParameterSource("year", yearId).addValue("q", "%" + q + "%");
        return named.query("""
                select en.id, st.student_code, st.full_name, st.date_of_birth, sc.name as class_name, cg.name as group_name,
                       sum(bs.receivable_amount - bs.collected_amount) as outstanding
                from fee_collection_batch_students bs
                join fee_collection_batches b on b.id = bs.batch_id
                join student_enrollments en on en.id = bs.enrollment_id
                join students st on st.id = en.student_id
                join school_classes sc on sc.id = bs.school_class_id
                join class_groups cg on cg.id = bs.class_group_id
                where b.academic_year_id = :year
                  and (lower(st.full_name) like :q or lower(st.student_code) like :q)
                group by en.id, st.student_code, st.full_name, st.date_of_birth, sc.name, cg.name
                having sum(bs.receivable_amount - bs.collected_amount) > 0
                order by st.full_name
                limit 30
                """, args, (rs, i) -> new StudentHit(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, LocalDate.class),
                rs.getString(5), rs.getString(6), rs.getBigDecimal(7)));
    }

    /** Every đợt the student is in, with the khoản thu owed in each. Paid-up đợt are included, greyed out by the screen. */
    public StudentDebts debts(Long yearId, Long enrollmentId) {
        academicYearService.get(yearId);
        List<Map<String, Object>> head = jdbc.queryForList("""
                select st.student_code, st.full_name, sc.name as class_name, cg.name as group_name
                from student_enrollments en
                join students st on st.id = en.student_id
                left join school_classes sc on sc.id = en.school_class_id
                left join class_groups cg on cg.id = sc.class_group_id
                where en.id = ? and en.academic_year_id = ?
                """, enrollmentId, yearId);
        if (head.isEmpty()) {
            throw new ResourceNotFoundException("học sinh", enrollmentId);
        }
        Map<Long, BatchDebt> byBatch = new LinkedHashMap<>();
        Map<Long, List<DebtLine>> lines = new LinkedHashMap<>();
        jdbc.query("""
                select b.id, b.name, b.scope, g.name, c.name, bs.id,
                       r.id, i.name, i.unit_name, r.quantity, r.receivable_amount, r.collected_amount
                from fee_collection_batch_students bs
                join fee_collection_batches b on b.id = bs.batch_id
                left join class_groups g on g.id = b.scope_class_group_id
                left join school_classes c on c.id = b.scope_class_id
                join student_fee_receivables r on r.batch_student_id = bs.id
                join fee_collection_batch_items i on i.id = r.batch_item_id
                where bs.enrollment_id = ? and b.academic_year_id = ?
                order by b.collection_month desc, b.id desc, i.sequence, i.id
                """, rs -> {
            long batchId = rs.getLong(1);
            String scope = rs.getString(3);
            String label = switch (scope == null ? "" : scope) {
                case "ALL_SCHOOL" -> "Toàn trường";
                case "CLASS_GROUP" -> rs.getString(4);
                case "CLASS" -> rs.getString(5);
                default -> "";
            };
            lines.computeIfAbsent(batchId, id -> new ArrayList<>());
            byBatch.putIfAbsent(batchId, new BatchDebt(batchId, rs.getString(2), label, rs.getLong(6),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
            BigDecimal receivable = rs.getBigDecimal(11);
            BigDecimal collected = rs.getBigDecimal(12);
            lines.get(batchId).add(new DebtLine(rs.getLong(7), rs.getString(8), rs.getString(9), rs.getInt(10),
                    receivable, collected, receivable.subtract(collected).max(BigDecimal.ZERO)));
        }, enrollmentId, yearId);

        List<BatchDebt> batches = new ArrayList<>();
        for (Map.Entry<Long, BatchDebt> entry : byBatch.entrySet()) {
            List<DebtLine> batchLines = lines.get(entry.getKey());
            BatchDebt b = entry.getValue();
            batches.add(new BatchDebt(b.batchId(), b.batchName(), b.scopeLabel(), b.batchStudentId(),
                    sum(batchLines, DebtLine::receivable), sum(batchLines, DebtLine::collected),
                    sum(batchLines, DebtLine::outstanding), batchLines));
        }
        Map<String, Object> h = head.get(0);
        return new StudentDebts(enrollmentId, (String) h.get("student_code"), (String) h.get("full_name"),
                (String) h.get("class_name"), (String) h.get("group_name"), batches);
    }

    /**
     * The bank account, a fresh transfer description and the QR that carries both, for the
     * amount about to be collected. The description is handed back when the receipt is made so
     * it is kept with it.
     */
    public TransferInfo transferInfo(Long yearId, BigDecimal amount) {
        academicYearService.get(yearId);
        String content = orderCode();
        if (!paymentProperties.configured()) {
            return new TransferInfo(false, null, null, null, content, amount, null);
        }
        FeePaymentProperties.BankAccount bank = paymentProperties.bankAccount();
        FeePaymentProperties.VietQr qr = paymentProperties.vietQr();
        String url = qr.baseUrl()
                + "?acc=" + encode(bank.number())
                + "&bank=" + encode(bank.bank())
                + "&amount=" + (amount == null ? "0" : amount.setScale(0, RoundingMode.HALF_UP).toPlainString())
                + "&des=" + encode(content)
                + "&template=" + encode(qr.template())
                + "&showinfo=" + encode(qr.showInfo())
                + "&holder=" + encode(bank.holder());
        return new TransferInfo(true, bank.bank(), bank.number(), bank.holder(), content, amount, url);
    }

    /**
     * Collects the named khoản thu of one student, each for what is still owed on it. Several
     * đợt give several receipts.
     */
    @Transactional
    public Collected collectStudent(Long yearId, Long enrollmentId, String method, LocalDate paidOn,
                                    Collection<Long> receivableIds, String transferContent) {
        AcademicYear year = academicYearService.get(yearId);
        requireMethod(method);
        if (receivableIds == null || receivableIds.isEmpty()) {
            throw invalid("receivableIds", "Chọn ít nhất một khoản thu");
        }
        List<Open> open = lockOpen(yearId, "r.id in (:ids) and bs.enrollment_id = :enrollment",
                new MapSqlParameterSource("ids", receivableIds).addValue("enrollment", enrollmentId));
        if (open.size() != new HashSet<>(receivableIds).size()) {
            throw new ConflictException("Có khoản thu đã được thu hoặc không còn trong đợt thu, hãy tải lại màn hình");
        }
        List<Receipt> receipts = settle(year, open, method, paidOn, transferContent);
        return new Collected(receipts, total(receipts), 1);
    }

    // ---- by class

    /** The classes of one đợt with what is owed, collected and left. */
    public List<ClassRow> classes(Long yearId, Long batchId) {
        academicYearService.get(yearId);
        batchService.get(batchId);
        return jdbc.query("""
                select sc.id, sc.name, cg.name, count(*),
                       count(*) filter (where bs.receivable_amount > bs.collected_amount),
                       sum(bs.receivable_amount), sum(bs.collected_amount)
                from fee_collection_batch_students bs
                join school_classes sc on sc.id = bs.school_class_id
                join class_groups cg on cg.id = bs.class_group_id
                where bs.batch_id = ?
                group by sc.id, sc.name, cg.name
                order by cg.name, sc.name
                """, (rs, i) -> {
            BigDecimal receivable = rs.getBigDecimal(6);
            BigDecimal collected = rs.getBigDecimal(7);
            return new ClassRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5),
                    receivable, collected, receivable.subtract(collected).max(BigDecimal.ZERO));
        }, batchId);
    }

    /** Collects everything still owed by every student of the chosen classes in a đợt, in cash. */
    @Transactional
    public Collected collectClasses(Long yearId, Long batchId, Collection<Long> classIds, LocalDate paidOn) {
        AcademicYear year = academicYearService.get(yearId);
        batchService.get(batchId);
        if (classIds == null || classIds.isEmpty()) {
            throw invalid("classIds", "Chọn ít nhất một lớp");
        }
        List<Open> open = lockOpen(yearId, "bs.batch_id = :batch and bs.school_class_id in (:classes)",
                new MapSqlParameterSource("batch", batchId).addValue("classes", classIds));
        if (open.isEmpty()) {
            throw new ConflictException("Các lớp đã chọn không còn khoản nào phải thu");
        }
        List<Receipt> receipts = settle(year, open, "CASH", paidOn, null);
        long students = open.stream().map(Open::batchStudentId).distinct().count();
        return new Collected(receipts, total(receipts), (int) students);
    }

    // ---- receipts

    public History history(Long yearId, LocalDate from, LocalDate to, Long batchId, String q) {
        academicYearService.get(yearId);
        MapSqlParameterSource args = new MapSqlParameterSource("year", yearId);
        StringBuilder where = new StringBuilder(" where p.academic_year_id = :year");
        if (from != null) {
            where.append(" and p.paid_on >= :from");
            args.addValue("from", from);
        }
        if (to != null) {
            where.append(" and p.paid_on <= :to");
            args.addValue("to", to);
        }
        if (batchId != null) {
            where.append(" and p.batch_id = :batch");
            args.addValue("batch", batchId);
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(st.full_name) like :q or lower(st.student_code) like :q)");
            args.addValue("q", "%" + q.trim().toLowerCase(Locale.ROOT) + "%");
        }
        String joins = " from fee_payments p"
                + " join fee_collection_batch_students bs on bs.id = p.batch_student_id"
                + " join student_enrollments en on en.id = bs.enrollment_id"
                + " join students st on st.id = en.student_id";
        Long total = named.queryForObject("select count(*)" + joins + where, args, Long.class);
        BigDecimal amount = named.queryForObject("select coalesce(sum(p.amount), 0)" + joins + where
                + " and p.status = 'ACTIVE'", args, BigDecimal.class);
        args.addValue("limit", HISTORY_LIMIT);
        List<Receipt> rows = named.query(RECEIPT_SELECT + where
                + " order by p.paid_on desc, p.id desc limit :limit", args, (rs, i) -> receipt(rs, List.of()));
        return new History(rows, total == null ? 0 : total, amount);
    }

    /** Receipts with their lines, for printing. */
    public List<Receipt> receipts(Long yearId, Collection<Long> ids) {
        academicYearService.get(yearId);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource args = new MapSqlParameterSource("year", yearId).addValue("ids", ids);
        return named.query(RECEIPT_SELECT + " where p.academic_year_id = :year and p.id in (:ids) order by p.id",
                args, (rs, i) -> receipt(rs, linesOf(rs.getLong("id"))));
    }

    /** Cancels receipts: each line gives its amount back to the khoản thu it settled. */
    @Transactional
    public int cancel(Long yearId, Collection<Long> ids) {
        academicYearService.get(yearId);
        if (ids == null || ids.isEmpty()) {
            throw invalid("ids", "Chọn phiếu thu cần hủy");
        }
        List<Map<String, Object>> payments = named.queryForList("""
                select id, batch_id, batch_student_id from fee_payments
                where academic_year_id = :year and id in (:ids) and status = 'ACTIVE' for update
                """, new MapSqlParameterSource("year", yearId).addValue("ids", ids));
        if (payments.size() != new HashSet<>(ids).size()) {
            throw new ConflictException("Có phiếu thu đã bị hủy hoặc không tồn tại, hãy tải lại màn hình");
        }
        String who = collectorName();
        Set<Long> batches = new LinkedHashSet<>();
        for (Map<String, Object> payment : payments) {
            long id = ((Number) payment.get("id")).longValue();
            long batchStudentId = ((Number) payment.get("batch_student_id")).longValue();
            jdbc.update("""
                    update student_fee_receivables r set collected_amount = greatest(r.collected_amount - l.amount, 0)
                    from fee_payment_lines l where l.payment_id = ? and l.receivable_id = r.id
                    """, id);
            jdbc.update("update fee_payments set status = 'CANCELLED', cancelled_at = now(), cancelled_by_name = ? "
                    + "where id = ?", who, id);
            jdbc.update("update fee_payment_requests set status = 'PENDING' "
                    + "where batch_student_id = ? and status = 'PAID'", batchStudentId);
            batches.add(((Number) payment.get("batch_id")).longValue());
        }
        batches.forEach(batchService::refreshTotals);
        return payments.size();
    }

    // ---- collecting

    private record Open(long receivableId, long batchId, String batchName, long batchStudentId, String itemName,
                        String unitName, int quantity, BigDecimal outstanding) {
    }

    /** Locks and reads the receivables that still owe something, under a condition on the batch student. */
    private List<Open> lockOpen(Long yearId, String condition, MapSqlParameterSource args) {
        args.addValue("year", yearId);
        return named.query("""
                select r.id, b.id, b.name, bs.id, i.name, i.unit_name, r.quantity,
                       r.receivable_amount - r.collected_amount as outstanding
                from student_fee_receivables r
                join fee_collection_batch_students bs on bs.id = r.batch_student_id
                join fee_collection_batches b on b.id = bs.batch_id
                join fee_collection_batch_items i on i.id = r.batch_item_id
                where b.academic_year_id = :year and r.receivable_amount > r.collected_amount and
                """ + condition + """
                 order by b.id, bs.id, i.sequence, i.id
                 for update of r
                """, args, (rs, n) -> new Open(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4),
                rs.getString(5), rs.getString(6), rs.getInt(7), rs.getBigDecimal(8)));
    }

    /** One receipt per (đợt, student); every receivable is brought up to its full amount. */
    private List<Receipt> settle(AcademicYear year, List<Open> open, String method, LocalDate paidOn,
                                 String transferContent) {
        long organizationId = year.getOrganization().getId();
        String who = collectorName();
        Long collectorId = CurrentUser.id().orElse(null);
        LocalDate date = paidOn == null ? LocalDate.now() : paidOn;
        if (date.isAfter(LocalDate.now())) {
            throw invalid("paidOn", "Ngày thu không được ở tương lai");
        }
        String content = "TRANSFER".equals(method) && transferContent != null && !transferContent.isBlank()
                ? transferContent.trim() : null;

        // Receipt numbers run per school; the lock makes the next number safe under concurrent counters.
        jdbc.queryForObject("select pg_advisory_xact_lock(?)::text", String.class, organizationId);
        Long last = jdbc.queryForObject(
                "select coalesce(max(receipt_no), 0) from fee_payments where organization_id = ?",
                Long.class, organizationId);
        long next = (last == null ? 0 : last) + 1;

        Map<Long, List<Open>> byStudent = new LinkedHashMap<>();
        for (Open row : open) {
            byStudent.computeIfAbsent(row.batchStudentId(), id -> new ArrayList<>()).add(row);
        }
        List<Long> paymentIds = new ArrayList<>();
        Set<Long> batches = new LinkedHashSet<>();
        for (Map.Entry<Long, List<Open>> entry : byStudent.entrySet()) {
            List<Open> rows = entry.getValue();
            BigDecimal amount = sum(rows, Open::outstanding);
            Long paymentId = jdbc.queryForObject("""
                    insert into fee_payments (organization_id, academic_year_id, batch_id, batch_student_id,
                        receipt_no, method, amount, paid_on, transfer_content, collector_id, collector_name)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, Long.class, organizationId, year.getId(), rows.get(0).batchId(), entry.getKey(),
                    next++, method, amount, date, content, collectorId, who);
            paymentIds.add(paymentId);
            batches.add(rows.get(0).batchId());
            for (Open row : rows) {
                jdbc.update("insert into fee_payment_lines (payment_id, receivable_id, amount) values (?, ?, ?)",
                        paymentId, row.receivableId(), row.outstanding());
                jdbc.update("update student_fee_receivables set collected_amount = receivable_amount where id = ?",
                        row.receivableId());
            }
            jdbc.update("""
                    update fee_payment_requests set status = 'PAID'
                    where batch_student_id = ? and status = 'PENDING' and not exists (
                        select 1 from student_fee_receivables r
                        where r.batch_student_id = ? and r.collected_amount < r.receivable_amount)
                    """, entry.getKey(), entry.getKey());
        }
        batches.forEach(batchService::refreshTotals);
        return named.query(RECEIPT_SELECT + " where p.id in (:ids) order by p.id",
                new MapSqlParameterSource("ids", paymentIds), (rs, i) -> receipt(rs, linesOf(rs.getLong("id"))));
    }

    // ---- helpers

    private static Receipt receipt(ResultSet rs, List<ReceiptLine> lines) throws SQLException {
        long no = rs.getLong("receipt_no");
        return new Receipt(rs.getLong("id"), no, "PT-" + String.format("%07d", no), rs.getLong("batch_id"),
                rs.getString("batch_name"), rs.getString("student_code"), rs.getString("full_name"),
                rs.getString("class_name"), rs.getString("method"), rs.getBigDecimal("amount"),
                rs.getObject("paid_on", LocalDate.class), rs.getString("status"), rs.getString("collector_name"),
                rs.getString("transfer_content"), rs.getString("cancelled_by_name"), lines);
    }

    private List<ReceiptLine> linesOf(long paymentId) {
        return jdbc.query("""
                select i.name, i.unit_name, r.quantity, l.amount
                from fee_payment_lines l
                join student_fee_receivables r on r.id = l.receivable_id
                join fee_collection_batch_items i on i.id = r.batch_item_id
                where l.payment_id = ? order by i.sequence, i.id
                """, (rs, n) -> new ReceiptLine(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBigDecimal(4)),
                paymentId);
    }

    private String collectorName() {
        return CurrentUser.id()
                .map(id -> jdbc.query("select coalesce(nullif(full_name, ''), username) from users where id = ?",
                        rs -> rs.next() ? rs.getString(1) : null, id))
                .orElseGet(() -> CurrentUser.username().orElse(null));
    }

    private String orderCode() {
        StringBuilder code = new StringBuilder(paymentProperties.orderCodePrefix().toUpperCase(Locale.ROOT)).append('-');
        for (int i = 0; i < 14; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static void requireMethod(String method) {
        if (!"CASH".equals(method) && !"TRANSFER".equals(method)) {
            throw invalid("method", "Hình thức thu không hợp lệ");
        }
    }

    private static BigDecimal total(List<Receipt> receipts) {
        return receipts.stream().map(Receipt::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }
}
