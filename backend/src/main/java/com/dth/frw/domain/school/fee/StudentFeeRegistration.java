package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.shared.security.rowlevel.RowLevelResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

/**
 * One student registered for one optional khoản thu: a row exists exactly when the
 * registration does, and unregistering deletes it. There is no quantity and no
 * state — registration is a plain yes or no per (student, khoản thu).
 *
 * <p>The student here is the <em>enrollment</em>, the student in one school year, so a
 * registration belongs to its year by way of both its parents and the service checks
 * they agree. It follows the enrollment when the student changes class, and goes
 * with it when the enrollment is deleted. {@link #getOrganization()} repeats the
 * enrollment's school so the tenant filter can read it without a join. Under
 * row-level management as {@code fee_registrations}.
 */
@RowLevelResource("fee_registrations")
@Entity
@Table(
        name = "student_fee_registrations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_student_fee_registrations_enrollment_fee_item",
                columnNames = {"enrollment_id", "fee_item_id"}))
public class StudentFeeRegistration implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private StudentEnrollment enrollment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fee_item_id", nullable = false)
    private FeeItem feeItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FeeRegistrationSource source = FeeRegistrationSource.SCHOOL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudentFeeRegistration() {
        // for JPA
    }

    public StudentFeeRegistration(StudentEnrollment enrollment, FeeItem feeItem, FeeRegistrationSource source) {
        this.enrollment = enrollment;
        this.feeItem = feeItem;
        this.organization = enrollment.getOrganization();
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    @Override
    public Organization getOrganization() {
        return organization;
    }

    public StudentEnrollment getEnrollment() {
        return enrollment;
    }

    public FeeItem getFeeItem() {
        return feeItem;
    }

    public FeeRegistrationSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StudentFeeRegistration that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
