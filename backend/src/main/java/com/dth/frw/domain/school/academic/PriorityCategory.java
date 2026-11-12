package com.dth.frw.domain.school.academic;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
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
 * A diện ưu tiên of one school year, such as {@code Con thương binh}. A name is
 * unique within its year.
 *
 * <p>{@link #getOrganization()} repeats the year's school so the tenant filter
 * can read it without a join; the service keeps the two in step and a row never
 * changes year. Under row-level management as {@code priority_categories}.
 */
@RowLevelResource("priority_categories")
@Entity
@Table(
        name = "priority_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_priority_categories_year_name",
                columnNames = {"academic_year_id", "name"}))
public class PriorityCategory implements TenantOwned {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 50)
    private DiscountType discountType = DiscountType.PERCENT;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_period", nullable = false, length = 50)
    private PriorityApplicationPeriod applicationPeriod = PriorityApplicationPeriod.FULL_YEAR;

    /** Only meaningful when the period is {@code CUSTOM}; null otherwise. */
    @Column(name = "from_month")
    private Integer fromMonth;

    @Column(name = "to_month")
    private Integer toMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PriorityCategory() {
        // for JPA
    }

    public PriorityCategory(AcademicYear academicYear, String name) {
        this.academicYear = academicYear;
        this.organization = academicYear.getOrganization();
        this.name = name;
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

    public void setName(String name) {
        this.name = name;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public PriorityApplicationPeriod getApplicationPeriod() {
        return applicationPeriod;
    }

    public void setApplicationPeriod(PriorityApplicationPeriod applicationPeriod) {
        this.applicationPeriod = applicationPeriod;
    }

    public Integer getFromMonth() {
        return fromMonth;
    }

    public void setFromMonth(Integer fromMonth) {
        this.fromMonth = fromMonth;
    }

    public Integer getToMonth() {
        return toMonth;
    }

    public void setToMonth(Integer toMonth) {
        this.toMonth = toMonth;
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
        if (!(other instanceof PriorityCategory that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
