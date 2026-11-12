package com.dth.frw.module.school.academic.schoolclass.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a lớp. {@code organization} and {@code classGroup} are
 * nested because the list reads their names; the summaries are declared here
 * rather than imported, so widening another API cannot widen this one.
 */
public record SchoolClassResponse(
        Long id,
        OrganizationSummary organization,
        ClassGroupSummary classGroup,
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

    public record ClassGroupSummary(Long id, String name) {
        static ClassGroupSummary from(ClassGroup group) {
            return new ClassGroupSummary(group.getId(), group.getName());
        }
    }

    /** Without row flags — for the echo of a deleted row. */
    public static SchoolClassResponse from(SchoolClass row) {
        return from(row, null);
    }

    public static SchoolClassResponse from(SchoolClass row, RowLevelSecurity rowLevel) {
        return new SchoolClassResponse(
                row.getId(),
                OrganizationSummary.from(row.getOrganization()),
                ClassGroupSummary.from(row.getClassGroup()),
                row.getName(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
