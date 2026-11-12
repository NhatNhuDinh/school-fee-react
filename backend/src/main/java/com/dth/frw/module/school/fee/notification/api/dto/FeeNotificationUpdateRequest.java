package com.dth.frw.module.school.fee.notification.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Body of {@code PUT /api/fee_notifications/{id}}: the words of a draft. Who it goes to is fixed. */
public record FeeNotificationUpdateRequest(
        @NotNull String subject,
        @NotNull String content,
        @NotNull LocalDate deadline) {
}
