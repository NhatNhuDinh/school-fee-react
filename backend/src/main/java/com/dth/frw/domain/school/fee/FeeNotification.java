package com.dth.frw.domain.school.fee;

import com.dth.frw.domain.catalog.organization.Organization;
import com.dth.frw.domain.school.TenantOwned;
import com.dth.frw.domain.school.academic.AcademicYear;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Đợt thông báo: one e-mail to each of a group of students of a đợt thu, with the same
 * words and the same deadline, sent in the background.
 *
 * <p>Owned by a school and a year like every school row. Its recipients and the payment
 * requests they carry are separate rows that no endpoint reaches except through a
 * notification the caller was allowed to read, so — like the đợt's own students — they
 * have no resource name and no policy of their own, and no entity: they are read and
 * written in SQL.
 *
 * <p>The counters are never edited in the browser or patched here: the dispatcher rewrites
 * them in SQL from the recipient rows after every chunk it sends.
 */
@RowLevelResource("fee_notifications")
@Entity
@Table(name = "fee_notifications")
public class FeeNotification implements TenantOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private FeeCollectionBatch batch;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false)
    private LocalDate deadline;

    @Column(name = "send_at")
    private Instant sendAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FeeNotificationStatus status = FeeNotificationStatus.DRAFT;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "sent_count", nullable = false)
    private int sentCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "missing_email_count", nullable = false)
    private int missingEmailCount;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected FeeNotification() {
        // for JPA
    }

    public FeeNotification(FeeCollectionBatch batch, String subject, String content, LocalDate deadline,
                           String createdByName) {
        this.batch = batch;
        this.academicYear = batch.getAcademicYear();
        this.organization = batch.getOrganization();
        this.subject = subject;
        this.content = content;
        this.deadline = deadline;
        this.createdByName = createdByName;
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

    public FeeCollectionBatch getBatch() {
        return batch;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Instant getSendAt() {
        return sendAt;
    }

    public void setSendAt(Instant sendAt) {
        this.sendAt = sendAt;
    }

    public FeeNotificationStatus getStatus() {
        return status;
    }

    public void setStatus(FeeNotificationStatus status) {
        this.status = status;
    }

    public int getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(int recipientCount) {
        this.recipientCount = recipientCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getMissingEmailCount() {
        return missingEmailCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCreatedByName() {
        return createdByName;
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
        if (!(other instanceof FeeNotification that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
