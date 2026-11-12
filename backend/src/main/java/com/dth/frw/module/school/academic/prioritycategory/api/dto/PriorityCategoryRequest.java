package com.dth.frw.module.school.academic.prioritycategory.api.dto;

import com.dth.frw.domain.school.academic.DiscountType;
import com.dth.frw.domain.school.academic.PriorityApplicationPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code POST /priority_categories} payload. The year decides the school, so
 * there is no school to name here. {@code fromMonth} and {@code toMonth} are
 * read only when the period is {@code CUSTOM}.
 */
public record PriorityCategoryRequest(
        @NotNull(message = "Vui lòng chọn năm học") @Valid AcademicYearRef academicYear,
        @NotBlank(message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name,
        @NotNull(message = "Vui lòng chọn loại miễn giảm") DiscountType discountType,
        @NotNull(message = "Vui lòng nhập mức miễn giảm")
        @DecimalMin(value = "0.00", message = "Mức miễn giảm không được âm")
        BigDecimal discountValue,
        @NotNull(message = "Vui lòng chọn thời gian áp dụng") PriorityApplicationPeriod applicationPeriod,
        @Min(value = 1, message = "Tháng từ 1 đến 12") @Max(value = 12, message = "Tháng từ 1 đến 12")
        Integer fromMonth,
        @Min(value = 1, message = "Tháng từ 1 đến 12") @Max(value = 12, message = "Tháng từ 1 đến 12")
        Integer toMonth) {

    /** The year, by id. Only the id is read; anything else on the object is ignored. */
    public record AcademicYearRef(@NotNull(message = "Vui lòng chọn năm học") Long id) {
    }
}
