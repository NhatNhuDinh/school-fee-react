package com.dth.frw.module.school.fee.item.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.domain.school.fee.FeeItem;
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
 * What a row-level policy on {@code fee_items} may say, and the tenant boundary
 * that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class FeeItemRowLevelPolicies implements RowLevelPolicySet<FeeItem> {

    private final TenantFilters tenantFilters;

    public FeeItemRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "fee_items";
    }

    @Override
    public Class<FeeItem> entityClass() {
        return FeeItem.class;
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
                FieldDescriptor.column("basePrice", BigDecimal.class, true),
                FieldDescriptor.column("vatRate", BigDecimal.class, false),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("academicYear", AcademicYear.class),
                FieldDescriptor.reference("measurementUnit", MeasurementUnit.class),
                FieldDescriptor.reference("scopeClassGroup", ClassGroup.class),
                FieldDescriptor.reference("scopeClass", SchoolClass.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<FeeItem>> filters() {
        return tenantFilters.forEveryAction();
    }
}
