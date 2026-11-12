package com.dth.frw.module.school.fee.batch.repo;

import com.dth.frw.domain.school.fee.FeeCollectionBatchItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Not a {@code SecuredRepository}: a batch item is owned by its đợt thu and is only ever
 * reached with the id of one the caller has already been allowed to read, so the scope
 * is applied there and not repeated here.
 */
public interface FeeCollectionBatchItemRepository extends JpaRepository<FeeCollectionBatchItem, Long> {

    List<FeeCollectionBatchItem> findByBatchIdOrderBySequenceAsc(Long batchId);
}
