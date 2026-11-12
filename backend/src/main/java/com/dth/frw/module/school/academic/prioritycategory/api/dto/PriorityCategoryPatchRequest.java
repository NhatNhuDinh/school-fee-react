package com.dth.frw.module.school.academic.prioritycategory.api.dto;

import com.dth.frw.domain.school.academic.DiscountType;
import com.dth.frw.domain.school.academic.PriorityApplicationPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code PATCH /priority_categories/{id}} payload — a {@code null} field means
 * "leave it unchanged". The year is not patchable: moving a row to another year
 * is not an edit.
 */
public record PriorityCategoryPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name,
        DiscountType discountType,
        @DecimalMin(value = "0.00", message = "Mức miễn giảm không được âm")
        BigDecimal discountValue,
        PriorityApplicationPeriod applicationPeriod,
        @Min(value = 1, message = "Tháng từ 1 đến 12") @Max(value = 12, message = "Tháng từ 1 đến 12")
        Integer fromMonth,
        @Min(value = 1, message = "Tháng từ 1 đến 12") @Max(value = 12, message = "Tháng từ 1 đến 12")
        Integer toMonth) {
}
