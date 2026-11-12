package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionBatchItem;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.ItemTotals;
import java.math.BigDecimal;

/**
 * One khoản thu of a đợt thu: the name and unit it had when the đợt was made, its quantity,
 * and what it comes to over the students charged for it. {@code minUnitPrice} and
 * {@code maxUnitPrice} differ only for a khoản thu priced by khối or area.
 */
public record FeeCollectionBatchItemResponse(
        Long id,
        Long feeItemId,
        String name,
        String unitName,
        boolean mandatory,
        int quantity,
        int sequence,
        int studentCount,
        BigDecimal minUnitPrice,
        BigDecimal maxUnitPrice,
        BigDecimal receivableAmount,
        BigDecimal collectedAmount) {

    public static FeeCollectionBatchItemResponse from(ItemTotals totals) {
        FeeCollectionBatchItem item = totals.item();
        return new FeeCollectionBatchItemResponse(
                item.getId(),
                item.getFeeItem().getId(),
                item.getName(),
                item.getUnitName(),
                item.getFeeItem().isMandatory(),
                item.getQuantity(),
                item.getSequence(),
                totals.studentCount(),
                totals.minUnitPrice(),
                totals.maxUnitPrice(),
                totals.receivableAmount(),
                totals.collectedAmount());
    }
}
