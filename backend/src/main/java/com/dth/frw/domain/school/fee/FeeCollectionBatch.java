package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A đợt thu of one school year: the khoản thu collected from a group of students in
 * one month. A name is unique within its year, and the row never changes year.
 *
 * <p>Its counters (khoản thu, học sinh, phải thu, đã thu) are totals kept by the service
 * after every write that moves them — they are what the list reads, and what the
 * {@linkplain #status() status} is derived from. {@link #getOrganization()} repeats the
 * year's school so the tenant filter can read it without a join. Its khoản thu, học sinh
 * and công nợ are separate rows that belong to it and are reached through it. Under
 * row-level management as {@code fee_collection_batches}.
 */
@RowLevelResource("fee_collection_batches")
@Entity
@Table(
        name = "fee_collection_batches",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fee_collection_batches_year_name",
                columnNames = {"academic_year_id", "name"}))
public class FeeCollectionBatch implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(nullable = false)
    private String name;

    @Column(name = "collection_month", nullable = false)
    private int collectionMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FeeCollectionScope scope = FeeCollectionScope.ALL_SCHOOL;

    /** Set only when the scope is {@code CLASS_GROUP}. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scope_class_group_id")
    private ClassGroup scopeClassGroup;

    /** Set only when the scope is {@code CLASS}. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scope_class_id")
    private SchoolClass scopeClass;

    @Column(name = "fee_item_count", nullable = false)
    private int feeItemCount;

    @Column(name = "student_count", nullable = false)
    private int studentCount;

    @Column(name = "receivable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal receivableAmount = BigDecimal.ZERO;

    @Column(name = "collected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FeeCollectionBatch() {
        // for JPA
    }

    public FeeCollectionBatch(AcademicYear academicYear, String name, int collectionMonth) {
        this.academicYear = academicYear;
        this.organization = academicYear.getOrganization();
        this.name = name;
        this.collectionMonth = collectionMonth;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Read off the money: nothing collected, all of it collected, or somewhere between. */
    public FeeCollectionBatchStatus status() {
        if (collectedAmount.signum() <= 0) {
            return FeeCollectionBatchStatus.NOT_STARTED;
        }
        if (collectedAmount.compareTo(receivableAmount) >= 0) {
            return FeeCollectionBatchStatus.COMPLETED;
        }
        return FeeCollectionBatchStatus.COLLECTING;
    }

    public Long getId() {
        return id;
    }

    @Override
    public Organization getOrganization() {
        return organization;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public String getName() {
        return name;
    }

    public int getCollectionMonth() {
        return collectionMonth;
    }

    public FeeCollectionScope getScope() {
        return scope;
    }

    public void setScope(FeeCollectionScope scope) {
        this.scope = scope;
    }

    public ClassGroup getScopeClassGroup() {
        return scopeClassGroup;
    }

    public void setScopeClassGroup(ClassGroup scopeClassGroup) {
        this.scopeClassGroup = scopeClassGroup;
    }

    public SchoolClass getScopeClass() {
        return scopeClass;
    }

    public void setScopeClass(SchoolClass scopeClass) {
        this.scopeClass = scopeClass;
    }

    public int getFeeItemCount() {
        return feeItemCount;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public BigDecimal getReceivableAmount() {
        return receivableAmount;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FeeCollectionBatch that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
