package com.dth.frw.module.school.fee.batch.api.dto;

import java.util.List;

/**
 * A page of a đợt thu's students with the total behind it.
 *
 * <p>An envelope rather than the data provider's bare array plus {@code X-Total-Count}:
 * this endpoint is not a resource the provider lists, it is read through a custom
 * request, and a custom request returns the body only — the header would never reach the
 * screen, and the pager would not know how many pages there are.
 */
public record FeeCollectionBatchStudentsResponse(List<FeeCollectionBatchStudentResponse> rows, long total) {
}
