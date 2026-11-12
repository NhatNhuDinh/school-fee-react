package com.dth.frw.module.school.fee.registration.repo;

import com.dth.frw.domain.school.fee.StudentFeeRegistration;
import com.dth.frw.shared.data.SecuredRepository;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/**
 * A {@link SecuredRepository}: every read is handed a scope, so the school boundary
 * cannot be skipped by calling an inherited method.
 */
public interface FeeRegistrationRepository extends SecuredRepository<StudentFeeRegistration, Long> {

    /** The registrations of these enrollments, to be combined with the caller's scope. */
    static Specification<StudentFeeRegistration> ofEnrollments(Collection<Long> enrollmentIds) {
        return (root, query, cb) -> enrollmentIds.isEmpty()
                ? cb.disjunction()
                : root.get("enrollment").get("id").in(enrollmentIds);
    }
}
