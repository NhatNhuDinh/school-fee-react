package com.dth.frw.module.school.academic.schoolclass.repo;

import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.shared.data.SecuredRepository;
import java.util.Optional;

/**
 * A {@link SecuredRepository}, so every read has to be handed a scope and the
 * school boundary cannot be skipped by calling an inherited method.
 */
public interface SchoolClassRepository extends SecuredRepository<SchoolClass, Long> {

    /**
     * Backs the duplicate-name check.
     *
     * <p><b>Deliberately not row-level scoped</b>: a uniqueness check that
     * cannot see the rows it checks against is not one. It is bounded by the
     * khối lớp instead, which is the boundary the unique constraint has.
     */
    Optional<SchoolClass> findByClassGroupIdAndName(Long classGroupId, String name);
}
