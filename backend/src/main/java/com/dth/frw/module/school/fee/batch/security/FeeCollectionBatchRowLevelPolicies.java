package com.dth.frw.module.school.fee.batch.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.fee.FeeCollectionBatch;
import com.dth.frw.module.school.tenant.service.TenantFilters;
import com.dth.frw.shared.security.rowlevel.DefaultScope;
import com.dth.frw.shared.security.rowlevel.FieldDescriptor;
import com.dth.frw.shared.security.rowlevel.RowLevelPolicySet;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What a row-level policy on {@code fee_collection_batches} may say, and the tenant
 * boundary that is always in force.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the school
 * boundary, which the design-time filter below applies to everybody.
 */
@Component
public class FeeCollectionBatchRowLevelPolicies implements RowLevelPolicySet<FeeCollectionBatch> {

    private final TenantFilters tenantFilters;

    public FeeCollectionBatchRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "fee_collection_batches";
    }

    @Override
    public Class<FeeCollectionBatch> entityClass() {
        return FeeCollectionBatch.class;
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
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("academicYear", AcademicYear.class),
                FieldDescriptor.reference("scopeClassGroup", ClassGroup.class),
                FieldDescriptor.reference("scopeClass", SchoolClass.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<FeeCollectionBatch>> filters() {
        return tenantFilters.forEveryAction();
    }
}
