package com.dth.frw.module.school.catalog.trainingsystem.service;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.module.catalog.organization.service.OrganizationService;
import com.dth.frw.module.school.catalog.trainingsystem.api.dto.TrainingSystemPatchRequest;
import com.dth.frw.module.school.catalog.trainingsystem.api.dto.TrainingSystemRequest;
import com.dth.frw.module.school.catalog.trainingsystem.repo.TrainingSystemRepository;
import com.dth.frw.module.school.catalog.trainingsystem.repo.TrainingSystemSpecifications;
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
 * The hệ đào tạo of a school. A name is unique within its school, as the Jmix
 * application enforced with its tenant-and-name constraint.
 */
@Service
@Transactional(readOnly = true)
public class TrainingSystemService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "hệ đào tạo";

    private final TrainingSystemRepository repository;
    private final OrganizationService organizationService;
    private final RowLevelSecurity rowLevel;
    private final RowLevelPrincipalResolver principals;

    public TrainingSystemService(TrainingSystemRepository repository,
                           OrganizationService organizationService,
                           RowLevelSecurity rowLevel,
                           RowLevelPrincipalResolver principals) {
        this.repository = repository;
        this.organizationService = organizationService;
        this.rowLevel = rowLevel;
        this.principals = principals;
    }

    public Page<TrainingSystem> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(TrainingSystem.class, Action.READ).and(TrainingSystemSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public TrainingSystem get(Long id) {
        return repository.findOne(rowLevel.byId(TrainingSystem.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<TrainingSystem> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(TrainingSystem.class, Action.READ, ids));
    }

    @Transactional
    public TrainingSystem create(TrainingSystemRequest request) {
        Organization school = resolveSchool(request.organization() == null ? null : request.organization().id());
        String name = request.name().trim();
        requireNameAvailable(school, name, null);

        TrainingSystem saved = repository.persist(new TrainingSystem(school, name));
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public TrainingSystem update(Long id, TrainingSystemPatchRequest request) {
        TrainingSystem row = repository.findOne(rowLevel.byId(TrainingSystem.class, Action.WRITE, id))
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
    public TrainingSystem delete(Long id) {
        TrainingSystem row = repository.findOne(rowLevel.byId(TrainingSystem.class, Action.DELETE, id))
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
                        "%s đã có hệ đào tạo \"%s\"".formatted(school.getName(), name));
            }
        });
    }
}
