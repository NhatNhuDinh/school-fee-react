package com.dth.frw.module.school.fee.item.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionCycle;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.PriceRow;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.Ref;
import com.dth.frw.module.school.fee.item.api.dto.FeeItemRequest.ScheduleRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code PATCH /fee_items/{id}} payload — a {@code null} field means "leave it
 * unchanged". The year is not patchable: moving a row to another year is not an
 * edit. {@code prices} and {@code collectionSchedules} replace the whole list when
 * they are sent, and leave it alone when they are not.
 */
public record FeeItemPatchRequest(
        @Pattern(regexp = ".*\\S.*", message = "Vui lòng nhập tên khoản thu")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name,
        FeeScope scope,
        @Valid Ref scopeClassGroup,
        @Valid Ref scopeClass,
        FeePriceMode priceMode,
        @DecimalMin(value = "0.00", message = "Đơn giá không được âm") BigDecimal basePrice,
        @Valid Ref measurementUnit,
        @DecimalMin(value = "0.00", message = "Thuế suất từ 0 đến 100%")
        @DecimalMax(value = "100.00", message = "Thuế suất từ 0 đến 100%")
        BigDecimal vatRate,
        FeeCollectionCycle collectionCycle,
        Boolean discountEligible,
        Boolean mandatory,
        Boolean serviceFee,
        Boolean refundable,
        Boolean invoiceEnabled,
        Boolean receiptEnabled,
        Boolean internalCollection,
        @DecimalMin(value = "0.00", message = "Tỷ lệ từ 0 đến 100%")
        @DecimalMax(value = "100.00", message = "Tỷ lệ từ 0 đến 100%")
        BigDecimal invoiceRate,
        Boolean active,
        List<@Valid PriceRow> prices,
        List<@Valid ScheduleRow> collectionSchedules) {
}
