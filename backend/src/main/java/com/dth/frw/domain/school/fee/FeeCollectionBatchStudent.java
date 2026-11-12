package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.student.StudentEnrollment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One student in a đợt thu, with the khối lớp and lớp they were in when it was made —
 * a student who changes class afterwards still belongs to the đợt they were charged
 * in. The two totals repeat the sum of the student's receivables so a list can filter
 * "còn nợ" without adding rows up; the service keeps them in step. Owned by the đợt
 * thu, reached through it.
 */
@Entity
@Table(
        name = "fee_collection_batch_students",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fee_collection_batch_students_batch_enrollment",
                columnNames = {"batch_id", "enrollment_id"}))
public class FeeCollectionBatchStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private FeeCollectionBatch batch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private StudentEnrollment enrollment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "class_group_id", nullable = false)
    private ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "school_class_id", nullable = false)
    private SchoolClass schoolClass;

    @Column(name = "receivable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal receivableAmount = BigDecimal.ZERO;

    @Column(name = "collected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    protected FeeCollectionBatchStudent() {
        // for JPA
    }

    public FeeCollectionBatchStudent(FeeCollectionBatch batch, StudentEnrollment enrollment) {
        this.batch = batch;
        this.enrollment = enrollment;
        this.schoolClass = enrollment.getSchoolClass();
        this.classGroup = enrollment.getSchoolClass().getClassGroup();
    }

    public Long getId() {
        return id;
    }

    public FeeCollectionBatch getBatch() {
        return batch;
    }

    public StudentEnrollment getEnrollment() {
        return enrollment;
    }

    public ClassGroup getClassGroup() {
        return classGroup;
    }

    public SchoolClass getSchoolClass() {
        return schoolClass;
    }

    public BigDecimal getReceivableAmount() {
        return receivableAmount;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FeeCollectionBatchStudent that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
