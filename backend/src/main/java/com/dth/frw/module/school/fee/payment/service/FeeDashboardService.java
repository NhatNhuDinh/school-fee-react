package com.dth.frw.module.school.fee.payment.service;

import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers on the home page: what a school year asks for, what has come in, and where it stands.
 *
 * <p>Read-only and cheap by design — every figure is a sum over rows that already exist. The
 * counters kept on {@code fee_collection_batches} answer the totals; the rest is grouped from the
 * receivables and the active receipts. A cancelled receipt never counts, here or anywhere.
 */
@Service
@Transactional(readOnly = true)
public class FeeDashboardService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_DAYS = 366;
    private static final int TOP_ITEMS = 4;

    public record Totals(BigDecimal receivable, BigDecimal collected, BigDecimal outstanding, BigDecimal cashCollected,
                         BigDecimal transferCollected, long batches, long studentsOwing, long review) {
    }

    /** One day of receipts, the zero days included so a chart has no gaps to bridge. */
    public record Day(LocalDate date, BigDecimal cash, BigDecimal transfer, long receipts) {
    }

    public record BatchProgress(long id, String name, BigDecimal receivable, BigDecimal collected) {
    }

    public record Slice(String name, BigDecimal amount) {
    }

    public record GroupProgress(String name, BigDecimal receivable, BigDecimal collected) {
    }

    public record ClassDebt(String name, BigDecimal outstanding) {
    }

    public record Recent(long id, long receiptNo, String studentName, String className, String method,
                         BigDecimal amount, OffsetDateTime createdAt) {
    }

    public record Dashboard(Totals totals, BigDecimal cash, BigDecimal transfer, List<Day> daily,
                            List<BatchProgress> batches, List<Slice> items, List<GroupProgress> groups,
                            List<ClassDebt> classes, List<Recent> recent) {
    }

    private final JdbcTemplate jdbc;
    private final AcademicYearService academicYearService;

    public FeeDashboardService(JdbcTemplate jdbc, AcademicYearService academicYearService) {
        this.jdbc = jdbc;
        this.academicYearService = academicYearService;
    }

    public Dashboard get(Long yearId, int days) {
        // The year is read through its service, so a year the caller cannot see is a 404 like everywhere else.
        Long organization = academicYearService.get(yearId).getOrganization().getId();
        int window = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = LocalDate.now(VN);

        Totals totals = totals(yearId, organization);
        List<Day> daily = daily(yearId, today, window);
        BigDecimal cash = daily.stream().map(Day::cash).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal transfer = daily.stream().map(Day::transfer).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Dashboard(totals, cash, transfer, daily, batches(yearId), items(yearId), groups(yearId),
                classes(yearId), recent(yearId));
    }

    private Totals totals(Long yearId, Long organization) {
        Map<String, Object> row = jdbc.queryForMap("""
                select coalesce(sum(receivable_amount), 0) as receivable, coalesce(sum(collected_amount), 0) as collected,
                       count(*) as batches
                from fee_collection_batches where academic_year_id = ?
                """, yearId);
        BigDecimal receivable = (BigDecimal) row.get("receivable");
        BigDecimal collected = (BigDecimal) row.get("collected");
        Long owing = jdbc.queryForObject("""
                select count(distinct bs.enrollment_id)
                from fee_collection_batch_students bs
                join fee_collection_batches b on b.id = bs.batch_id
                where b.academic_year_id = ? and bs.receivable_amount > bs.collected_amount
                """, Long.class, yearId);
        Long review = jdbc.queryForObject(
                "select count(*) from bank_transactions where organization_id = ? and status = 'NEEDS_REVIEW'",
                Long.class, organization);
        BigDecimal[] byMethod = {BigDecimal.ZERO, BigDecimal.ZERO};
        jdbc.query("select method, coalesce(sum(amount), 0) from fee_payments "
                + "where academic_year_id = ? and status = 'ACTIVE' group by method", rs -> {
            byMethod["CASH".equals(rs.getString(1)) ? 0 : 1] = rs.getBigDecimal(2);
        }, yearId);
        return new Totals(receivable, collected, receivable.subtract(collected).max(BigDecimal.ZERO), byMethod[0], byMethod[1],
                ((Number) row.get("batches")).longValue(), owing == null ? 0 : owing, review == null ? 0 : review);
    }

    private List<Day> daily(Long yearId, LocalDate today, int window) {
        LocalDate from = today.minusDays(window - 1L);
        Map<LocalDate, BigDecimal[]> sums = new HashMap<>();
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query("""
                select paid_on, method, sum(amount), count(*)
                from fee_payments
                where academic_year_id = ? and status = 'ACTIVE' and paid_on >= ? and paid_on <= ?
                group by paid_on, method
                """, rs -> {
            LocalDate date = rs.getDate(1).toLocalDate();
            BigDecimal[] pair = sums.computeIfAbsent(date, d -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            pair["CASH".equals(rs.getString(2)) ? 0 : 1] = rs.getBigDecimal(3);
            counts.merge(date, rs.getLong(4), Long::sum);
        }, yearId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(today));

        List<Day> days = new ArrayList<>(window);
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            BigDecimal[] pair = sums.getOrDefault(date, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            days.add(new Day(date, pair[0], pair[1], counts.getOrDefault(date, 0L)));
        }
        return days;
    }

    /** The latest đợt, so the page shows how the current ones are going rather than the whole year's history. */
    private List<BatchProgress> batches(Long yearId) {
        return jdbc.query("""
                select id, name, receivable_amount, collected_amount
                from fee_collection_batches
                where academic_year_id = ? and receivable_amount > 0
                order by created_at desc, id desc limit 5
                """, (rs, i) -> new BatchProgress(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4)), yearId);
    }

    /** What the year asks for, by khoản thu: the biggest few, and everything else together. */
    private List<Slice> items(Long yearId) {
        List<Slice> all = jdbc.query("""
                select bi.name, sum(r.receivable_amount)
                from student_fee_receivables r
                join fee_collection_batch_items bi on bi.id = r.batch_item_id
                join fee_collection_batches b on b.id = bi.batch_id
                where b.academic_year_id = ?
                group by bi.name
                having sum(r.receivable_amount) > 0
                order by sum(r.receivable_amount) desc
                """, (rs, i) -> new Slice(rs.getString(1), rs.getBigDecimal(2)), yearId);
        if (all.size() <= TOP_ITEMS + 1) {
            return all;
        }
        List<Slice> top = new ArrayList<>(all.subList(0, TOP_ITEMS));
        BigDecimal rest = all.subList(TOP_ITEMS, all.size()).stream().map(Slice::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        top.add(new Slice("Khác", rest));
        return top;
    }

    private List<GroupProgress> groups(Long yearId) {
        return jdbc.query("""
                select cg.name, sum(bs.receivable_amount), sum(bs.collected_amount)
                from fee_collection_batch_students bs
                join fee_collection_batches b on b.id = bs.batch_id
                join class_groups cg on cg.id = bs.class_group_id
                where b.academic_year_id = ?
                group by cg.id, cg.name
                having sum(bs.receivable_amount) > 0
                order by cg.name
                """, (rs, i) -> new GroupProgress(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), yearId);
    }

    private List<ClassDebt> classes(Long yearId) {
        return jdbc.query("""
                select sc.name, sum(bs.receivable_amount - bs.collected_amount) as owed
                from fee_collection_batch_students bs
                join fee_collection_batches b on b.id = bs.batch_id
                join school_classes sc on sc.id = bs.school_class_id
                where b.academic_year_id = ? and bs.receivable_amount > bs.collected_amount
                group by sc.id, sc.name
                order by owed desc, sc.name limit 5
                """, (rs, i) -> new ClassDebt(rs.getString(1), rs.getBigDecimal(2)), yearId);
    }

    private List<Recent> recent(Long yearId) {
        return jdbc.query("""
                select p.id, p.receipt_no, st.full_name, sc.name, p.method, p.amount, p.created_at
                from fee_payments p
                join fee_collection_batch_students bs on bs.id = p.batch_student_id
                join student_enrollments en on en.id = bs.enrollment_id
                join students st on st.id = en.student_id
                join school_classes sc on sc.id = bs.school_class_id
                where p.academic_year_id = ? and p.status = 'ACTIVE'
                order by p.created_at desc, p.id desc limit 5
                """, (rs, i) -> new Recent(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getBigDecimal(6), rs.getObject(7, OffsetDateTime.class)), yearId);
    }
}
