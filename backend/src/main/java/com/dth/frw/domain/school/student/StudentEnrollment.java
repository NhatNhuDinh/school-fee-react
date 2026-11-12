package com.dth.frw.domain.school.student;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.shared.security.rowlevel.RowLevelResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A student in one school year: which class they are in, under which training
 * system, and since when. This is the row the student list shows, and the
 * {@code students} resource is this entity — a student with no enrollment in the
 * year chosen does not appear.
 *
 * <p>A student has at most one enrollment per year. {@code schoolClass} is null
 * for a student carried over from a previous year and not yet placed.
 * {@code sourceEnrollment} remembers which enrollment they were carried over from.
 */
@RowLevelResource("students")
@Entity
@Table(
        name = "student_enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_student_enrollments_year_student",
                columnNames = {"academic_year_id", "student_id"}))
public class StudentEnrollment implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_class_id")
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "training_system_id")
    private TrainingSystem trainingSystem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_enrollment_id")
    private StudentEnrollment sourceEnrollment;

    @Column(name = "transfer_in_date")
    private LocalDate transferInDate;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected StudentEnrollment() {
        // for JPA
    }

    public StudentEnrollment(Student student, AcademicYear academicYear) {
        this.student = student;
        this.academicYear = academicYear;
        this.organization = academicYear.getOrganization();
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

    public Student getStudent() {
        return student;
    }

    public AcademicYear getAcademicYear() {
        return academicYear;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public void setSchoolClass(SchoolClass schoolClass) {
        this.schoolClass = schoolClass;
    }

    public TrainingSystem getTrainingSystem() {
        return trainingSystem;
    }

    public void setTrainingSystem(TrainingSystem trainingSystem) {
        this.trainingSystem = trainingSystem;
    }

    public StudentEnrollment getSourceEnrollment() {
        return sourceEnrollment;
    }

    public void setSourceEnrollment(StudentEnrollment sourceEnrollment) {
        this.sourceEnrollment = sourceEnrollment;
    }

    public LocalDate getTransferInDate() {
        return transferInDate;
    }

    public void setTransferInDate(LocalDate transferInDate) {
        this.transferInDate = transferInDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
        if (!(other instanceof StudentEnrollment that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
