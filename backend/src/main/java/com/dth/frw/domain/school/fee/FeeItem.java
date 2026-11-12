package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.MeasurementUnit;
import com.dth.frw.shared.security.rowlevel.RowLevelResource;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

/**
 * A khoản thu of one school year, such as {@code Học phí}: what is collected, from
 * whom, at what price, how often, and which rules apply to it. A name is unique
 * within its year.
 *
 * <p>{@link #getOrganization()} repeats the year's school so the tenant filter can
 * read it without a join; the service keeps the two in step and a row never
 * changes year. The price list and the collection dates belong to it: they are
 * written and removed with it, and have no identity outside it. Under row-level
 * management as {@code fee_items}.
 */
@RowLevelResource("fee_items")
@Entity
@Table(
        name = "fee_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_fee_items_year_name",
                columnNames = {"academic_year_id", "name"}))
public class FeeItem implements TenantOwned {

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
    @Column(nullable = false, length = 50)
    private FeeScope scope = FeeScope.ALL_SCHOOL;

    /** Set only when the scope is {@code CLASS_GROUP}. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scope_class_group_id")
    private ClassGroup scopeClassGroup;

    /** Set only when the scope is {@code CLASS}. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scope_class_id")
    private SchoolClass scopeClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_mode", nullable = false, length = 50)
    private FeePriceMode priceMode = FeePriceMode.SINGLE;

    /** The one price, with VAT included. Set only when the price mode is {@code SINGLE}. */
    @Column(name = "base_price", precision = 19, scale = 2)
    private BigDecimal basePrice;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "measurement_unit_id", nullable = false)
    private MeasurementUnit measurementUnit;

    @Column(name = "vat_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FeeNature nature = FeeNature.REGULAR;

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_cycle", nullable = false, length = 50)
    private FeeCollectionCycle collectionCycle = FeeCollectionCycle.MONTHLY;

    @Column(name = "discount_eligible", nullable = false)
    private boolean discountEligible;

    @Column(nullable = false)
    private boolean mandatory;

    @Column(name = "service_fee", nullable = false)
    private boolean serviceFee;

    @Column(nullable = false)
    private boolean refundable;

    @Column(name = "invoice_enabled", nullable = false)
    private boolean invoiceEnabled;

    @Column(name = "receipt_enabled", nullable = false)
    private boolean receiptEnabled;

    @Column(name = "internal_collection", nullable = false)
    private boolean internalCollection;

    /** The share of the amount invoiced, in percent. Meaningful only when invoicing is on. */
    @Column(name = "invoice_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal invoiceRate = new BigDecimal("100.00");

    /** Whether the khoản thu is still followed ("Theo dõi"); a stopped one is hidden by default. */
    @Column(nullable = false)
    private boolean active = true;

    // Eager and subselected: the response is built after the transaction has closed, and two
    // eager bags cannot be joined into one query, so each is loaded by a query of its own.
    @OneToMany(mappedBy = "feeItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id ASC")
    private List<FeeItemPrice> prices = new ArrayList<>();

    @OneToMany(mappedBy = "feeItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("sequence ASC")
    private List<FeeCollectionSchedule> collectionSchedules = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FeeItem() {
        // for JPA
    }

    public FeeItem(AcademicYear academicYear, String name, MeasurementUnit measurementUnit) {
        this.academicYear = academicYear;
        this.organization = academicYear.getOrganization();
        this.name = name;
        this.measurementUnit = measurementUnit;
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

    /** Replaces the whole price list; the rows that are no longer in it are deleted. */
    public void replacePrices(List<FeeItemPrice> next) {
        prices.clear();
        prices.addAll(next);
    }

    /**
     * Brings the collection dates to the given set: one that keeps its sequence is
     * moved in place, a new sequence is added, and one that is no longer wanted is
     * deleted. Not clear-and-add, which would insert a sequence before the old row
     * of the same sequence is deleted and trip the unique constraint.
     */
    public void syncCollectionSchedules(List<FeeCollectionSchedule> wanted) {
        for (FeeCollectionSchedule next : wanted) {
            collectionSchedules.stream()
                    .filter(existing -> existing.getSequence() == next.getSequence())
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> existing.moveTo(next.getCollectionMonth(), next.getAcademicPeriod()),
                            () -> collectionSchedules.add(next));
        }
        collectionSchedules.removeIf(existing ->
                wanted.stream().noneMatch(next -> next.getSequence() == existing.getSequence()));
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

    public FeeScope getScope() {
        return scope;
    }

    public void setScope(FeeScope scope) {
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

    public FeePriceMode getPriceMode() {
        return priceMode;
    }

    public void setPriceMode(FeePriceMode priceMode) {
        this.priceMode = priceMode;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public MeasurementUnit getMeasurementUnit() {
        return measurementUnit;
    }

    public void setMeasurementUnit(MeasurementUnit measurementUnit) {
        this.measurementUnit = measurementUnit;
    }

    public BigDecimal getVatRate() {
        return vatRate;
    }

    public void setVatRate(BigDecimal vatRate) {
        this.vatRate = vatRate;
    }

    public FeeNature getNature() {
        return nature;
    }

    public void setNature(FeeNature nature) {
        this.nature = nature;
    }

    public FeeCollectionCycle getCollectionCycle() {
        return collectionCycle;
    }

    public void setCollectionCycle(FeeCollectionCycle collectionCycle) {
        this.collectionCycle = collectionCycle;
    }

    public boolean isDiscountEligible() {
        return discountEligible;
    }

    public void setDiscountEligible(boolean discountEligible) {
        this.discountEligible = discountEligible;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(boolean serviceFee) {
        this.serviceFee = serviceFee;
    }

    public boolean isRefundable() {
        return refundable;
    }

    public void setRefundable(boolean refundable) {
        this.refundable = refundable;
    }

    public boolean isInvoiceEnabled() {
        return invoiceEnabled;
    }

    public void setInvoiceEnabled(boolean invoiceEnabled) {
        this.invoiceEnabled = invoiceEnabled;
    }

    public boolean isReceiptEnabled() {
        return receiptEnabled;
    }

    public void setReceiptEnabled(boolean receiptEnabled) {
        this.receiptEnabled = receiptEnabled;
    }

    public boolean isInternalCollection() {
        return internalCollection;
    }

    public void setInternalCollection(boolean internalCollection) {
        this.internalCollection = internalCollection;
    }

    public BigDecimal getInvoiceRate() {
        return invoiceRate;
    }

    public void setInvoiceRate(BigDecimal invoiceRate) {
        this.invoiceRate = invoiceRate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<FeeItemPrice> getPrices() {
        return prices;
    }

    public List<FeeCollectionSchedule> getCollectionSchedules() {
        return collectionSchedules;
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
        if (!(other instanceof FeeItem that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
