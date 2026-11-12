package com.dth.frw.module.school.student.repo;

import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.shared.data.SecuredRepository;
import java.util.Optional;

/** A {@link SecuredRepository}: every read has to be handed a scope. */
public interface StudentEnrollmentRepository extends SecuredRepository<StudentEnrollment, Long> {

    /**
     * Backs the duplicate-enrollment check and the clean-up on delete.
     *
     * <p><b>Deliberately not row-level scoped</b>: a uniqueness check that cannot
     * see the rows it checks against is not one. Both are bounded by the student,
     * who belongs to one school.
     */
    Optional<StudentEnrollment> findByAcademicYearIdAndStudentId(Long academicYearId, Long studentId);

    long countByStudentId(Long studentId);
}
