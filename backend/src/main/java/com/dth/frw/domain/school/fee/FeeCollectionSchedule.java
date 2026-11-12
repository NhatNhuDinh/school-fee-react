package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.school.academic.AcademicPeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One collection date of a khoản thu: the month it falls due in a quarter, an
 * academic period, or the year. A monthly khoản thu has none. Owned by the
 * khoản thu, like its prices.
 */
@Entity
@Table(name = "fee_collection_schedules")
public class FeeCollectionSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_item_id", nullable = false)
    private FeeItem feeItem;

    /** Which collection this is: the quarter (1-4), the period's order, or 1 for a year. */
    @Column(nullable = false)
    private int sequence;

    @Column(name = "collection_month", nullable = false)
    private int collectionMonth;

    /** Set for a semester schedule only. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_period_id")
    private AcademicPeriod academicPeriod;

    protected FeeCollectionSchedule() {
        // for JPA
    }

    public FeeCollectionSchedule(FeeItem feeItem, int sequence, int collectionMonth, AcademicPeriod academicPeriod) {
        this.feeItem = feeItem;
        this.sequence = sequence;
        this.collectionMonth = collectionMonth;
        this.academicPeriod = academicPeriod;
    }

    /**
     * Moves this collection to another month in place. A collection that keeps its
     * sequence is updated rather than deleted and re-inserted: Hibernate inserts
     * before it deletes, and the (khoản thu, sequence) pair is unique.
     */
    public void moveTo(int collectionMonth, AcademicPeriod academicPeriod) {
        this.collectionMonth = collectionMonth;
        this.academicPeriod = academicPeriod;
    }

    public Long getId() {
        return id;
    }

    public FeeItem getFeeItem() {
        return feeItem;
    }

    public int getSequence() {
        return sequence;
    }

    public int getCollectionMonth() {
        return collectionMonth;
    }

    public AcademicPeriod getAcademicPeriod() {
        return academicPeriod;
    }
}
