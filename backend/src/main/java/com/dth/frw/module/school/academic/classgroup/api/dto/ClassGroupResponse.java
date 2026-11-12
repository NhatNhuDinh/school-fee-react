package com.dth.frw.module.school.academic.classgroup.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a khối lớp. {@code organization} and {@code academicYear} are
 * nested because the list reads their names; the summaries are declared here
 * rather than imported, so widening another API cannot widen this one.
 */
public record ClassGroupResponse(
        Long id,
        OrganizationSummary organization,
        AcademicYearSummary academicYear,
        String name,
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
    public static ClassGroupResponse from(ClassGroup row) {
        return from(row, null);
    }

    public static ClassGroupResponse from(ClassGroup row, RowLevelSecurity rowLevel) {
        return new ClassGroupResponse(
                row.getId(),
                OrganizationSummary.from(row.getOrganization()),
                AcademicYearSummary.from(row.getAcademicYear()),
                row.getName(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
