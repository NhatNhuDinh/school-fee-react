package com.dth.frw.module.school.academic.year.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code academic_years} may say, and the tenant
 * boundary that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class AcademicYearRowLevelPolicies implements RowLevelPolicySet<AcademicYear> {

    private final TenantFilters tenantFilters;

    public AcademicYearRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "academic_years";
    }

    @Override
    public Class<AcademicYear> entityClass() {
        return AcademicYear.class;
    }

    @Override
    public DefaultScope defaultScope() {
        return DefaultScope.FULL;
    }

    @Override
    public List<FieldDescriptor> fields() {
        return List.of(
                FieldDescriptor.id("id"),
                FieldDescriptor.column("name", String.class, false),
                FieldDescriptor.column("startDate", LocalDate.class, false),
                FieldDescriptor.column("endDate", LocalDate.class, false),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<AcademicYear>> filters() {
        return tenantFilters.forEveryAction();
    }
}
