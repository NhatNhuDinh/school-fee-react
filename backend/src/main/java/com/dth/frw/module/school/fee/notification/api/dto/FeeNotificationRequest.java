package com.dth.frw.module.school.fee.notification.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Body of {@code POST /api/fee_notifications}: the đợt thu, the students picked from it
 * (their ids in that đợt), the words and the deadline. {@code draft} keeps it as a draft;
 * otherwise it is scheduled for {@code sendAt}, or for now when that is empty.
 */
public record FeeNotificationRequest(
        @NotNull @Valid Ref batch,
        @NotEmpty @Size(max = 10_000) List<@NotNull Long> batchStudentIds,
        @NotNull String subject,
        @NotNull String content,
        @NotNull LocalDate deadline,
        Instant sendAt,
        boolean draft) {

    /** A record named by its id. */
    public record Ref(@NotNull Long id) {
    }
}
