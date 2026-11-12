package com.dth.frw.domain.school.academic;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.shared.security.rowlevel.RowLevelResource;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A school year, such as {@code 2026-2027}, with the terms inside it.
 *
 * <p>The name is derived from the two dates and never typed, so it cannot
 * disagree with them. It is unique within a school: two schools both having a
 * 2026-2027 is the normal case.
 *
 * <p>Under row-level management as {@code academic_years}. Which school a row
 * belongs to is {@link #getOrganization()}; see {@code TenantFilters}.
 */
@RowLevelResource("academic_years")
@Entity
@Table(
        name = "academic_years",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_academic_years_organization_name",
                columnNames = {"organization_id", "name"}))
public class AcademicYear implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Eager: every row of the list shows the school's name.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    // A year has a handful of terms and every screen that shows the year shows
    // them, so a lazy collection would only turn one query into two.
    @OneToMany(mappedBy = "academicYear", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("startDate ASC")
    private List<AcademicPeriod> periods = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AcademicYear() {
        // for JPA
    }

    public AcademicYear(Organization organization, String name, LocalDate startDate, LocalDate endDate) {
        this.organization = organization;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<AcademicPeriod> getPeriods() {
        return periods;
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
        if (!(other instanceof AcademicYear year)) {
            return false;
        }
        return id != null && id.equals(year.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
