package com.dth.frw.module.school.academic.prioritycategory.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.DiscountType;
import com.dth.frw.domain.school.academic.PriorityApplicationPeriod;
import com.dth.frw.domain.school.academic.PriorityCategory;
import com.dth.frw.module.school.academic.prioritycategory.api.dto.PriorityCategoryPatchRequest;
import com.dth.frw.module.school.academic.prioritycategory.api.dto.PriorityCategoryRequest;
import com.dth.frw.module.school.academic.prioritycategory.repo.PriorityCategoryRepository;
import com.dth.frw.module.school.academic.prioritycategory.repo.PriorityCategorySpecifications;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The diện ưu tiên of a school year. A name is unique within its year, as the Jmix
 * application enforced with its tenant, year and name constraint; the school is
 * the year's own.
 */
@Service
@Transactional(readOnly = true)
public class PriorityCategoryService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "discountValue", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "diện ưu tiên";

    private final PriorityCategoryRepository repository;
    private final AcademicYearService academicYearService;
    private final RowLevelSecurity rowLevel;

    public PriorityCategoryService(PriorityCategoryRepository repository,
                             AcademicYearService academicYearService,
                             RowLevelSecurity rowLevel) {
        this.repository = repository;
        this.academicYearService = academicYearService;
        this.rowLevel = rowLevel;
    }

    public Page<PriorityCategory> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(PriorityCategory.class, Action.READ).and(PriorityCategorySpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public PriorityCategory get(Long id) {
        return repository.findOne(rowLevel.byId(PriorityCategory.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<PriorityCategory> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(PriorityCategory.class, Action.READ, ids));
    }

    @Transactional
    public PriorityCategory create(PriorityCategoryRequest request) {
        // Read through the year's own scope: a year the caller cannot see is a 404.
        AcademicYear year = academicYearService.get(request.academicYear().id());
        String name = request.name().trim();
        requireNameAvailable(year, name, null);

        PriorityCategory row = new PriorityCategory(year, name);
        row.setDiscountType(request.discountType());
        row.setDiscountValue(request.discountValue());
        row.setApplicationPeriod(request.applicationPeriod());
        row.setFromMonth(request.fromMonth());
        row.setToMonth(request.toMonth());
        normalizeAndValidate(row);

        PriorityCategory saved = repository.persist(row);
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public PriorityCategory update(Long id, PriorityCategoryPatchRequest request) {
        PriorityCategory row = repository.findOne(rowLevel.byId(PriorityCategory.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));

        // Checked before the entity is touched, so the auto-flush cannot push the
        // update ahead of the check and let the unique index answer instead.
        if (request.name() != null) {
            String name = request.name().trim();
            requireNameAvailable(row.getAcademicYear(), name, row.getId());
            row.setName(name);
        }
        if (request.discountType() != null) {
            row.setDiscountType(request.discountType());
        }
        if (request.discountValue() != null) {
            row.setDiscountValue(request.discountValue());
        }
        if (request.applicationPeriod() != null) {
            row.setApplicationPeriod(request.applicationPeriod());
        }
        if (request.fromMonth() != null) {
            row.setFromMonth(request.fromMonth());
        }
        if (request.toMonth() != null) {
            row.setToMonth(request.toMonth());
        }
        normalizeAndValidate(row);

        rowLevel.checkWritable(row);
        return repository.persist(row);
    }

    @Transactional
    public PriorityCategory delete(Long id) {
        PriorityCategory row = repository.findOne(rowLevel.byId(PriorityCategory.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        repository.remove(row);
        return row;
    }

    /**
     * The rules the Jmix service enforced: a percentage cannot pass 100, and a
     * custom period needs both months, in order. Months mean nothing for any other
     * period, so they are cleared rather than left to contradict it.
     */
    private static void normalizeAndValidate(PriorityCategory row) {
        Map<String, Object> errors = new HashMap<>();

        if (row.getDiscountType() == DiscountType.PERCENT
                && row.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.put("discountValue", "Mức miễn giảm theo phần trăm không được vượt quá 100%");
        }

        if (row.getApplicationPeriod() == PriorityApplicationPeriod.CUSTOM) {
            if (row.getFromMonth() == null || row.getToMonth() == null) {
                errors.put("fromMonth", "Vui lòng chọn Từ tháng và Đến tháng khi thời gian áp dụng là Tùy chọn");
            } else if (row.getFromMonth() > row.getToMonth()) {
                errors.put("fromMonth", "Từ tháng không được lớn hơn Đến tháng");
            }
        } else {
            row.setFromMonth(null);
            row.setToMonth(null);
        }

        if (!errors.isEmpty()) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", errors);
        }
    }

    private void requireNameAvailable(AcademicYear year, String name, Long selfId) {
        repository.findByAcademicYearIdAndName(year.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "Năm học %s đã có diện ưu tiên \"%s\"".formatted(year.getName(), name));
            }
        });
    }
}
