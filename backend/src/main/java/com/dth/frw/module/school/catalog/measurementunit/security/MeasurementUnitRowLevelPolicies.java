package com.dth.frw.module.school.catalog.measurementunit.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code measurement_units} may say, and the tenant boundary
 * that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class MeasurementUnitRowLevelPolicies implements RowLevelPolicySet<MeasurementUnit> {

    private final TenantFilters tenantFilters;

    public MeasurementUnitRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "measurement_units";
    }

    @Override
    public Class<MeasurementUnit> entityClass() {
        return MeasurementUnit.class;
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
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<MeasurementUnit>> filters() {
        return tenantFilters.forEveryAction();
    }
}
