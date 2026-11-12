package com.dth.frw.module.school.fee.notification.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code fee_notifications} may say, and the school boundary that
 * is always in force. {@code FULL}: a role with no scope of its own is not narrowed beyond
 * the school.
 */
@Component
public class FeeNotificationRowLevelPolicies implements RowLevelPolicySet<FeeNotification> {

    private final TenantFilters tenantFilters;

    public FeeNotificationRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "fee_notifications";
    }

    @Override
    public Class<FeeNotification> entityClass() {
        return FeeNotification.class;
    }

    @Override
    public DefaultScope defaultScope() {
        return DefaultScope.FULL;
    }

    @Override
    public List<FieldDescriptor> fields() {
        return List.of(
                FieldDescriptor.id("id"),
                FieldDescriptor.column("subject", String.class, false),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("academicYear", AcademicYear.class),
                FieldDescriptor.reference("batch", FeeCollectionBatch.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<FeeNotification>> filters() {
        return tenantFilters.forEveryAction();
    }
}
