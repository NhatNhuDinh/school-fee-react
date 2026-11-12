package com.dth.frw.module.school.fee.batch.repo;

import com.dth.frw.domain.school.fee.FeeCollectionBatchStudent;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Not a {@code SecuredRepository}, for the reason {@link FeeCollectionBatchItemRepository}
 * gives: a student of a đợt is reached through the đợt, which was read through the scope.
 */
public interface FeeCollectionBatchStudentRepository
        extends JpaRepository<FeeCollectionBatchStudent, Long>, JpaSpecificationExecutor<FeeCollectionBatchStudent> {

    List<FeeCollectionBatchStudent> findByBatchId(Long batchId);

    /** The students of one đợt, to be combined with the caller's filters. */
    static Specification<FeeCollectionBatchStudent> ofBatch(Long batchId) {
        return (root, query, cb) -> cb.equal(root.get("batch").get("id"), batchId);
    }
}
