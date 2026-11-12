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
import java.util.Objects;

/**
 * One khoản thu in a đợt thu, with the name and unit it had when the đợt was made, so
 * renaming the khoản thu later does not rewrite a đợt already collected. The
 * quantity is the same for every student. Owned by the đợt thu and never read on its
 * own, so it has no resource name and no policy of its own: it is reached through a
 * đợt the caller was allowed to read.
 */
@Entity
@Table(
        name = "fee_collection_batch_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fee_collection_batch_items_batch_fee_item",
                columnNames = {"batch_id", "fee_item_id"}))
public class FeeCollectionBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private FeeCollectionBatch batch;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fee_item_id", nullable = false)
    private FeeItem feeItem;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false)
    private int sequence;

    protected FeeCollectionBatchItem() {
        // for JPA
    }

    public FeeCollectionBatchItem(FeeCollectionBatch batch, FeeItem feeItem, int quantity, int sequence) {
        this.batch = batch;
        this.feeItem = feeItem;
        this.name = feeItem.getName();
        this.unitName = feeItem.getMeasurementUnit().getName();
        this.quantity = quantity;
        this.sequence = sequence;
    }

    public Long getId() {
        return id;
    }

    public FeeCollectionBatch getBatch() {
        return batch;
    }

    public FeeItem getFeeItem() {
        return feeItem;
    }

    public String getName() {
        return name;
    }

    public String getUnitName() {
        return unitName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FeeCollectionBatchItem that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
