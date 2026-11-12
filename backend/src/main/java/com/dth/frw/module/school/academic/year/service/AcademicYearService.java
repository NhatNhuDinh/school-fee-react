package com.dth.frw.module.school.academic.year.service;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicPeriod;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.catalog.organization.service.OrganizationService;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearPatchRequest;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearRequest;
import com.dth.frw.module.school.academic.year.api.dto.AcademicYearRequest.PeriodRequest;
import com.dth.frw.module.school.academic.year.repo.AcademicYearRepository;
import com.dth.frw.module.school.academic.year.repo.AcademicYearSpecifications;
import com.dth.frw.module.security.rowlevel.service.RowLevelPrincipalResolver;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.security.rowlevel.RowLevelPrincipal;
import com.dth.frw.shared.web.ListParams;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School years and the terms inside them.
 *
 * <p>The rules are the ones the Jmix application enforced: a year ends after it
 * starts, is named after its two calendar years and is unique within its
 * school; a term ends after it starts, lies inside its year and does not
 * overlap another term of the same year.
 */
@Service
@Transactional(readOnly = true)
public class AcademicYearService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "startDate", "endDate", "createdAt");

    // Newest year first, which is the order the year selector offers them in.
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("id"));

    private final AcademicYearRepository repository;
    private final OrganizationService organizationService;
    private final RowLevelSecurity rowLevel;
    private final RowLevelPrincipalResolver principals;

    public AcademicYearService(AcademicYearRepository repository,
                               OrganizationService organizationService,
                               RowLevelSecurity rowLevel,
                               RowLevelPrincipalResolver principals) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.rowLevel = rowLevel;
        this.principals = principals;
    }

    public Page<AcademicYear> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(AcademicYear.class, Action.READ).and(AcademicYearSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public AcademicYear get(Long id) {
        return repository.findOne(rowLevel.byId(AcademicYear.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", id));
    }

    public List<AcademicYear> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(AcademicYear.class, Action.READ, ids));
    }

    @Transactional
    public AcademicYear create(AcademicYearRequest request) {
        Organization school = resolveSchool(request.organization() == null ? null : request.organization().id());
        String name = nameOf(request.startDate(), request.endDate());
        List<PeriodRequest> periods = request.periods() == null ? List.of() : request.periods();

        validate(request.startDate(), request.endDate(), periods);
        requireNameAvailable(school, name, null);

        AcademicYear year = new AcademicYear(school, name, request.startDate(), request.endDate());
        syncPeriods(year, periods);

        AcademicYear saved = repository.persist(year);
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public AcademicYear update(Long id, AcademicYearPatchRequest request) {
        AcademicYear year = repository.findOne(rowLevel.byId(AcademicYear.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", id));

        LocalDate start = request.startDate() != null ? request.startDate() : year.getStartDate();
        LocalDate end = request.endDate() != null ? request.endDate() : year.getEndDate();
        List<PeriodRequest> periods = request.periods() != null
                ? request.periods()
                : year.getPeriods().stream()
                        .map(p -> new PeriodRequest(p.getId(), p.getName(), p.getStartDate(), p.getEndDate()))
                        .toList();

        // Everything is checked before the entity is touched, for the reason the
        // department feature spells out: mutating first lets the auto-flush push
        // the update ahead of the check and the index answers instead.
        validate(start, end, periods);
        String name = nameOf(start, end);
        requireNameAvailable(year.getOrganization(), name, year.getId());

        year.setStartDate(start);
        year.setEndDate(end);
        year.setName(name);
        if (request.periods() != null) {
            syncPeriods(year, request.periods());
        }

        rowLevel.checkWritable(year);
        return repository.persist(year);
    }

    @Transactional
    public AcademicYear delete(Long id) {
        AcademicYear year = repository.findOne(rowLevel.byId(AcademicYear.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", id));
        repository.remove(year);
        return year;
    }

    /**
     * The school a new year belongs to: the one asked for, else the caller's own.
     *
     * <p>Asking for a school other than your own is not refused here. The row is
     * built and the write check refuses it, which keeps the school boundary in
     * one place instead of being restated by every feature.
     */
    private Organization resolveSchool(Long requested) {
        Long id = requested != null
                ? requested
                : principals.current().map(RowLevelPrincipal::organizationId).orElse(null);
        if (id == null) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", Map.of("organization", "Vui lòng chọn trường"));
        }
        return organizationService.get(id);
    }

    private static String nameOf(LocalDate start, LocalDate end) {
        return start.getYear() + "-" + end.getYear();
    }

    private void requireNameAvailable(Organization school, String name, Long selfId) {
        repository.findByOrganizationIdAndName(school.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "%s đã có năm học %s".formatted(school.getName(), name));
            }
        });
    }

    private static void validate(LocalDate start, LocalDate end, List<PeriodRequest> periods) {
        Map<String, Object> errors = new LinkedHashMap<>();
        if (!start.isBefore(end)) {
            errors.put("endDate", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        for (PeriodRequest period : periods) {
            if (!period.startDate().isBefore(period.endDate())) {
                errors.putIfAbsent("periods", "Mỗi học kỳ phải kết thúc sau ngày bắt đầu");
            } else if (period.startDate().isBefore(start) || period.endDate().isAfter(end)) {
                errors.putIfAbsent("periods", "Mỗi học kỳ phải nằm trong năm học");
            }
        }
        for (int i = 0; i < periods.size(); i++) {
            for (int j = i + 1; j < periods.size(); j++) {
                PeriodRequest a = periods.get(i);
                PeriodRequest b = periods.get(j);
                if (!a.endDate().isBefore(b.startDate()) && !b.endDate().isBefore(a.startDate())) {
                    errors.putIfAbsent("periods", "Các học kỳ trong cùng năm học không được chồng lấn");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", errors);
        }
    }

    /** Makes the year's terms equal to the list: updates by id, adds the rest, drops what is missing. */
    private static void syncPeriods(AcademicYear year, List<PeriodRequest> requested) {
        Set<Long> keptIds = new java.util.HashSet<>();
        for (PeriodRequest request : requested) {
            if (request.id() != null) {
                keptIds.add(request.id());
            }
        }
        year.getPeriods().removeIf(existing -> existing.getId() != null && !keptIds.contains(existing.getId()));

        for (PeriodRequest request : requested) {
            if (request.id() == null) {
                year.getPeriods().add(new AcademicPeriod(
                        year, request.name().trim(), request.startDate(), request.endDate()));
                continue;
            }
            AcademicPeriod existing = year.getPeriods().stream()
                    .filter(p -> request.id().equals(p.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationFailedException(
                            "Dữ liệu không hợp lệ", Map.of("periods", "Không tìm thấy học kỳ " + request.id())));
            existing.setName(request.name().trim());
            existing.setStartDate(request.startDate());
            existing.setEndDate(request.endDate());
        }
    }
}
