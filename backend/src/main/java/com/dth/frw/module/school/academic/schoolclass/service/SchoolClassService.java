package com.dth.frw.module.school.academic.schoolclass.service;

import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.module.school.academic.schoolclass.api.dto.SchoolClassPatchRequest;
import com.dth.frw.module.school.academic.schoolclass.api.dto.SchoolClassRequest;
import com.dth.frw.module.school.academic.schoolclass.repo.SchoolClassRepository;
import com.dth.frw.module.school.academic.schoolclass.repo.SchoolClassSpecifications;
import com.dth.frw.module.school.academic.classgroup.service.ClassGroupService;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.dth.frw.shared.exception.ConflictException;
import com.dth.frw.shared.exception.ResourceNotFoundException;
import com.dth.frw.shared.security.rowlevel.Action;
import com.dth.frw.shared.web.ListParams;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lớp of a khối lớp. A name is unique within its khối lớp, as the Jmix
 * application enforced with its tenant, khối lớp and name constraint; the school and
 * the year are the khối lớp's own.
 */
@Service
@Transactional(readOnly = true)
public class SchoolClassService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "lớp";

    private final SchoolClassRepository repository;
    private final ClassGroupService classGroupService;
    private final RowLevelSecurity rowLevel;

    public SchoolClassService(SchoolClassRepository repository,
                             ClassGroupService classGroupService,
                             RowLevelSecurity rowLevel) {
        this.repository = repository;
        this.classGroupService = classGroupService;
        this.rowLevel = rowLevel;
    }

    public Page<SchoolClass> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(SchoolClass.class, Action.READ).and(SchoolClassSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public SchoolClass get(Long id) {
        return repository.findOne(rowLevel.byId(SchoolClass.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<SchoolClass> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(SchoolClass.class, Action.READ, ids));
    }

    @Transactional
    public SchoolClass create(SchoolClassRequest request) {
        // Read through the khối lớp's own scope: one the caller cannot see is a 404.
        ClassGroup group = classGroupService.get(request.classGroup().id());
        String name = request.name().trim();
        requireNameAvailable(group, name, null);

        SchoolClass saved = repository.persist(new SchoolClass(group, name));
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public SchoolClass update(Long id, SchoolClassPatchRequest request) {
        SchoolClass row = repository.findOne(rowLevel.byId(SchoolClass.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));

        // Checked before the entity is touched, so the auto-flush cannot push the
        // update ahead of the check and let the unique index answer instead.
        if (request.name() != null) {
            String name = request.name().trim();
            requireNameAvailable(row.getClassGroup(), name, row.getId());
            row.setName(name);
        }

        rowLevel.checkWritable(row);
        return repository.persist(row);
    }

    @Transactional
    public SchoolClass delete(Long id) {
        SchoolClass row = repository.findOne(rowLevel.byId(SchoolClass.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        repository.remove(row);
        return row;
    }

    private void requireNameAvailable(ClassGroup group, String name, Long selfId) {
        repository.findByClassGroupIdAndName(group.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "%s đã có lớp \"%s\"".formatted(group.getName(), name));
            }
        });
    }
}
