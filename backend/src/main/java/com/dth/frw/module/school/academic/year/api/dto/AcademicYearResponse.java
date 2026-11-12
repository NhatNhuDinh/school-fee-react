package com.dth.frw.module.school.academic.year.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicPeriod;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Wire shape of an academic year.
 *
 * <p>{@code organization} is nested because the list reads
 * {@code organization.name} for its school column; the summary is declared here
 * rather than imported, so widening the organization API cannot widen this one.
 */
public record AcademicYearResponse(
        Long id,
        OrganizationSummary organization,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        List<PeriodSummary> periods,
        Instant createdAt,
        Instant updatedAt,
        /** What this caller may do to this row, as {@code {"write": true, "delete": false}}. */
        @JsonProperty("_can") Map<String, Boolean> can) {

    public record OrganizationSummary(Long id, String code, String name) {
        static OrganizationSummary from(Organization organization) {
            return new OrganizationSummary(organization.getId(), organization.getCode(), organization.getName());
        }
    }

    public record PeriodSummary(Long id, String name, LocalDate startDate, LocalDate endDate) {
        static PeriodSummary from(AcademicPeriod period) {
            return new PeriodSummary(period.getId(), period.getName(), period.getStartDate(), period.getEndDate());
        }
    }

    /** Without row flags — for the echo of a deleted row. */
    public static AcademicYearResponse from(AcademicYear year) {
        return from(year, null);
    }

    public static AcademicYearResponse from(AcademicYear year, RowLevelSecurity rowLevel) {
        return new AcademicYearResponse(
                year.getId(),
                OrganizationSummary.from(year.getOrganization()),
                year.getName(),
                year.getStartDate(),
                year.getEndDate(),
                year.getPeriods().stream().map(PeriodSummary::from).toList(),
                year.getCreatedAt(),
                year.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(year));
    }
}
