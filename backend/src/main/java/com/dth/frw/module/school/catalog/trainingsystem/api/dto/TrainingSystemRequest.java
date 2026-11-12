package com.dth.frw.module.school.catalog.trainingsystem.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /training_systems} payload. {@code organization} is optional because
 * somebody placed in a school creates for that school; only a system
 * administrator, who belongs to none, has to say which.
 */
public record TrainingSystemRequest(
        @Valid OrganizationRef organization,
        @NotBlank(message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name) {

    /** The school, by id. Only the id is read; anything else on the object is ignored. */
    public record OrganizationRef(Long id) {
    }
}
