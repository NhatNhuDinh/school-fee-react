package com.dth.frw.module.school.academic.classgroup.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /class_groups} payload. The year decides the school, so there is
 * no school to name here.
 */
public record ClassGroupRequest(
        @NotNull(message = "Vui lòng chọn năm học") @Valid AcademicYearRef academicYear,
        @NotBlank(message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name) {

    /** The year, by id. Only the id is read; anything else on the object is ignored. */
    public record AcademicYearRef(@NotNull(message = "Vui lòng chọn năm học") Long id) {
    }
}
