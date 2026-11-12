package com.dth.frw.module.school.academic.prioritycategory.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.DiscountType;
import com.dth.frw.domain.school.academic.PriorityApplicationPeriod;
import com.dth.frw.domain.school.academic.PriorityCategory;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a diện ưu tiên. {@code organization} and {@code academicYear} are
 * nested because the list reads their names; the summaries are declared here
 * rather than imported, so widening another API cannot widen this one.
 */
public record PriorityCategoryResponse(
        Long id,
        OrganizationSummary organization,
        AcademicYearSummary academicYear,
        String name,
        DiscountType discountType,
        BigDecimal discountValue,
        PriorityApplicationPeriod applicationPeriod,
        Integer fromMonth,
        Integer toMonth,
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

    /** Without row flags — for the echo of a deleted row. */
    public static PriorityCategoryResponse from(PriorityCategory row) {
        return from(row, null);
    }

    public static PriorityCategoryResponse from(PriorityCategory row, RowLevelSecurity rowLevel) {
        return new PriorityCategoryResponse(
                row.getId(),
                OrganizationSummary.from(row.getOrganization()),
                AcademicYearSummary.from(row.getAcademicYear()),
                row.getName(),
                row.getDiscountType(),
                row.getDiscountValue(),
                row.getApplicationPeriod(),
                row.getFromMonth(),
                row.getToMonth(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
