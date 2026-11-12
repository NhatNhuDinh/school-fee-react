package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionBatchStudent;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Amount;
import java.math.BigDecimal;
import java.util.Map;

/**
 * One student of a đợt thu with what they owe. {@code amounts} is keyed by the id of the
 * đợt's khoản thu ({@link FeeCollectionBatchItemResponse#id()}); a khoản thu that does not
 * apply to the student is simply absent, which is what the table draws as a dash.
 */
public record FeeCollectionBatchStudentResponse(
        Long id,
        Long enrollmentId,
        String studentCode,
        String fullName,
        Long schoolClassId,
        String schoolClassName,
        BigDecimal receivableAmount,
        BigDecimal collectedAmount,
        Map<Long, AmountResponse> amounts) {

    public record AmountResponse(BigDecimal receivable, BigDecimal collected) {
    }

    public static FeeCollectionBatchStudentResponse from(FeeCollectionBatchStudent row, Map<Long, Amount> amounts) {
        Map<Long, AmountResponse> mapped = new java.util.LinkedHashMap<>();
        if (amounts != null) {
            amounts.forEach((itemId, amount) ->
                    mapped.put(itemId, new AmountResponse(amount.receivable(), amount.collected())));
        }
        return new FeeCollectionBatchStudentResponse(
                row.getId(),
                row.getEnrollment().getId(),
                row.getEnrollment().getStudent().getStudentCode(),
                row.getEnrollment().getStudent().getFullName(),
                row.getSchoolClass().getId(),
                row.getSchoolClass().getName(),
                row.getReceivableAmount(),
                row.getCollectedAmount(),
                mapped);
    }
}
