package com.dth.frw.module.school.fee.batch.service;

import com.dth.frw.domain.school.fee.FeeCollectionBatchItem;
import com.dth.frw.domain.school.fee.FeeCollectionBatchStudent;
import com.dth.frw.domain.school.fee.FeeItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * What the service hands the controller beyond a plain entity: read models that join a
 * đợt thu's rows to the totals computed over its receivables.
 */
public final class FeeCollectionBatchViews {

    private FeeCollectionBatchViews() {
    }

    /** What one khoản thu of a đợt comes to over the students it was charged to. */
    public record ItemTotals(
            FeeCollectionBatchItem item,
            int studentCount,
            BigDecimal receivableAmount,
            BigDecimal collectedAmount,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice) {
    }

    /** One student's amount for one khoản thu of the đợt. */
    public record Amount(BigDecimal receivable, BigDecimal collected) {
    }

    /** A page of a đợt's students, with each one's amount per khoản thu (by batch item id). */
    public record StudentRows(
            Page<FeeCollectionBatchStudent> page,
            Map<Long, Map<Long, Amount>> amountsByStudent) {
    }

    /** One khoản thu the đợt could collect, as the create dialog lists it. */
    public record Candidate(
            FeeItem item,
            int applicableStudents,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            int studentsWithoutPrice,
            boolean quantityEditable,
            boolean selected,
            int quantity,
            BigDecimal amount) {
    }

    /** The result of asking "what would this đợt thu be?" without making it. */
    public record Preview(
            int studentCount,
            int itemCount,
            BigDecimal receivableAmount,
            List<Candidate> candidates,
            List<String> problems) {
    }

    /** One khoản thu asked for, with its quantity. */
    public record Line(Long feeItemId, int quantity) {
    }

    /** What a sync with the registrations changed. */
    public record SyncResult(int added, int removed, int keptBecauseCollected) {
    }
}
