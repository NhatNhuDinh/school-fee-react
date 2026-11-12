package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.school.academic.ClassGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One line of a khoản thu's price list: the amount for a khối lớp, optionally for
 * one area of it. Owned by the khoản thu and never read on its own, so it has no
 * resource name, no permissions and no row-level policy of its own.
 */
@Entity
@Table(name = "fee_item_prices")
public class FeeItemPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_item_id", nullable = false)
    private FeeItem feeItem;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "class_group_id", nullable = false)
    private ClassGroup classGroup;

    @Column(name = "area_name", length = 100)
    private String areaName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    protected FeeItemPrice() {
        // for JPA
    }

    public FeeItemPrice(FeeItem feeItem, ClassGroup classGroup, String areaName, BigDecimal amount) {
        this.feeItem = feeItem;
        this.classGroup = classGroup;
        this.areaName = areaName;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public FeeItem getFeeItem() {
        return feeItem;
    }

    public ClassGroup getClassGroup() {
        return classGroup;
    }

    public String getAreaName() {
        return areaName;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
