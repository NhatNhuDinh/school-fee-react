package com.dth.frw.module.school.academic.schoolclass.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /school_classes/{id}} payload — a {@code null} field means "leave it
 * unchanged". The school is not patchable: moving a row to another school is
 * not an edit.
 */
public record SchoolClassPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name) {
}
