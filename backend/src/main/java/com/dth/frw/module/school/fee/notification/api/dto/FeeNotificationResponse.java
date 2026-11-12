package com.dth.frw.module.school.fee.notification.api.dto;

import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.domain.school.fee.FeeNotificationStatus;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * A đợt thông báo with the counters its list reads. {@code _can} is what the caller may do to
 * this row, so the screen offers a button only where it would not end in a refusal.
 */
public record FeeNotificationResponse(
        Long id,
        BatchSummary batch,
        String subject,
        String content,
        LocalDate deadline,
        Instant sendAt,
        FeeNotificationStatus status,
        int recipientCount,
        int sentCount,
        int failedCount,
        int missingEmailCount,
        String errorMessage,
        String createdByName,
        Instant createdAt,
        Instant updatedAt,
        @JsonProperty("_can") Map<String, Boolean> can) {

    public record BatchSummary(Long id, String name, int collectionMonth) {
    }

    public static FeeNotificationResponse from(FeeNotification row, RowLevelSecurity rowLevel) {
        return build(row, rowLevel.capabilities(row));
    }

    public static FeeNotificationResponse from(FeeNotification row) {
        return build(row, null);
    }

    private static FeeNotificationResponse build(FeeNotification row, Map<String, Boolean> can) {
        return new FeeNotificationResponse(
                row.getId(),
                new BatchSummary(row.getBatch().getId(), row.getBatch().getName(), row.getBatch().getCollectionMonth()),
                row.getSubject(),
                row.getContent(),
                row.getDeadline(),
                row.getSendAt(),
                row.getStatus(),
                row.getRecipientCount(),
                row.getSentCount(),
                row.getFailedCount(),
                row.getMissingEmailCount(),
                row.getErrorMessage(),
                row.getCreatedByName(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                can);
    }
}
