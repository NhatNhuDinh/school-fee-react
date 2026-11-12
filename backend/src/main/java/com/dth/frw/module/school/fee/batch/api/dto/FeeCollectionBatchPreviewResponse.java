package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Candidate;
import com.dth.frw.module.school.fee.batch.service.FeeCollectionBatchViews.Preview;
import java.math.BigDecimal;
import java.util.List;

/**
 * What a đợt thu would be: who it reaches, which khoản thu can go in it and what the
 * chosen ones come to. {@code problems} are the reasons it could not be made as asked
 * (a missing price, say) — a preview reports them rather than failing, so the dialog can
 * show them beside the figure.
 */
public record FeeCollectionBatchPreviewResponse(
        int studentCount,
        int itemCount,
        BigDecimal receivableAmount,
        List<CandidateResponse> candidates,
        List<String> problems) {

    /** One khoản thu that could go in the đợt. */
    public record CandidateResponse(
            Long feeItemId,
            String name,
            String unitName,
            boolean mandatory,
            FeeScope scope,
            String scopeName,
            int applicableStudents,
            BigDecimal minUnitPrice,
            BigDecimal maxUnitPrice,
            int studentsWithoutPrice,
            boolean quantityEditable,
            boolean selected,
            int quantity,
            BigDecimal amount) {

        static CandidateResponse from(Candidate candidate) {
            FeeItem item = candidate.item();
            String scopeName = switch (item.getScope()) {
                case CLASS_GROUP -> item.getScopeClassGroup() == null ? null : item.getScopeClassGroup().getName();
                case CLASS -> item.getScopeClass() == null ? null : item.getScopeClass().getName();
                default -> null;
            };
            return new CandidateResponse(
                    item.getId(),
                    item.getName(),
                    item.getMeasurementUnit().getName(),
                    item.isMandatory(),
                    item.getScope(),
                    scopeName,
                    candidate.applicableStudents(),
                    candidate.minUnitPrice(),
                    candidate.maxUnitPrice(),
                    candidate.studentsWithoutPrice(),
                    candidate.quantityEditable(),
                    candidate.selected(),
                    candidate.quantity(),
                    candidate.amount());
        }
    }

    public static FeeCollectionBatchPreviewResponse from(Preview preview) {
        return new FeeCollectionBatchPreviewResponse(
                preview.studentCount(),
                preview.itemCount(),
                preview.receivableAmount(),
                preview.candidates().stream().map(CandidateResponse::from).toList(),
                preview.problems());
    }
}
