package com.dth.frw.module.school.student.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** {@code POST /students/assign-class}: put the chosen enrollments into one class. */
public record AssignClassRequest(
        @NotEmpty(message = "Vui lòng chọn học sinh") List<Long> ids,
        @NotNull(message = "Vui lòng chọn lớp") Long schoolClassId,
        @NotNull(message = "Vui lòng chọn ngày chuyển đến") LocalDate transferInDate) {
}
