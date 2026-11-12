package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.module.school.fee.batch.api.dto.FeeCollectionBatchRequest.ItemLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body of {@code PUT /api/fee_collection_batches/{id}/items}: the whole set of khoản thu
 * the đợt should have. What is not in it is dropped, so the screen sends what it shows.
 */
public record FeeCollectionBatchItemsRequest(@NotEmpty List<@Valid ItemLine> items) {
}
