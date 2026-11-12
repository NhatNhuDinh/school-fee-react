package com.dth.frw.module.school.fee.registration.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.StudentFeeRegistration;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code fee_registrations} may say, and the tenant
 * boundary that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class FeeRegistrationRowLevelPolicies implements RowLevelPolicySet<StudentFeeRegistration> {

    private final TenantFilters tenantFilters;

    public FeeRegistrationRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "fee_registrations";
    }

    @Override
    public Class<StudentFeeRegistration> entityClass() {
        return StudentFeeRegistration.class;
    }

    @Override
    public DefaultScope defaultScope() {
        return DefaultScope.FULL;
    }

    @Override
    public List<FieldDescriptor> fields() {
        return List.of(
                FieldDescriptor.id("id"),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("enrollment", StudentEnrollment.class),
                FieldDescriptor.reference("feeItem", FeeItem.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt");
    }

    @Override
    public List<DesignTimeFilter<StudentFeeRegistration>> filters() {
        return tenantFilters.forEveryAction();
    }
}
