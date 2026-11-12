package com.dth.frw.module.school.academic.classgroup.service;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.module.school.academic.classgroup.api.dto.ClassGroupPatchRequest;
import com.dth.frw.module.school.academic.classgroup.api.dto.ClassGroupRequest;
import com.dth.frw.module.school.academic.classgroup.repo.ClassGroupRepository;
import com.dth.frw.module.school.academic.classgroup.repo.ClassGroupSpecifications;
import com.dth.frw.module.school.academic.year.service.AcademicYearService;
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
 * The khối lớp of a school year. A name is unique within its year, as the Jmix
 * application enforced with its tenant, year and name constraint; the school is
 * the year's own.
 */
@Service
@Transactional(readOnly = true)
public class ClassGroupService {

    /** Sort properties the list endpoint accepts; anything else is ignored. */
    private static final Set<String> SORTABLE = Set.of("id", "name", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private static final String RESOURCE = "khối lớp";

    private final ClassGroupRepository repository;
    private final AcademicYearService academicYearService;
    private final RowLevelSecurity rowLevel;

    public ClassGroupService(ClassGroupRepository repository,
                             AcademicYearService academicYearService,
                             RowLevelSecurity rowLevel) {
        this.repository = repository;
        this.academicYearService = academicYearService;
        this.rowLevel = rowLevel;
    }

    public Page<ClassGroup> list(ListParams params) {
        return repository.findAll(
                rowLevel.scope(ClassGroup.class, Action.READ).and(ClassGroupSpecifications.from(params)),
                params.pageable(SORTABLE, DEFAULT_SORT));
    }

    public ClassGroup get(Long id) {
        return repository.findOne(rowLevel.byId(ClassGroup.class, Action.READ, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    public List<ClassGroup> getMany(List<Long> ids) {
        return repository.findAll(rowLevel.byIds(ClassGroup.class, Action.READ, ids));
    }

    @Transactional
    public ClassGroup create(ClassGroupRequest request) {
        // Read through the year's own scope: a year the caller cannot see is a 404.
        AcademicYear year = academicYearService.get(request.academicYear().id());
        String name = request.name().trim();
        requireNameAvailable(year, name, null);

        ClassGroup saved = repository.persist(new ClassGroup(year, name));
        // Flushed first: the identity key only exists afterwards, and checking a
        // policy against a half-built row is wrong in every case.
        repository.flush();
        rowLevel.checkWritable(saved);
        return saved;
    }

    @Transactional
    public ClassGroup update(Long id, ClassGroupPatchRequest request) {
        ClassGroup row = repository.findOne(rowLevel.byId(ClassGroup.class, Action.WRITE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));

        // Checked before the entity is touched, so the auto-flush cannot push the
        // update ahead of the check and let the unique index answer instead.
        if (request.name() != null) {
            String name = request.name().trim();
            requireNameAvailable(row.getAcademicYear(), name, row.getId());
            row.setName(name);
        }

        rowLevel.checkWritable(row);
        return repository.persist(row);
    }

    @Transactional
    public ClassGroup delete(Long id) {
        ClassGroup row = repository.findOne(rowLevel.byId(ClassGroup.class, Action.DELETE, id))
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        repository.remove(row);
        return row;
    }

    private void requireNameAvailable(AcademicYear year, String name, Long selfId) {
        repository.findByAcademicYearIdAndName(year.getId(), name).ifPresent(existing -> {
            if (!existing.getId().equals(selfId)) {
                throw new ConflictException(
                        "Năm học %s đã có khối lớp \"%s\"".formatted(year.getName(), name));
            }
        });
    }
}
