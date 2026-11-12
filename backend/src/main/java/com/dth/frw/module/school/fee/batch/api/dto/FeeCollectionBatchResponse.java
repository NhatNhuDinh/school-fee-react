package com.dth.frw.module.school.fee.batch.api.dto;

import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.domain.school.fee.FeeCollectionBatchStatus;
import com.dth.frw.domain.school.fee.FeeCollectionScope;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a đợt thu. The year, khối lớp and lớp are nested because the screens read
 * their names; the summaries are declared here rather than imported, so widening another
 * API cannot widen this one. {@code status} is derived from the money and never stored.
 */
public record FeeCollectionBatchResponse(
        Long id,
        AcademicYearSummary academicYear,
        String name,
        int collectionMonth,
        FeeCollectionScope scope,
        NamedSummary scopeClassGroup,
        NamedSummary scopeClass,
        int feeItemCount,
        int studentCount,
        BigDecimal receivableAmount,
        BigDecimal collectedAmount,
        FeeCollectionBatchStatus status,
        Instant createdAt,
        Instant updatedAt,
        /** What this caller may do to this row, as {@code {"write": true, "delete": false}}. */
        @JsonProperty("_can") Map<String, Boolean> can) {

    public record AcademicYearSummary(Long id, String name) {
    }

    public record NamedSummary(Long id, String name) {
        static NamedSummary from(ClassGroup group) {
            return group == null ? null : new NamedSummary(group.getId(), group.getName());
        }

        static NamedSummary from(SchoolClass schoolClass) {
            return schoolClass == null ? null : new NamedSummary(schoolClass.getId(), schoolClass.getName());
        }
    }

    public static FeeCollectionBatchResponse from(FeeCollectionBatch row, RowLevelSecurity rowLevel) {
        return build(row, rowLevel.capabilities(row));
    }

    /** For a row that was just deleted: there is nothing left to ask what may be done to it. */
    public static FeeCollectionBatchResponse from(FeeCollectionBatch row) {
        return build(row, null);
    }

    private static FeeCollectionBatchResponse build(FeeCollectionBatch row, Map<String, Boolean> can) {
        return new FeeCollectionBatchResponse(
                row.getId(),
                new AcademicYearSummary(row.getAcademicYear().getId(), row.getAcademicYear().getName()),
                row.getName(),
                row.getCollectionMonth(),
                row.getScope(),
                NamedSummary.from(row.getScopeClassGroup()),
                NamedSummary.from(row.getScopeClass()),
                row.getFeeItemCount(),
                row.getStudentCount(),
                row.getReceivableAmount(),
                row.getCollectedAmount(),
                row.status(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                can);
    }
}
