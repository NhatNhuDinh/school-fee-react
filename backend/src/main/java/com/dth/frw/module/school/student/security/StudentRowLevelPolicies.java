package com.dth.frw.module.school.student.security;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.domain.school.student.StudentEnrollment;
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
 * What a row-level policy on {@code students} may say, and the tenant boundary
 * that is always in force. The managed row is the enrollment: the student in a
 * year.
 *
 * <p>{@code FULL}: a role with no scope of its own is not narrowed beyond the
 * school boundary, which the design-time filters below apply to everybody.
 */
@Component
public class StudentRowLevelPolicies implements RowLevelPolicySet<StudentEnrollment> {

    private final TenantFilters tenantFilters;

    public StudentRowLevelPolicies(TenantFilters tenantFilters) {
        this.tenantFilters = tenantFilters;
    }

    @Override
    public String resource() {
        return "students";
    }

    @Override
    public Class<StudentEnrollment> entityClass() {
        return StudentEnrollment.class;
    }

    @Override
    public DefaultScope defaultScope() {
        return DefaultScope.FULL;
    }

    @Override
    public List<FieldDescriptor> fields() {
        return List.of(
                FieldDescriptor.id("id"),
                FieldDescriptor.column("transferInDate", LocalDate.class, true),
                FieldDescriptor.column("createdAt", Instant.class, false),
                FieldDescriptor.reference("organization", Organization.class),
                FieldDescriptor.reference("academicYear", AcademicYear.class),
                FieldDescriptor.reference("schoolClass", SchoolClass.class),
                FieldDescriptor.reference("trainingSystem", TrainingSystem.class));
    }

    @Override
    public Set<String> notCheckSafe() {
        return Set.of("id", "createdAt", "updatedAt");
    }

    @Override
    public List<DesignTimeFilter<StudentEnrollment>> filters() {
        return tenantFilters.forEveryAction();
    }
}
