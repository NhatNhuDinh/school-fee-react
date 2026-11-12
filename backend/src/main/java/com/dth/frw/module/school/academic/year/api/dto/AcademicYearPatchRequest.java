package com.dth.frw.module.school.academic.year.api.dto;

import com.dth.frw.module.school.academic.year.api.dto.AcademicYearRequest.PeriodRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code PATCH /academic_years/{id}} payload — a {@code null} field means "leave
 * it unchanged".
 *
 * <p>{@code periods}, when present, is the complete list the year should end up
 * with: terms missing from it are removed. A year's school is not patchable;
 * moving a year to another school is not an edit.
 */
public record AcademicYearPatchRequest(
        LocalDate startDate,
        LocalDate endDate,
        @Valid List<PeriodRequest> periods) {
}
