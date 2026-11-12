package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionScope;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchRequest.ItemLine;
import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchRequest.Ref;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of {@code POST /api/fee_collection_batches/preview}: the same choices as creating
 * a đợt, without the name and without needing any khoản thu yet — an empty list is how
 * the dialog asks "what could go in a đợt like this".
 */
public record FeeCollectionBatchPreviewRequest(
        @NotNull @Valid Ref academicYear,
        @NotNull @Min(1) @Max(12) Integer collectionMonth,
        @NotNull FeeCollectionScope scope,
        @Valid Ref scopeClassGroup,
        @Valid Ref scopeClass,
        List<@Valid ItemLine> items) {
}
