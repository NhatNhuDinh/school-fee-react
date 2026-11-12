package com.dth.frw.module.school.catalog.trainingsystem.api.dto;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Wire shape of a hệ đào tạo. {@code organization} is nested because the list
 * reads {@code organization.name}; the summary is declared here rather than
 * imported, so widening the organization API cannot widen this one.
 */
public record TrainingSystemResponse(
        Long id,
        OrganizationSummary organization,
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

    /** Without row flags — for the echo of a deleted row. */
    public static TrainingSystemResponse from(TrainingSystem row) {
        return from(row, null);
    }

    public static TrainingSystemResponse from(TrainingSystem row, RowLevelSecurity rowLevel) {
        return new TrainingSystemResponse(
                row.getId(),
                OrganizationSummary.from(row.getOrganization()),
                row.getName(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
