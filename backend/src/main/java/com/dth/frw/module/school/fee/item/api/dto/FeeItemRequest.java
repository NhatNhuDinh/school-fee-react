package com.dth.frw.module.school.fee.item.api.dto;

import com.dth.frw.domain.school.fee.FeeCollectionCycle;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code POST /fee_items} payload. The year decides the school, so there is no
 * school to name here.
 *
 * <p>Which fields matter depends on others, and the service enforces it: a
 * {@code CLASS_GROUP} scope needs {@code scopeClassGroup}, a {@code SINGLE} price
 * needs {@code basePrice}, a {@code MULTIPLE} one needs {@code prices}, and every
 * cycle but {@code MONTHLY} needs its {@code collectionSchedules}. Fields that do
 * not apply to the chosen scope or mode are cleared rather than kept, so a row can
 * never contradict itself.
 */
public record FeeItemRequest(
        @NotNull(message = "Vui lòng chọn năm học") @Valid Ref academicYear,
        @NotBlank(message = "Vui lòng nhập tên khoản thu")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
        String name,
        @NotNull(message = "Vui lòng chọn phạm vi thu") FeeScope scope,
        @Valid Ref scopeClassGroup,
        @Valid Ref scopeClass,
        @NotNull(message = "Vui lòng chọn cách xác định đơn giá") FeePriceMode priceMode,
        @DecimalMin(value = "0.00", message = "Đơn giá không được âm") BigDecimal basePrice,
        @NotNull(message = "Vui lòng chọn đơn vị tính") @Valid Ref measurementUnit,
        @NotNull(message = "Vui lòng nhập thuế suất GTGT")
        @DecimalMin(value = "0.00", message = "Thuế suất từ 0 đến 100%")
        @DecimalMax(value = "100.00", message = "Thuế suất từ 0 đến 100%")
        BigDecimal vatRate,
        @NotNull(message = "Vui lòng chọn kỳ thu") FeeCollectionCycle collectionCycle,
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

    /** Another record, by id. Only the id is read. */
    public record Ref(@NotNull(message = "Thiếu mã") Long id) {
    }

    /** One line of the price list. */
    public record PriceRow(
            @NotNull(message = "Vui lòng chọn khối") @Valid Ref classGroup,
            @Size(max = 100, message = "Khu vực tối đa 100 ký tự") String areaName,
            @NotNull(message = "Vui lòng nhập đơn giá")
            @DecimalMin(value = "0.00", message = "Đơn giá không được âm")
            BigDecimal amount) {
    }

    /** One collection date: which collection it is, and the month it falls due in. */
    public record ScheduleRow(
            @NotNull(message = "Thiếu thứ tự kỳ thu") Integer sequence,
            @NotNull(message = "Vui lòng chọn tháng thu") Integer collectionMonth) {
    }
}
