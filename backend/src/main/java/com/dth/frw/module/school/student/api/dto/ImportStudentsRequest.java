package com.dth.frw.module.school.student.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * {@code POST /students/import}: carry the chosen enrollments of an earlier year
 * into a later one. {@code ids} are the earlier year's enrollments.
 */
public record ImportStudentsRequest(
        @NotNull(message = "Vui lòng chọn năm học nguồn") Long sourceAcademicYearId,
        @NotNull(message = "Vui lòng chọn năm học đích") Long targetAcademicYearId,
        @NotEmpty(message = "Vui lòng chọn học sinh") List<Long> ids) {
}
