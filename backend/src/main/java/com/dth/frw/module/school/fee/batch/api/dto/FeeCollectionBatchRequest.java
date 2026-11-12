package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/fee_collection_batches}. The year is on the request and nowhere
 * else: a đợt never changes year, and there is no patch. The khoản thu come with their
 * quantity; the service decides which of them are allowed.
 */
public record FeeCollectionBatchRequest(
        @NotNull @Valid Ref academicYear,
        @NotBlank @Size(max = 255) String name,
        @NotNull @Min(1) @Max(12) Integer collectionMonth,
        @NotNull FeeCollectionScope scope,
        @Valid Ref scopeClassGroup,
        @Valid Ref scopeClass,
        @NotEmpty List<@Valid ItemLine> items) {

    /** A record named by its id. */
    public record Ref(@NotNull Long id) {
    }

    /** One khoản thu asked for, and how many of it each student is charged. */
    public record ItemLine(@NotNull Long feeItemId, @NotNull @Min(1) Integer quantity) {
    }
}
