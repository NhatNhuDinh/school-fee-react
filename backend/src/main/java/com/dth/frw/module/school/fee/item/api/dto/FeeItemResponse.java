package com.dth.frw.module.school.fee.item.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicPeriod;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.domain.school.fee.FeeCollectionCycle;
import com.dth.frw.domain.school.fee.FeeCollectionSchedule;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeeItemPrice;
import com.dth.frw.domain.school.fee.FeeNature;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.FeeScope;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire shape of a khoản thu. The year, the khối lớp and lớp it is scoped to and
 * the đơn vị tính are nested because the list reads their names; the summaries are
 * declared here rather than imported, so widening another API cannot widen this one.
 */
public record FeeItemResponse(
        Long id,
        OrganizationSummary organization,
        AcademicYearSummary academicYear,
        String name,
        FeeScope scope,
        NamedSummary scopeClassGroup,
        NamedSummary scopeClass,
        FeePriceMode priceMode,
        BigDecimal basePrice,
        NamedSummary measurementUnit,
        BigDecimal vatRate,
        FeeNature nature,
        FeeCollectionCycle collectionCycle,
        boolean discountEligible,
        boolean mandatory,
        boolean serviceFee,
        boolean refundable,
        boolean invoiceEnabled,
        boolean receiptEnabled,
        boolean internalCollection,
        BigDecimal invoiceRate,
        boolean active,
        List<PriceSummary> prices,
        List<ScheduleSummary> collectionSchedules,
        Instant createdAt,
        Instant updatedAt,
        /** What this caller may do to this row, as {@code {"write": true, "delete": false}}. */
        @JsonProperty("_can") Map<String, Boolean> can) {

    public record OrganizationSummary(Long id, String code, String name) {
        static OrganizationSummary from(Organization organization) {
            return new OrganizationSummary(organization.getId(), organization.getCode(), organization.getName());
        }
    }

    public record AcademicYearSummary(Long id, String name) {
        static AcademicYearSummary from(AcademicYear year) {
            return new AcademicYearSummary(year.getId(), year.getName());
        }
    }

    /** A record named by one field: a khối lớp, a lớp or an đơn vị tính. */
    public record NamedSummary(Long id, String name) {
        static NamedSummary from(ClassGroup group) {
            return group == null ? null : new NamedSummary(group.getId(), group.getName());
        }

        static NamedSummary from(SchoolClass schoolClass) {
            return schoolClass == null ? null : new NamedSummary(schoolClass.getId(), schoolClass.getName());
        }

        static NamedSummary from(MeasurementUnit unit) {
            return unit == null ? null : new NamedSummary(unit.getId(), unit.getName());
        }

        static NamedSummary from(AcademicPeriod period) {
            return period == null ? null : new NamedSummary(period.getId(), period.getName());
        }
    }

    public record PriceSummary(Long id, NamedSummary classGroup, String areaName, BigDecimal amount) {
        static PriceSummary from(FeeItemPrice price) {
            return new PriceSummary(
                    price.getId(), NamedSummary.from(price.getClassGroup()), price.getAreaName(), price.getAmount());
        }
    }

    public record ScheduleSummary(Long id, int sequence, int collectionMonth, NamedSummary academicPeriod) {
        static ScheduleSummary from(FeeCollectionSchedule schedule) {
            return new ScheduleSummary(
                    schedule.getId(),
                    schedule.getSequence(),
                    schedule.getCollectionMonth(),
                    NamedSummary.from(schedule.getAcademicPeriod()));
        }
    }

    /** Without row flags — for the echo of a deleted row. */
    public static FeeItemResponse from(FeeItem row) {
        return from(row, null);
    }

    public static FeeItemResponse from(FeeItem row, RowLevelSecurity rowLevel) {
        return new FeeItemResponse(
                row.getId(),
                OrganizationSummary.from(row.getOrganization()),
                AcademicYearSummary.from(row.getAcademicYear()),
                row.getName(),
                row.getScope(),
                NamedSummary.from(row.getScopeClassGroup()),
                NamedSummary.from(row.getScopeClass()),
                row.getPriceMode(),
                row.getBasePrice(),
                NamedSummary.from(row.getMeasurementUnit()),
                row.getVatRate(),
                row.getNature(),
                row.getCollectionCycle(),
                row.isDiscountEligible(),
                row.isMandatory(),
                row.isServiceFee(),
                row.isRefundable(),
                row.isInvoiceEnabled(),
                row.isReceiptEnabled(),
                row.isInternalCollection(),
                row.getInvoiceRate(),
                row.isActive(),
                row.getPrices().stream().map(PriceSummary::from).toList(),
                row.getCollectionSchedules().stream().map(ScheduleSummary::from).toList(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
