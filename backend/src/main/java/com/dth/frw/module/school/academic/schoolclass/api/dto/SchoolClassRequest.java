package com.dth.frw.module.school.academic.schoolclass.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /school_classes} payload. The khối lớp decides the year and the school, so there is
 * no school to name here.
 */
public record SchoolClassRequest(
        @NotNull(message = "Vui lòng chọn khối lớp") @Valid ClassGroupRef classGroup,
        @NotBlank(message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name) {

    /** The khối lớp, by id. Only the id is read; anything else on the object is ignored. */
    public record ClassGroupRef(@NotNull(message = "Vui lòng chọn khối lớp") Long id) {
    }
}
