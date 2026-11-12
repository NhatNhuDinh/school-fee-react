package com.dth.frw.module.school.fee.registration.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code PUT /fee_registrations/class/{schoolClassId}}: the registrations the class should
 * end up with. It is the whole state of the matrix, not a patch — the service works out
 * what to add and what to delete by comparing it with what is stored, so a retried or
 * repeated save changes nothing the second time.
 *
 * <p>A student left out of {@code registrations} has none; one named with an empty list
 * has none either.
 */
public record FeeRegistrationSaveRequest(
        @NotNull(message = "Thiếu danh sách đăng ký") List<@Valid Row> registrations) {

    /** One student's ticked khoản thu. */
    public record Row(
            @NotNull(message = "Thiếu học sinh") Long enrollmentId,
            @NotNull(message = "Thiếu danh sách khoản thu") List<@NotNull Long> feeItemIds) {
    }
}
