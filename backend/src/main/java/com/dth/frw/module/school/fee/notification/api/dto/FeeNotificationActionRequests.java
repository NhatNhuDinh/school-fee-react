package com.dth.frw.module.school.fee.notification.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The small bodies of the notification endpoints that are not a create or an update. */
public final class FeeNotificationActionRequests {

    private FeeNotificationActionRequests() {
    }

    /** {@code POST /{id}/send}: when to send a draft; now when empty. */
    public record Send(Instant sendAt) {
    }

    /** {@code POST /{id}/retry}: which recipients to try again; every failed one when empty. */
    public record Retry(List<Long> recipientIds) {
    }

    /** {@code POST /preview}: the e-mail one student would get with these words. */
    public record Preview(
            @NotNull Long batchId,
            @NotNull Long batchStudentId,
            String subject,
            String content,
            LocalDate deadline) {
    }
}
