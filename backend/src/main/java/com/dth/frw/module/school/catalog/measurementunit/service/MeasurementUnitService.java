package com.dth.frw.module.school.catalog.measurementunit.service;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.module.catalog.organization.service.OrganizationService;
import com.dth.frw.module.school.catalog.measurementunit.api.dto.MeasurementUnitPatchRequest;
import com.dth.frw.module.school.catalog.measurementunit.api.dto.MeasurementUnitRequest;
import com.dth.frw.module.school.catalog.measurementunit.repo.MeasurementUnitRepository;
import com.dth.frw.module.school.catalog.measurementunit.repo.MeasurementUnitSpecifications;
import com.dth.frw.module.security.rowlevel.service.RowLevelPrincipalResolver;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.exception.ValidationFailedException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.security.rowlevel.RowLevelPrincipal;
import com.dth.frw.shared.web.ListParams;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The đơn vị tính of a school. A name is unique within its school, as the Jmix
 * application enforced with its tenant-and-name constraint.
 */
@Service
@Transactional(readOnly = true)
public class MeasurementUnitService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "đơn vị tính";

    private final MeasurementUnitRepository repository;
    private final OrganizationService organizationService;
    private final RowLevelSecurity rowLevel;
    private final RowLevelPrincipalResolver principals;

    public MeasurementUnitService(MeasurementUnitRepository repository,
                           OrganizationService organizationService,
                           RowLevelSecurity rowLevel,
                           RowLevelPrincipalResolver principals) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.rowLevel = rowLevel;
        this.principals = principals;
    }

    public Page<MeasurementUnit> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(MeasurementUnit.class, Action.READ).and(MeasurementUnitSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public MeasurementUnit get(Long id) {
        return repository.findOne(rowLevel.byId(MeasurementUnit.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<MeasurementUnit> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(MeasurementUnit.class, Action.READ, ids));
    }

    @Transactional
    public MeasurementUnit create(MeasurementUnitRequest request) {
        Organization school = resolveSchool(request.organization() == null ? null : request.organization().id());
        String name = request.name().trim();
        requireNameAvailable(school, name, null);

        MeasurementUnit saved = repository.persist(new MeasurementUnit(school, name));
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public MeasurementUnit update(Long id, MeasurementUnitPatchRequest request) {
        MeasurementUnit row = repository.findOne(rowLevel.byId(MeasurementUnit.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));

        // Checked before the entity is touched, so the auto-flush cannot push the
        // update ahead of the check and let the unique index answer instead.
        if (request.name() != null) {
            String name = request.name().trim();
            requireNameAvailable(row.getOrganization(), name, row.getId());
            row.setName(name);
        }

        rowLevel.checkWritable(row);
        return repository.persist(row);
    }

    @Transactional
    public MeasurementUnit delete(Long id) {
        MeasurementUnit row = repository.findOne(rowLevel.byId(MeasurementUnit.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        repository.remove(row);
        return row;
    }

    /** The school a new row belongs to: the one asked for, else the caller's own. */
    private Organization resolveSchool(Long requested) {
        Long id = requested != null
                ? requested
                : principals.current().map(RowLevelPrincipal::organizationId).orElse(null);
        if (id == null) {
            throw new ValidationFailedException("Dữ liệu không hợp lệ", Map.of("organization", "Vui lòng chọn trường"));
        }
        return organizationService.get(id);
    }

    private void requireNameAvailable(Organization school, String name, Long selfId) {
        repository.findByOrganizationIdAndName(school.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "%s đã có đơn vị tính \"%s\"".formatted(school.getName(), name));
            }
        });
    }
}
