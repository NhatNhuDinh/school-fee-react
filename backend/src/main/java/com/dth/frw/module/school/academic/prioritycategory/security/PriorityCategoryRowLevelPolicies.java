package com.dth.frw.module.school.academic.prioritycategory.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.PriorityCategory;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code priority_categories} may say, and the tenant boundary
 * that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class PriorityCategoryRowLevelPolicies implements RowLevelPolicySet<PriorityCategory> {

    private final TenantFilters tenantFilters;

    public PriorityCategoryRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "priority_categories";
    }

    @Override
    public Class<PriorityCategory> entityClass() {
        return PriorityCategory.class;
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
                FieldDescriptor.column("discountValue", BigDecimal.class, false),
                FieldDescriptor.column("fromMonth", Integer.class, true),
                FieldDescriptor.column("toMonth", Integer.class, true),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("academicYear", AcademicYear.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<PriorityCategory>> filters() {
        return tenantFilters.forEveryAction();
    }
}
