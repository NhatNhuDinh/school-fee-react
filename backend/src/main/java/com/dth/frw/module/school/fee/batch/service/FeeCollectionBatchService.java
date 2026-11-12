package com.dth.frw.module.school.fee.batch.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.domain.school.fee.FeeCollectionBatchItem;
import com.dth.frw.domain.school.fee.FeeCollectionBatchStudent;
import com.dth.frw.domain.school.fee.FeeCollectionScope;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeeItemPrice;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.school.academic.classgroup.service.ClassGroupService;
import com.dth.frw.module.school.academic.schoolclass.service.SchoolClassService;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.fee.batch.repo.FeeCollectionBatchItemRepository;
import com.dth.frw.module.school.fee.batch.repo.FeeCollectionBatchRepository;
import com.dth.frw.module.school.fee.batch.repo.FeeCollectionBatchSpecifications;
import com.dth.frw.module.school.fee.batch.repo.FeeCollectionBatchStudentRepository;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Amount;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Candidate;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.ItemTotals;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Line;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Preview;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.StudentRows;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.SyncResult;
import com.dth.frw.module.school.fee.item.service.FeeItemService;
import com.dth.frw.module.school.fee.registration.service.FeeRegistrationService;
import com.dth.frw.module.school.student.service.StudentService;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Đợt thu: a month's collection of khoản thu from a group of students.
 *
 * <p>The rules are the ones the Jmix application's batch service held. A đợt names a
 * month and a scope (the whole school, a khối lớp or a lớp); its students are the
 * year's enrollments that have a lớp and fall in the scope; its khoản thu are chosen
 * from those that are still followed and due in the month. A khoản thu reaches a student
 * when its own scope reaches the student's lớp and — if it is optional — the student is
 * registered for it. The unit price is the single price, or the line of the price list
 * for the student's khối and area; the amount is that price times the quantity, which is
 * the same for everyone. The status is never stored: it is read off the money.
 *
 * <p>Receivables are written in bulk through JDBC (a đợt for a whole school is thousands
 * of rows, and an identity key forbids batching them through the persistence context),
 * after which the totals are recomputed in SQL and the context is cleared so the next
 * read sees them. Nothing here collects money: that is a later step, and until then every
 * đợt is {@code NOT_STARTED}.
 */
@Service
@Transactional(readOnly = true)
public class FeeCollectionBatchService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of(
            "id", "name", "collectionMonth", "studentCount", "feeItemCount",
            "receivableAmount", "collectedAmount", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private static final Set<String> STUDENT_SORTABLE = Set.of("receivableAmount", "collectedAmount");
    private static final Sort STUDENT_SORT = Sort.by(
            Sort.Order.asc("schoolClass.name"),
            Sort.Order.asc("enrollment.student.fullName"),
            Sort.Order.asc("id"));

    /** A khoản thu measured in these is charged once per period, so its quantity is always 1. */
    private static final Set<String> PERIOD_UNITS = Set.of("thang", "quy", "ky", "nam");

    private static final int CHUNK = 500;
    private static final int PAGE = 1_000;
    private static final String RESOURCE = "đợt thu";

    private final FeeCollectionBatchRepository repository;
    private final FeeCollectionBatchItemRepository itemRepository;
    private final FeeCollectionBatchStudentRepository studentRepository;
    private final AcademicYearService academicYearService;
    private final ClassGroupService classGroupService;
    private final SchoolClassService schoolClassService;
    private final StudentService studentService;
    private final FeeItemService feeItemService;
    private final FeeRegistrationService registrationService;
    private final RowLevelSecurity rowLevel;
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final ObjectProvider<FeeCollectionBatchLock> locks;

    public FeeCollectionBatchService(FeeCollectionBatchRepository repository,
                                     FeeCollectionBatchItemRepository itemRepository,
                                     FeeCollectionBatchStudentRepository studentRepository,
                                     AcademicYearService academicYearService,
                                     ClassGroupService classGroupService,
                                     SchoolClassService schoolClassService,
                                     StudentService studentService,
                                     FeeItemService feeItemService,
                                     FeeRegistrationService registrationService,
                                     RowLevelSecurity rowLevel,
                                     JdbcTemplate jdbc,
                                     EntityManager em,
                                     ObjectProvider<FeeCollectionBatchLock> locks) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.studentRepository = studentRepository;
        this.academicYearService = academicYearService;
        this.classGroupService = classGroupService;
        this.schoolClassService = schoolClassService;
        this.studentService = studentService;
        this.feeItemService = feeItemService;
        this.registrationService = registrationService;
        this.rowLevel = rowLevel;
        this.jdbc = jdbc;
        this.em = em;
        this.locks = locks;
    }

    // ---- reads

    public Page<FeeCollectionBatch> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(FeeCollectionBatch.class, Action.READ).and(FeeCollectionBatchSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public FeeCollectionBatch get(Long id) {
        return repository.findOne(rowLevel.byId(FeeCollectionBatch.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<FeeCollectionBatch> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(FeeCollectionBatch.class, Action.READ, ids));
    }

    /** The khoản thu of a đợt, each with what it comes to over the students charged for it. */
    public List<ItemTotals> items(Long batchId) {
        get(batchId);
        List<FeeCollectionBatchItem> items = itemRepository.findByBatchIdOrderBySequenceAsc(batchId);
        Map<Long, Object[]> totals = new HashMap<>();
        jdbc.query("""
                select r.batch_item_id, count(*), sum(r.receivable_amount), sum(r.collected_amount),
                       min(r.unit_price), max(r.unit_price)
                from student_fee_receivables r
                join fee_collection_batch_items i on i.id = r.batch_item_id
                where i.batch_id = ?
                group by r.batch_item_id
                """, rs -> {
            totals.put(rs.getLong(1), new Object[] {
                    rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6)});
        }, batchId);
        List<ItemTotals> result = new ArrayList<>();
        for (FeeCollectionBatchItem item : items) {
            Object[] t = totals.get(item.getId());
            result.add(t == null
                    ? new ItemTotals(item, 0, BigDecimal.ZERO, BigDecimal.ZERO, null, null)
                    : new ItemTotals(item, (Integer) t[0], (BigDecimal) t[1], (BigDecimal) t[2],
                            (BigDecimal) t[3], (BigDecimal) t[4]));
        }
        return result;
    }

    /**
     * A page of the students of a đợt with each one's amount per khoản thu. Filters:
     * {@code schoolClass.id}, {@code debt} ({@code owing}, {@code paid} or {@code any})
     * and {@code q} over the student's name and code.
     */
    public StudentRows students(Long batchId, ListParams params) {
        get(batchId);
        List<Specification<FeeCollectionBatchStudent>> specs = new ArrayList<>();
        specs.add(FeeCollectionBatchStudentRepository.ofBatch(batchId));
        for (ListParams.Criterion criterion : params.filters()) {
            switch (criterion.field()) {
                case "schoolClass.id", "schoolClassId" -> {
                    Long classId = parseLong(criterion.value());
                    specs.add(classId == null
                            ? (root, query, cb) -> cb.disjunction()
                            : (root, query, cb) -> cb.equal(root.get("schoolClass").get("id"), classId));
                }
                case "debt" -> {
                    String debt = criterion.value().trim().toLowerCase(Locale.ROOT);
                    if (debt.equals("owing")) {
                        specs.add((root, query, cb) -> cb.greaterThan(
                                root.<BigDecimal>get("receivableAmount"), root.<BigDecimal>get("collectedAmount")));
                    } else if (debt.equals("paid")) {
                        specs.add((root, query, cb) -> cb.and(
                                cb.greaterThan(root.<BigDecimal>get("receivableAmount"), BigDecimal.ZERO),
                                cb.lessThanOrEqualTo(
                                        root.<BigDecimal>get("receivableAmount"), root.<BigDecimal>get("collectedAmount"))));
                    }
                }
                default -> {
                    // Unknown fields narrow nothing, like everywhere else.
                }
            }
        }
        params.search().ifPresent(term -> specs.add((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("enrollment").get("student").get("fullName")), ListParams.likePattern(term)),
                cb.like(cb.lower(root.get("enrollment").get("student").get("studentCode")), ListParams.likePattern(term)))));

        Page<FeeCollectionBatchStudent> page = studentRepository.findAll(
                Specification.allOf(specs), params.pageable(STUDENT_SORTABLE, STUDENT_SORT));
        return new StudentRows(page, amountsOf(page.getContent()));
    }

    private Map<Long, Map<Long, Amount>> amountsOf(List<FeeCollectionBatchStudent> students) {
        Map<Long, Map<Long, Amount>> result = new HashMap<>();
        if (students.isEmpty()) {
            return result;
        }
        String marks = students.stream().map(s -> "?").collect(Collectors.joining(","));
        jdbc.query("select batch_student_id, batch_item_id, receivable_amount, collected_amount "
                        + "from student_fee_receivables where batch_student_id in (" + marks + ")",
                rs -> {
                    result.computeIfAbsent(rs.getLong(1), id -> new HashMap<>())
                            .put(rs.getLong(2), new Amount(rs.getBigDecimal(3), rs.getBigDecimal(4)));
                },
                students.stream().map(FeeCollectionBatchStudent::getId).toArray());
        return result;
    }

    // ---- preview

    /**
     * What a đợt thu would be, without making it: the students it would reach, the
     * khoản thu that can be put in it (with whom each reaches and at what price), and
     * what the chosen ones come to. The same planning {@link #create} runs, so the figure
     * shown is the figure that will be written.
     */
    public Preview preview(Long academicYearId, int month, FeeCollectionScope scope,
                           Long classGroupId, Long schoolClassId, List<Line> lines) {
        AcademicYear year = academicYearService.get(academicYearId);
        Target target = target(year, scope, classGroupId, schoolClassId);
        Plan plan = plan(year, month, target);
        Map<Long, Line> chosen = new LinkedHashMap<>();
        for (Line line : lines) {
            chosen.putIfAbsent(line.feeItemId(), line);
        }

        List<Candidate> candidates = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int selected = 0;
        for (Applicable candidate : plan.candidates()) {
            FeeItem item = candidate.item();
            Line line = chosen.get(item.getId());
            boolean editable = !isPeriodUnit(item.getMeasurementUnit().getName());
            int quantity = line == null || !editable ? 1 : Math.max(line.quantity(), 1);
            Pricing pricing = pricing(item, candidate.students());
            BigDecimal amount = pricing.sum.multiply(BigDecimal.valueOf(quantity));
            if (line != null) {
                selected++;
                total = total.add(amount);
                if (pricing.missing > 0) {
                    problems.add(missingPriceMessage(item, pricing));
                }
            }
            candidates.add(new Candidate(item, candidate.students().size(), pricing.min, pricing.max,
                    pricing.missing, editable, line != null, quantity, amount));
        }
        return new Preview(plan.students().size(), selected, total, candidates, problems);
    }

    // ---- create

    @Transactional
    public FeeCollectionBatch create(Long academicYearId, String rawName, int month, FeeCollectionScope scope,
                                     Long classGroupId, Long schoolClassId, List<Line> lines) {
        // Read through the year's own scope: a year the caller cannot see is a 404.
        AcademicYear year = academicYearService.get(academicYearId);
        String name = rawName.trim();
        requireNameAvailable(year, name);
        Target target = target(year, scope, classGroupId, schoolClassId);
        Plan plan = plan(year, month, target);

        if (plan.students().isEmpty()) {
            throw invalid("scope", "Phạm vi đã chọn chưa có học sinh nào được xếp lớp");
        }
        Map<Long, Applicable> byItem = plan.candidates().stream()
                .collect(Collectors.toMap(c -> c.item().getId(), Function.identity()));
        List<Chosen> chosen = chooseLines(lines, byItem);

        FeeCollectionBatch batch = new FeeCollectionBatch(year, name, month);
        batch.setScope(target.scope());
        batch.setScopeClassGroup(target.group());
        batch.setScopeClass(target.schoolClass());
        FeeCollectionBatch saved = repository.persist(batch);
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);

        int sequence = 1;
        List<FeeCollectionBatchItem> batchItems = new ArrayList<>();
        for (Chosen c : chosen) {
            batchItems.add(itemRepository.save(new FeeCollectionBatchItem(saved, c.applicable().item(), c.quantity(), sequence++)));
        }
        Map<Long, FeeCollectionBatchStudent> batchStudents = new HashMap<>();
        for (StudentEnrollment enrollment : plan.students()) {
            batchStudents.put(enrollment.getId(), studentRepository.save(new FeeCollectionBatchStudent(saved, enrollment)));
        }
        em.flush();

        List<NewReceivable> rows = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            Chosen c = chosen.get(i);
            Long batchItemId = batchItems.get(i).getId();
            for (StudentEnrollment enrollment : c.applicable().students()) {
                BigDecimal price = priceFor(c.applicable().item(), enrollment.getSchoolClass().getClassGroup(),
                        enrollment.getStudent().getArea());
                rows.add(new NewReceivable(batchItemId, batchStudents.get(enrollment.getId()).getId(), price, c.quantity()));
            }
        }
        insertReceivables(rows);
        recompute(saved.getId());
        return get(saved.getId());
    }

    // ---- update the khoản thu of a đợt

    /**
     * Brings the khoản thu of a đợt to the set sent. A khoản thu already in it keeps its
     * receivables, and a new quantity reprices them; one that is no longer wanted is
     * dropped; a new one is charged to the students already in the đợt (the students are
     * fixed when it is made). Nothing is refused halfway: every check runs before the
     * first write.
     */
    @Transactional
    public FeeCollectionBatch updateItems(Long batchId, List<Line> lines) {
        FeeCollectionBatch batch = repository.findOne(rowLevel.byId(FeeCollectionBatch.class, Action.WRITE, batchId))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, batchId));
        requireNotNotified(batch);
        if (lines.isEmpty()) {
            throw invalid("items", "Đợt thu cần có ít nhất một khoản thu");
        }
        Map<Long, Line> wanted = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.quantity() < 1) {
                throw invalid("items", "Số lượng phải từ 1 trở lên");
            }
            if (wanted.putIfAbsent(line.feeItemId(), line) != null) {
                throw invalid("items", "Một khoản thu chỉ được chọn một lần trong đợt");
            }
        }
        List<FeeCollectionBatchItem> current = itemRepository.findByBatchIdOrderBySequenceAsc(batchId);
        Map<Long, FeeCollectionBatchItem> currentByFee = current.stream()
                .collect(Collectors.toMap(i -> i.getFeeItem().getId(), Function.identity()));

        // 1. Checks, all before any write.
        List<FeeCollectionBatchItem> toRemove = new ArrayList<>();
        for (FeeCollectionBatchItem item : current) {
            if (!wanted.containsKey(item.getFeeItem().getId())) {
                if (collectedOf(item.getId()).signum() > 0) {
                    throw invalid("items", "Khoản thu “" + item.getName() + "” đã thu tiền nên không thể bỏ khỏi đợt");
                }
                toRemove.add(item);
            }
        }
        Map<FeeCollectionBatchItem, Integer> requantify = new LinkedHashMap<>();
        for (FeeCollectionBatchItem item : current) {
            Line line = wanted.get(item.getFeeItem().getId());
            if (line == null || isPeriodUnit(item.getFeeItem().getMeasurementUnit().getName())) {
                continue;
            }
            if (line.quantity() != item.getQuantity()) {
                Integer tooLow = jdbc.queryForObject(
                        "select count(*) from student_fee_receivables "
                                + "where batch_item_id = ? and collected_amount > unit_price * ?",
                        Integer.class, item.getId(), line.quantity());
                if (tooLow != null && tooLow > 0) {
                    throw invalid("items", "Số lượng mới của “" + item.getName()
                            + "” làm phải thu thấp hơn số đã thu của " + tooLow + " học sinh");
                }
                requantify.put(item, line.quantity());
            }
        }
        List<FeeCollectionBatchStudent> batchStudents = studentRepository.findByBatchId(batchId);
        List<NewItem> additions = new ArrayList<>();
        Plan plan = planFor(batch);
        Map<Long, Applicable> byItem = plan.candidates().stream()
                .collect(Collectors.toMap(c -> c.item().getId(), Function.identity()));
        Set<Long> optionalIds = new HashSet<>();
        for (Line line : wanted.values()) {
            if (!currentByFee.containsKey(line.feeItemId())) {
                Applicable applicable = byItem.get(line.feeItemId());
                if (applicable == null) {
                    throw invalid("items", "Có khoản thu không còn áp dụng được cho đợt thu này");
                }
                if (!applicable.item().isMandatory()) {
                    optionalIds.add(applicable.item().getId());
                }
                additions.add(new NewItem(applicable, isPeriodUnit(applicable.item().getMeasurementUnit().getName())
                        ? 1 : line.quantity()));
            }
        }
        Map<Long, Set<Long>> registered = registrationService.registeredEnrollments(optionalIds);
        Map<NewItem, List<FeeCollectionBatchStudent>> reached = new LinkedHashMap<>();
        for (NewItem addition : additions) {
            FeeItem item = addition.applicable().item();
            List<FeeCollectionBatchStudent> students = batchStudents.stream()
                    .filter(s -> reaches(item, s.getClassGroup().getId(), s.getSchoolClass().getId()))
                    .filter(s -> item.isMandatory()
                            || registered.getOrDefault(item.getId(), Set.of()).contains(s.getEnrollment().getId()))
                    .toList();
            Pricing pricing = pricing(item, students.stream().map(FeeCollectionBatchStudent::getEnrollment).toList());
            if (pricing.missing > 0) {
                throw invalid("items", missingPriceMessage(item, pricing));
            }
            reached.put(addition, students);
        }

        // 2. Writes.
        for (FeeCollectionBatchItem item : toRemove) {
            itemRepository.delete(item);
        }
        em.flush();
        for (Map.Entry<FeeCollectionBatchItem, Integer> entry : requantify.entrySet()) {
            entry.getKey().setQuantity(entry.getValue());
            jdbc.update("update student_fee_receivables set quantity = ?, receivable_amount = unit_price * ? "
                    + "where batch_item_id = ?", entry.getValue(), entry.getValue(), entry.getKey().getId());
        }
        em.flush();
        int sequence = current.stream().mapToInt(FeeCollectionBatchItem::getSequence).max().orElse(0);
        List<NewReceivable> rows = new ArrayList<>();
        for (Map.Entry<NewItem, List<FeeCollectionBatchStudent>> entry : reached.entrySet()) {
            FeeItem item = entry.getKey().applicable().item();
            FeeCollectionBatchItem created = itemRepository.save(
                    new FeeCollectionBatchItem(batch, item, entry.getKey().quantity(), ++sequence));
            em.flush();
            for (FeeCollectionBatchStudent student : entry.getValue()) {
                BigDecimal price = priceFor(item, student.getClassGroup(), student.getEnrollment().getStudent().getArea());
                rows.add(new NewReceivable(created.getId(), student.getId(), price, entry.getKey().quantity()));
            }
        }
        insertReceivables(rows);
        rowLevel.checkWritable(batch);
        recompute(batchId);
        return get(batchId);
    }

    // ---- sync with registrations

    /**
     * Brings the optional khoản thu of a đợt in line with who is registered now: a student
     * registered since is charged, one no longer registered is not — unless money has been
     * collected for it, in which case the receivable stays (a collected amount is never
     * taken away). Mandatory khoản thu do not depend on registration and are left alone.
     */
    @Transactional
    public SyncResult syncRegistrations(Long batchId) {
        FeeCollectionBatch batch = repository.findOne(rowLevel.byId(FeeCollectionBatch.class, Action.WRITE, batchId))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, batchId));
        requireNotNotified(batch);
        List<FeeCollectionBatchItem> optional = itemRepository.findByBatchIdOrderBySequenceAsc(batchId).stream()
                .filter(i -> !i.getFeeItem().isMandatory())
                .toList();
        if (optional.isEmpty()) {
            return new SyncResult(0, 0, 0);
        }
        List<FeeCollectionBatchStudent> students = studentRepository.findByBatchId(batchId);
        Map<Long, Set<Long>> registered = registrationService.registeredEnrollments(
                optional.stream().map(i -> i.getFeeItem().getId()).toList());

        // What exists now: (item, student) -> collected amount.
        Map<String, BigDecimal> existing = new HashMap<>();
        String marks = optional.stream().map(i -> "?").collect(Collectors.joining(","));
        jdbc.query("select batch_item_id, batch_student_id, collected_amount from student_fee_receivables "
                        + "where batch_item_id in (" + marks + ")",
                rs -> {
                    existing.put(rs.getLong(1) + ":" + rs.getLong(2), rs.getBigDecimal(3));
                },
                optional.stream().map(FeeCollectionBatchItem::getId).toArray());

        List<NewReceivable> toAdd = new ArrayList<>();
        List<Object[]> toRemove = new ArrayList<>();
        int kept = 0;
        for (FeeCollectionBatchItem batchItem : optional) {
            FeeItem item = batchItem.getFeeItem();
            Set<Long> registeredHere = registered.getOrDefault(item.getId(), Set.of());
            for (FeeCollectionBatchStudent student : students) {
                boolean applies = reaches(item, student.getClassGroup().getId(), student.getSchoolClass().getId())
                        && registeredHere.contains(student.getEnrollment().getId());
                String key = batchItem.getId() + ":" + student.getId();
                BigDecimal collected = existing.get(key);
                if (applies && collected == null) {
                    BigDecimal price = priceFor(item, student.getClassGroup(), student.getEnrollment().getStudent().getArea());
                    if (price == null) {
                        throw invalid("items", "Thiếu đơn giá khoản thu “" + item.getName() + "” cho khối “"
                                + student.getClassGroup().getName() + "”");
                    }
                    toAdd.add(new NewReceivable(batchItem.getId(), student.getId(), price, batchItem.getQuantity()));
                } else if (!applies && collected != null) {
                    if (collected.signum() > 0) {
                        kept++;
                    } else {
                        toRemove.add(new Object[] {batchItem.getId(), student.getId()});
                    }
                }
            }
        }
        if (!toRemove.isEmpty()) {
            jdbc.batchUpdate("delete from student_fee_receivables where batch_item_id = ? and batch_student_id = ?",
                    toRemove);
        }
        insertReceivables(toAdd);
        rowLevel.checkWritable(batch);
        recompute(batchId);
        return new SyncResult(toAdd.size(), toRemove.size(), kept);
    }

    // ---- delete

    /** Removes a đợt nothing has been collected for; its rows go with it. */
    @Transactional
    public FeeCollectionBatch delete(Long id) {
        FeeCollectionBatch batch = repository.findOne(rowLevel.byId(FeeCollectionBatch.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        if (batch.getCollectedAmount().signum() > 0) {
            throw new ConflictException("Đợt thu “" + batch.getName() + "” đã thu tiền nên không thể xóa");
        }
        if (locks.stream().anyMatch(lock -> lock.hasNotifications(id))) {
            throw new ConflictException("Đợt thu “" + batch.getName() + "” đã có thông báo nên không thể xóa");
        }
        repository.remove(batch);
        return batch;
    }

    // ---- planning

    private record Target(FeeCollectionScope scope, ClassGroup group, SchoolClass schoolClass) {
    }

    /** A khoản thu that can go in the đợt, and the students it would charge. */
    private record Applicable(FeeItem item, List<StudentEnrollment> students) {
    }

    private record Plan(List<StudentEnrollment> students, List<Applicable> candidates) {
    }

    private record Chosen(Applicable applicable, int quantity) {
    }

    private record NewItem(Applicable applicable, int quantity) {
    }

    private record NewReceivable(Long batchItemId, Long batchStudentId, BigDecimal unitPrice, int quantity) {
    }

    /** The price a khoản thu charges over a set of students, and where it is missing. */
    private static final class Pricing {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min;
        BigDecimal max;
        int missing;
        String firstMissingGroup;
        String firstMissingArea;
    }

    private Target target(AcademicYear year, FeeCollectionScope scope, Long classGroupId, Long schoolClassId) {
        return switch (scope) {
            case ALL_SCHOOL -> new Target(scope, null, null);
            case CLASS_GROUP -> {
                if (classGroupId == null) {
                    throw invalid("scopeClassGroup", "Vui lòng chọn khối");
                }
                ClassGroup group = classGroupService.get(classGroupId);
                requireSameYear(year, group.getAcademicYear(), "Khối");
                yield new Target(scope, group, null);
            }
            case CLASS -> {
                if (schoolClassId == null) {
                    throw invalid("scopeClass", "Vui lòng chọn lớp");
                }
                SchoolClass schoolClass = schoolClassService.get(schoolClassId);
                requireSameYear(year, schoolClass.getClassGroup().getAcademicYear(), "Lớp");
                yield new Target(scope, null, schoolClass);
            }
        };
    }

    private static void requireSameYear(AcademicYear year, AcademicYear other, String what) {
        if (!year.getId().equals(other.getId())) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ",
                    Map.of("scope", what + " không thuộc năm học " + year.getName()));
        }
    }

    private Plan planFor(FeeCollectionBatch batch) {
        return plan(batch.getAcademicYear(), batch.getCollectionMonth(),
                new Target(batch.getScope(), batch.getScopeClassGroup(), batch.getScopeClass()));
    }

    /**
     * The students a đợt of this month and scope would reach, and the khoản thu it could
     * collect: those due in the month that reach at least one of those students.
     */
    private Plan plan(AcademicYear year, int month, Target target) {
        List<StudentEnrollment> students = studentsOf(year, target);
        List<FeeItem> due = feeItemService.dueIn(year.getId(), month).stream()
                .filter(i -> i.getScope() != FeeScope.UNASSIGNED)
                .toList();
        Set<Long> optionalIds = due.stream().filter(i -> !i.isMandatory()).map(FeeItem::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> registered = registrationService.registeredEnrollments(optionalIds);

        List<Applicable> candidates = new ArrayList<>();
        for (FeeItem item : due) {
            Set<Long> registeredHere = registered.getOrDefault(item.getId(), Set.of());
            List<StudentEnrollment> reached = students.stream()
                    .filter(s -> reaches(item, s.getSchoolClass().getClassGroup().getId(), s.getSchoolClass().getId()))
                    .filter(s -> item.isMandatory() || registeredHere.contains(s.getId()))
                    .toList();
            if (!reached.isEmpty()) {
                candidates.add(new Applicable(item, reached));
            }
        }
        return new Plan(students, candidates);
    }

    /** Every enrollment of the year that has a lớp and falls in the target, in the student list's order. */
    private List<StudentEnrollment> studentsOf(AcademicYear year, Target target) {
        List<StudentEnrollment> result = new ArrayList<>();
        int start = 0;
        while (true) {
            LinkedMultiValueMap<String, String> query = new LinkedMultiValueMap<>();
            query.add("academicYear.id", String.valueOf(year.getId()));
            if (target.group() != null) {
                query.add("classGroup.id", String.valueOf(target.group().getId()));
            }
            if (target.schoolClass() != null) {
                query.add("schoolClass.id", String.valueOf(target.schoolClass().getId()));
            }
            query.add("_start", String.valueOf(start));
            query.add("_end", String.valueOf(start + PAGE));
            Page<StudentEnrollment> page = studentService.list(new ListParams(query));
            page.getContent().stream().filter(e -> e.getSchoolClass() != null).forEach(result::add);
            if (!page.hasNext()) {
                return result;
            }
            start += PAGE;
        }
    }

    private List<Chosen> chooseLines(List<Line> lines, Map<Long, Applicable> byItem) {
        if (lines == null || lines.isEmpty()) {
            throw invalid("items", "Vui lòng chọn ít nhất một khoản thu");
        }
        Set<Long> seen = new HashSet<>();
        List<Chosen> chosen = new ArrayList<>();
        for (Line line : lines) {
            if (!seen.add(line.feeItemId())) {
                throw invalid("items", "Một khoản thu chỉ được chọn một lần trong đợt");
            }
            if (line.quantity() < 1) {
                throw invalid("items", "Số lượng phải từ 1 trở lên");
            }
            Applicable applicable = byItem.get(line.feeItemId());
            if (applicable == null) {
                throw invalid("items", "Có khoản thu không đến hạn trong tháng này hoặc không áp dụng cho phạm vi đã chọn");
            }
            Pricing pricing = pricing(applicable.item(), applicable.students());
            if (pricing.missing > 0) {
                throw invalid("items", missingPriceMessage(applicable.item(), pricing));
            }
            int quantity = isPeriodUnit(applicable.item().getMeasurementUnit().getName()) ? 1 : line.quantity();
            chosen.add(new Chosen(applicable, quantity));
        }
        return chosen;
    }

    // ---- pricing

    private Pricing pricing(FeeItem item, List<StudentEnrollment> students) {
        Pricing pricing = new Pricing();
        for (StudentEnrollment student : students) {
            ClassGroup group = student.getSchoolClass().getClassGroup();
            BigDecimal price = priceFor(item, group, student.getStudent().getArea());
            if (price == null) {
                if (pricing.missing++ == 0) {
                    pricing.firstMissingGroup = group.getName();
                    pricing.firstMissingArea = student.getStudent().getArea();
                }
                continue;
            }
            pricing.sum = pricing.sum.add(price);
            pricing.min = pricing.min == null || price.compareTo(pricing.min) < 0 ? price : pricing.min;
            pricing.max = pricing.max == null || price.compareTo(pricing.max) > 0 ? price : pricing.max;
        }
        return pricing;
    }

    /**
     * The unit price of a khoản thu for a student of this khối and area: the single price, or
     * the price-list line for the khối whose area matches (ignoring case and accents), else
     * the line with no area, else the khối's only line. {@code null} when none applies.
     */
    private static BigDecimal priceFor(FeeItem item, ClassGroup group, String area) {
        if (item.getPriceMode() == FeePriceMode.SINGLE) {
            return item.getBasePrice();
        }
        List<FeeItemPrice> ofGroup = item.getPrices().stream()
                .filter(p -> p.getClassGroup().getId().equals(group.getId()))
                .toList();
        if (ofGroup.isEmpty()) {
            return null;
        }
        String wanted = normalize(area);
        if (!wanted.isEmpty()) {
            for (FeeItemPrice price : ofGroup) {
                if (normalize(price.getAreaName()).equals(wanted)) {
                    return price.getAmount();
                }
            }
        }
        for (FeeItemPrice price : ofGroup) {
            if (normalize(price.getAreaName()).isEmpty()) {
                return price.getAmount();
            }
        }
        return ofGroup.size() == 1 ? ofGroup.get(0).getAmount() : null;
    }

    private static String missingPriceMessage(FeeItem item, Pricing pricing) {
        String area = pricing.firstMissingArea == null || pricing.firstMissingArea.isBlank()
                ? "" : " (khu vực " + pricing.firstMissingArea.trim() + ")";
        return "Thiếu đơn giá khoản thu “" + item.getName() + "” cho khối “" + pricing.firstMissingGroup + "”" + area
                + (pricing.missing > 1 ? ", và " + (pricing.missing - 1) + " học sinh khác" : "");
    }

    private static boolean reaches(FeeItem item, Long classGroupId, Long schoolClassId) {
        return switch (item.getScope()) {
            case ALL_SCHOOL -> true;
            case CLASS_GROUP -> item.getScopeClassGroup() != null && item.getScopeClassGroup().getId().equals(classGroupId);
            case CLASS -> item.getScopeClass() != null && item.getScopeClass().getId().equals(schoolClassId);
            case UNASSIGNED -> false;
        };
    }

    /** True for a unit that counts periods (tháng, quý, kỳ, năm), whose quantity is therefore always 1. */
    private static boolean isPeriodUnit(String unitName) {
        for (String token : normalize(unitName).split("\\s+")) {
            if (PERIOD_UNITS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return folded.toLowerCase(Locale.ROOT);
    }

    // ---- writes

    private void insertReceivables(List<NewReceivable> rows) {
        if (rows.isEmpty()) {
            return;
        }
        em.flush();
        jdbc.batchUpdate("""
                insert into student_fee_receivables
                    (batch_item_id, batch_student_id, unit_price, quantity, receivable_amount, collected_amount)
                values (?, ?, ?, ?, ?, 0)
                """, rows, CHUNK, (ps, row) -> {
            ps.setLong(1, row.batchItemId());
            ps.setLong(2, row.batchStudentId());
            ps.setBigDecimal(3, row.unitPrice());
            ps.setInt(4, row.quantity());
            ps.setBigDecimal(5, row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())));
        });
    }

    /** Recomputes the totals from the receivables, then clears the context so the next read sees them. */
    private void recompute(Long batchId) {
        em.flush();
        jdbc.update("""
                update fee_collection_batch_students s set
                    receivable_amount = coalesce((select sum(r.receivable_amount)
                        from student_fee_receivables r where r.batch_student_id = s.id), 0),
                    collected_amount = coalesce((select sum(r.collected_amount)
                        from student_fee_receivables r where r.batch_student_id = s.id), 0)
                where s.batch_id = ?
                """, batchId);
        jdbc.update("""
                update fee_collection_batches b set
                    fee_item_count = (select count(*) from fee_collection_batch_items i where i.batch_id = b.id),
                    student_count = (select count(*) from fee_collection_batch_students s where s.batch_id = b.id),
                    receivable_amount = coalesce((select sum(s.receivable_amount)
                        from fee_collection_batch_students s where s.batch_id = b.id), 0),
                    collected_amount = coalesce((select sum(s.collected_amount)
                        from fee_collection_batch_students s where s.batch_id = b.id), 0),
                    updated_at = now()
                where b.id = ?
                """, batchId);
        em.clear();
    }

    private BigDecimal collectedOf(Long batchItemId) {
        BigDecimal collected = jdbc.queryForObject(
                "select coalesce(sum(collected_amount), 0) from student_fee_receivables where batch_item_id = ?",
                BigDecimal.class, batchItemId);
        return collected == null ? BigDecimal.ZERO : collected;
    }

    // ---- helpers

    /**
     * Checked before the entity is built, so the unique index never answers first with the
     * generic "still in use" message.
     */
    private void requireNameAvailable(AcademicYear year, String name) {
        if (repository.findByAcademicYearIdAndName(year.getId(), name).isPresent()) {
            throw new ConflictException("Đã có đợt thu “" + name + "” trong năm học " + year.getName());
        }
    }

    /**
     * Once a notification has been scheduled, parents have been told what they owe: changing
     * the khoản thu, or following the registrations, would make the figure they were given wrong.
     */
    private void requireNotNotified(FeeCollectionBatch batch) {
        if (locks.stream().anyMatch(lock -> lock.locksChanges(batch.getId()))) {
            throw new ConflictException("Đợt thu “" + batch.getName()
                    + "” đã có thông báo gửi cho phụ huynh nên không thể đổi khoản thu");
        }
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
