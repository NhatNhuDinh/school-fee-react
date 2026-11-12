package com.dth.frw.module.school.academic.year.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code POST /academic_years} payload.
 *
 * <p>There is no {@code name}: it is derived from the two dates, so it cannot
 * disagree with them. {@code organization} is optional because somebody placed
 * in a school creates for that school; only a system administrator, who belongs
 * to none, has to say which.
 */
public record AcademicYearRequest(
        @Valid OrganizationRef organization,
        @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,
        @NotNull(message = "Vui lòng nhập ngày kết thúc") LocalDate endDate,
        @Valid List<PeriodRequest> periods) {

    /** The school, by id. Only the id is read; anything else on the object is ignored. */
    public record OrganizationRef(Long id) {
    }

    /**
     * One term. An {@code id} that matches a term already on the year updates
     * it; none adds a new one.
     */
    public record PeriodRequest(
            Long id,
            @NotBlank(message = "Vui lòng nhập tên")
            @Size(max = 255, message = "Tên tối đa 255 ký tự")
            String name,
            @NotNull(message = "Vui lòng nhập ngày bắt đầu") LocalDate startDate,
            @NotNull(message = "Vui lòng nhập ngày kết thúc") LocalDate endDate) {
    }
}
