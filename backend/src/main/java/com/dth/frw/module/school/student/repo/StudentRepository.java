package com.dth.frw.module.school.student.repo;

import com.dth.frw.domain.school.student.Student;
import com.dth.frw.shared.data.SecuredRepository;
import java.util.Optional;

/**
 * A {@link SecuredRepository}, so there is no unscoped {@code findById} to call by
 * mistake. A student is reached through its enrollment, whose scope is the
 * school boundary; the finders below back the duplicate checks and are bounded
 * by the school instead, which is the boundary the unique constraints have.
 */
public interface StudentRepository extends SecuredRepository<Student, Long> {

    Optional<Student> findByOrganizationIdAndStudentCode(Long organizationId, String studentCode);

    Optional<Student> findByOrganizationIdAndPersonalId(Long organizationId, String personalId);

    boolean existsByOrganizationIdAndStudentCode(Long organizationId, String studentCode);
}
