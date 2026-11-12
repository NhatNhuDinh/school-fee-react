package com.dth.frw.module.school.fee.item.service;

import com.dth.frw.domain.school.academic.AcademicPeriod;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.domain.school.fee.FeeCollectionCycle;
import com.dth.frw.domain.school.fee.FeeCollectionSchedule;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeeItemPrice;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.module.school.academic.classgroup.service.ClassGroupService;
import com.dth.frw.module.school.academic.schoolclass.service.SchoolClassService;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.school.catalog.measurementunit.service.MeasurementUnitService;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemPatchRequest;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.PriceRow;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.Ref;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.ScheduleRow;
import com.dth.frw.module.school.fee.item.repo.FeeItemRepository;
import com.dth.frw.module.school.fee.item.repo.FeeItemSpecifications;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The khoản thu of a school year. A name is unique within its year, as the Jmix
 * application enforced with its tenant, year and name constraint; the school is
 * the year's own.
 *
 * <p>The rules are the ones {@code FeeItemValidationService} and
 * {@code FeeItemFormService} held: a single price needs its amount and a price
 * list needs at least one line, a scope names the khối lớp or lớp it is for, and
 * every cycle but monthly has collection dates that must fall inside the quarter,
 * the academic period or the year they stand for.
 *
 * <p>Everything that needs a lookup is done before the entity is touched, so the
 * auto-flush cannot push a half-built update ahead of the check and let a unique
 * index answer instead; what remains afterwards is checked in memory.
 */
@Service
@Transactional(readOnly = true)
public class FeeItemService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE =
            Set.of("id", "name", "basePrice", "vatRate", "collectionCycle", "active", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "khoản thu";

    private final FeeItemRepository repository;
    private final AcademicYearService academicYearService;
    private final ClassGroupService classGroupService;
    private final SchoolClassService schoolClassService;
    private final MeasurementUnitService measurementUnitService;
    private final RowLevelSecurity rowLevel;

    public FeeItemService(FeeItemRepository repository,
                          AcademicYearService academicYearService,
                          ClassGroupService classGroupService,
                          SchoolClassService schoolClassService,
                          MeasurementUnitService measurementUnitService,
                          RowLevelSecurity rowLevel) {
        this.repository = repository;
        this.academicYearService = academicYearService;
        this.classGroupService = classGroupService;
        this.schoolClassService = schoolClassService;
        this.measurementUnitService = measurementUnitService;
        this.rowLevel = rowLevel;
    }

    public Page<FeeItem> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(FeeItem.class, Action.READ).and(FeeItemSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public FeeItem get(Long id) {
        return repository.findOne(rowLevel.byId(FeeItem.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<FeeItem> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(FeeItem.class, Action.READ, ids));
    }

    /**
     * The khoản thu a student of this lớp may register for: the optional ones (not
     * mandatory — nobody registers for those, everybody pays them), still followed,
     * of the lớp's own year, and whose scope reaches the lớp — the whole school, its
     * khối, or this lớp itself. A khoản thu for another khối or another lớp, and one
     * for students not yet in a class, is not a column of this lớp's matrix.
     */
    public List<FeeItem> optionalFor(SchoolClass schoolClass) {
        ClassGroup group = schoolClass.getClassGroup();
        Long yearId = group.getAcademicYear().getId();
        Specification<FeeItem> reachesClass = (root, query, cb) -> cb.and(
                cb.equal(root.get("academicYear").get("id"), yearId),
                cb.isFalse(root.get("mandatory")),
                cb.isTrue(root.get("active")),
                cb.or(
                        cb.equal(root.get("scope"), FeeScope.ALL_SCHOOL),
                        cb.and(
                                cb.equal(root.get("scope"), FeeScope.CLASS_GROUP),
                                cb.equal(root.get("scopeClassGroup").get("id"), group.getId())),
                        cb.and(
                                cb.equal(root.get("scope"), FeeScope.CLASS),
                                cb.equal(root.get("scopeClass").get("id"), schoolClass.getId()))));
        return repository.findAll(
                rowLevel.scope(FeeItem.class, Action.READ).and(reachesClass),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    /**
     * The khoản thu a đợt thu of this month can collect: still followed, of the year,
     * and due in the month — every month for a monthly one, and the months of its
     * collection dates otherwise. Whom each reaches is for the caller to decide.
     */
    public List<FeeItem> dueIn(Long academicYearId, int month) {
        Specification<FeeItem> ofYear = (root, query, cb) -> cb.and(
                cb.equal(root.get("academicYear").get("id"), academicYearId),
                cb.isTrue(root.get("active")));
        return repository.findAll(
                        rowLevel.scope(FeeItem.class, Action.READ).and(ofYear),
                        Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
                .stream()
                .filter(item -> item.getCollectionCycle() == FeeCollectionCycle.MONTHLY
                        || item.getCollectionSchedules().stream().anyMatch(s -> s.getCollectionMonth() == month))
                .toList();
    }

    @Transactional
    public FeeItem create(FeeItemRequest request) {
        // Read through the year's own scope: a year the caller cannot see is a 404.
        AcademicYear year = academicYearService.get(request.academicYear().id());
        String name = request.name().trim();
        requireNameAvailable(year, name, null);
        MeasurementUnit unit = unitOf(request.measurementUnit().id(), year);

        FeeItem row = new FeeItem(year, name, unit);
        apply(row, Draft.of(request), unit);

        FeeItem saved = repository.persist(row);
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public FeeItem update(Long id, FeeItemPatchRequest request) {
        FeeItem row = repository.findOne(rowLevel.byId(FeeItem.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        AcademicYear year = row.getAcademicYear();

        Draft draft = Draft.of(request);
        if (draft.name() != null) {
            requireNameAvailable(year, draft.name().trim(), row.getId());
        }
        MeasurementUnit unit = draft.measurementUnitId() == null ? null : unitOf(draft.measurementUnitId(), year);
        apply(row, draft, unit);

        rowLevel.checkWritable(row);
        return repository.persist(row);
    }

    @Transactional
    public FeeItem delete(Long id) {
        FeeItem row = repository.findOne(rowLevel.byId(FeeItem.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        repository.remove(row);
        return row;
    }

    // ---- applying a draft

    /**
     * Writes a create or a patch onto the row, then checks the row as a whole.
     * A {@code null} in the draft leaves that field as it is, which is how a patch
     * works and costs a create nothing: its required fields are already checked.
     */
    private void apply(FeeItem row, Draft draft, MeasurementUnit unit) {
        AcademicYear year = row.getAcademicYear();

        // Lookups first (see the class comment).
        ClassGroup scopeGroup = draft.scopeClassGroupId() == null ? null : groupOf(draft.scopeClassGroupId(), year);
        SchoolClass scopeClass = draft.scopeClassId() == null ? null : classOf(draft.scopeClassId(), year);
        List<FeeItemPrice> prices = draft.prices() == null ? null : buildPrices(row, draft.prices(), year);

        Map<String, Object> errors = new HashMap<>();

        if (draft.name() != null) {
            row.setName(draft.name().trim());
        }
        if (unit != null) {
            row.setMeasurementUnit(unit);
        }
        if (draft.scope() != null) {
            row.setScope(draft.scope());
        }
        if (scopeGroup != null) {
            row.setScopeClassGroup(scopeGroup);
        }
        if (scopeClass != null) {
            row.setScopeClass(scopeClass);
        }
        if (draft.priceMode() != null) {
            row.setPriceMode(draft.priceMode());
        }
        if (draft.basePrice() != null) {
            row.setBasePrice(draft.basePrice());
        }
        if (draft.vatRate() != null) {
            row.setVatRate(draft.vatRate());
        }
        if (draft.collectionCycle() != null) {
            row.setCollectionCycle(draft.collectionCycle());
        }
        if (draft.discountEligible() != null) {
            row.setDiscountEligible(draft.discountEligible());
        }
        if (draft.mandatory() != null) {
            row.setMandatory(draft.mandatory());
        }
        if (draft.serviceFee() != null) {
            row.setServiceFee(draft.serviceFee());
        }
        if (draft.refundable() != null) {
            row.setRefundable(draft.refundable());
        }
        if (draft.invoiceEnabled() != null) {
            row.setInvoiceEnabled(draft.invoiceEnabled());
        }
        if (draft.receiptEnabled() != null) {
            row.setReceiptEnabled(draft.receiptEnabled());
        }
        if (draft.internalCollection() != null) {
            row.setInternalCollection(draft.internalCollection());
        }
        if (draft.invoiceRate() != null) {
            row.setInvoiceRate(draft.invoiceRate());
        }
        if (draft.active() != null) {
            row.setActive(draft.active());
        }
        if (prices != null) {
            row.replacePrices(prices);
        }

        normalizeScope(row, errors);
        normalizePrice(row, errors);
        normalizeSchedules(row, draft.collectionSchedules(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", errors);
        }
    }

    /** The scope names exactly the record it needs; anything else it might still hold is dropped. */
    private static void normalizeScope(FeeItem row, Map<String, Object> errors) {
        FeeScope scope = row.getScope();
        if (scope != FeeScope.CLASS_GROUP) {
            row.setScopeClassGroup(null);
        } else if (row.getScopeClassGroup() == null) {
            errors.put("scopeClassGroup", "Vui lòng chọn khối áp dụng");
        }
        if (scope != FeeScope.CLASS) {
            row.setScopeClass(null);
        } else if (row.getScopeClass() == null) {
            errors.put("scopeClass", "Vui lòng chọn lớp áp dụng");
        }
    }

    /**
     * One price, or a list of them — never both. The one that does not apply is
     * cleared so a stale amount cannot outlive the mode it belonged to.
     */
    private static void normalizePrice(FeeItem row, Map<String, Object> errors) {
        if (row.getPriceMode() == FeePriceMode.SINGLE) {
            row.getPrices().clear();
            if (row.getBasePrice() == null) {
                errors.put("basePrice", "Vui lòng nhập đơn giá cho khoản thu");
            }
            return;
        }

        row.setBasePrice(null);
        if (row.getPrices().isEmpty()) {
            errors.put("prices", "Vui lòng khai báo ít nhất một đơn giá");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (FeeItemPrice price : row.getPrices()) {
            String key = price.getClassGroup().getId() + "|" + normalizeArea(price.getAreaName());
            if (!seen.add(key)) {
                errors.put("prices", "Bảng giá có hai dòng cùng khối và khu vực");
                return;
            }
        }
    }

    /**
     * The collection dates a cycle needs, and nothing else. A cycle with no dates
     * (monthly) drops any it held; one with dates must have exactly the set its
     * template describes, each in a month that template allows.
     */
    private static void normalizeSchedules(FeeItem row, List<ScheduleRow> sent, Map<String, Object> errors) {
        FeeCollectionCycle cycle = row.getCollectionCycle();
        if (cycle == FeeCollectionCycle.MONTHLY) {
            row.getCollectionSchedules().clear();
            return;
        }

        List<Template> templates = templatesFor(cycle, row.getAcademicYear());
        if (cycle == FeeCollectionCycle.SEMESTER && templates.isEmpty()) {
            errors.put("collectionSchedules", "Năm học chưa có giai đoạn để thiết lập kỳ thu học kỳ");
            return;
        }

        if (sent != null) {
            List<FeeCollectionSchedule> built = new ArrayList<>();
            Set<Integer> sequences = new HashSet<>();
            for (ScheduleRow line : sent) {
                Template template = templates.stream()
                        .filter(t -> t.sequence() == line.sequence())
                        .findFirst()
                        .orElse(null);
                if (template == null || !sequences.add(line.sequence())) {
                    errors.put("collectionSchedules", "Vui lòng thiết lập đầy đủ các thời điểm thu");
                    return;
                }
                built.add(new FeeCollectionSchedule(
                        row, line.sequence(), line.collectionMonth(), template.period()));
            }
            row.syncCollectionSchedules(built);
        }

        List<FeeCollectionSchedule> current = row.getCollectionSchedules();
        if (current.size() != templates.size()) {
            errors.put("collectionSchedules", "Vui lòng thiết lập đầy đủ các thời điểm thu");
            return;
        }
        for (Template template : templates) {
            FeeCollectionSchedule schedule = current.stream()
                    .filter(s -> s.getSequence() == template.sequence())
                    .findFirst()
                    .orElse(null);
            if (schedule == null) {
                errors.put("collectionSchedules", "Vui lòng thiết lập đầy đủ các thời điểm thu");
                return;
            }
            if (!template.months().contains(schedule.getCollectionMonth())) {
                errors.put("collectionSchedules", "Tháng thu phải nằm trong khoảng thời gian tương ứng");
                return;
            }
        }
    }

    // ---- collection-date templates

    /** One collection a cycle calls for, and the months it may fall due in. */
    private record Template(int sequence, AcademicPeriod period, Set<Integer> months) {
    }

    private static List<Template> templatesFor(FeeCollectionCycle cycle, AcademicYear year) {
        return switch (cycle) {
            case MONTHLY -> List.of();
            case QUARTERLY -> List.of(
                    quarter(1, 1), quarter(2, 4), quarter(3, 7), quarter(4, 10));
            case SEMESTER -> {
                List<AcademicPeriod> periods = year.getPeriods().stream()
                        .sorted(Comparator.comparing(AcademicPeriod::getStartDate))
                        .toList();
                List<Template> templates = new ArrayList<>();
                for (int i = 0; i < periods.size(); i++) {
                    AcademicPeriod period = periods.get(i);
                    templates.add(new Template(
                            i + 1, period, monthsBetween(period.getStartDate(), period.getEndDate())));
                }
                yield templates;
            }
            case ACADEMIC_YEAR -> List.of(new Template(
                    1, null, monthsBetween(year.getStartDate(), year.getEndDate())));
        };
    }

    private static Template quarter(int sequence, int firstMonth) {
        return new Template(sequence, null, Set.of(firstMonth, firstMonth + 1, firstMonth + 2));
    }

    private static Set<Integer> monthsBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return Set.of();
        }
        Set<Integer> months = new LinkedHashSet<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            months.add(cursor.getMonthValue());
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    // ---- lookups

    private List<FeeItemPrice> buildPrices(FeeItem row, List<PriceRow> lines, AcademicYear year) {
        List<FeeItemPrice> prices = new ArrayList<>();
        for (PriceRow line : lines) {
            ClassGroup group = groupOf(line.classGroup().id(), year);
            String area = line.areaName() == null || line.areaName().isBlank() ? null : line.areaName().trim();
            prices.add(new FeeItemPrice(row, group, area, line.amount()));
        }
        return prices;
    }

    private ClassGroup groupOf(Long id, AcademicYear year) {
        ClassGroup group = classGroupService.get(id);
        if (!group.getAcademicYear().getId().equals(year.getId())) {
            throw invalid("scopeClassGroup", "Khối không thuộc năm học " + year.getName());
        }
        return group;
    }

    private SchoolClass classOf(Long id, AcademicYear year) {
        SchoolClass schoolClass = schoolClassService.get(id);
        if (!schoolClass.getClassGroup().getAcademicYear().getId().equals(year.getId())) {
            throw invalid("scopeClass", "Lớp không thuộc năm học " + year.getName());
        }
        return schoolClass;
    }

    private MeasurementUnit unitOf(Long id, AcademicYear year) {
        MeasurementUnit unit = measurementUnitService.get(id);
        if (!unit.getOrganization().getId().equals(year.getOrganization().getId())) {
            throw invalid("measurementUnit", "Đơn vị tính không thuộc trường của năm học");
        }
        return unit;
    }

    private void requireNameAvailable(AcademicYear year, String name, Long selfId) {
        repository.findByAcademicYearIdAndName(year.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "Năm học %s đã có khoản thu \"%s\"".formatted(year.getName(), name));
            }
        });
    }

    private static String normalizeArea(String area) {
        return area == null ? "" : area.trim().toLowerCase();
    }

    private static ValidationFailedException invalid(String field, String message) {
        return new ValidationFailedException("Dữ liệu không hợp lệ", Map.of(field, message));
    }

    // ---- the one shape both requests are read through

    /** A create and a patch, with the same fields; {@code null} means "not sent". */
    private record Draft(
            String name,
            FeeScope scope,
            Long scopeClassGroupId,
            Long scopeClassId,
            FeePriceMode priceMode,
            BigDecimal basePrice,
            Long measurementUnitId,
            BigDecimal vatRate,
            FeeCollectionCycle collectionCycle,
            Boolean discountEligible,
            Boolean mandatory,
            Boolean serviceFee,
            Boolean refundable,
            Boolean invoiceEnabled,
            Boolean receiptEnabled,
            Boolean internalCollection,
            BigDecimal invoiceRate,
            Boolean active,
            List<PriceRow> prices,
            List<ScheduleRow> collectionSchedules) {

        static Draft of(FeeItemRequest r) {
            return new Draft(
                    r.name(), r.scope(), id(r.scopeClassGroup()), id(r.scopeClass()), r.priceMode(),
                    r.basePrice(), id(r.measurementUnit()), r.vatRate(), r.collectionCycle(),
                    r.discountEligible(), r.mandatory(), r.serviceFee(), r.refundable(),
                    r.invoiceEnabled(), r.receiptEnabled(), r.internalCollection(),
                    r.invoiceRate(), r.active(), r.prices(), r.collectionSchedules());
        }

        static Draft of(FeeItemPatchRequest r) {
            return new Draft(
                    r.name(), r.scope(), id(r.scopeClassGroup()), id(r.scopeClass()), r.priceMode(),
                    r.basePrice(), id(r.measurementUnit()), r.vatRate(), r.collectionCycle(),
                    r.discountEligible(), r.mandatory(), r.serviceFee(), r.refundable(),
                    r.invoiceEnabled(), r.receiptEnabled(), r.internalCollection(),
                    r.invoiceRate(), r.active(), r.prices(), r.collectionSchedules());
        }

        private static Long id(Ref ref) {
            return ref == null ? null : ref.id();
        }
    }
}
