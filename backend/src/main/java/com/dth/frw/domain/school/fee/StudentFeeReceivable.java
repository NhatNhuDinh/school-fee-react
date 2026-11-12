package com.dth.frw.domain.school.fee;

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
 * What one student owes for one khoản thu of a đợt thu: the unit price they were
 * charged, the quantity, the amount ({@code unit price × quantity}, VAT already in the
 * price) and how much of it has been collected. A row exists exactly when the khoản
 * thu applies to the student. Written in bulk by the service, which is why the
 * columns are all plain values; read through the đợt thu it belongs to.
 */
@Entity
@Table(
        name = "student_fee_receivables",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_student_fee_receivables_item_student",
                columnNames = {"batch_item_id", "batch_student_id"}))
public class StudentFeeReceivable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_item_id", nullable = false)
    private FeeCollectionBatchItem batchItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_student_id", nullable = false)
    private FeeCollectionBatchStudent batchStudent;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "receivable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal receivableAmount;

    @Column(name = "collected_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    protected StudentFeeReceivable() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public FeeCollectionBatchItem getBatchItem() {
        return batchItem;
    }

    public FeeCollectionBatchStudent getBatchStudent() {
        return batchStudent;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
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
        if (!(other instanceof StudentFeeReceivable that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
