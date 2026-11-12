package com.dth.frw.domain.school.fee;

/**
 * Where a đợt thông báo is. It only moves forward, except that a retry puts a finished
 * one back to {@link #SCHEDULED}:
 *
 * <pre>
 * DRAFT → SCHEDULED → SENDING → COMPLETED | PARTIAL_FAILURE | FAILED
 * </pre>
 *
 * A draft is only recipients and text, nothing has been promised to anybody; from
 * {@code SCHEDULED} on the students in it count as notified, and the đợt thu it belongs to
 * can no longer change what it asks them to pay.
 */
public enum FeeNotificationStatus {
    DRAFT,
    SCHEDULED,
    SENDING,
    /** Every recipient with an address was sent to. */
    COMPLETED,
    /** Finished, but some recipients failed or have no address. */
    PARTIAL_FAILURE,
    /** Finished and nothing was delivered. */
    FAILED;

    /** The notification has begun to promise something: the đợt thu is locked from here. */
    public boolean locksBatch() {
        return this != DRAFT;
    }

    public boolean canBeCancelled() {
        return this == DRAFT || this == SCHEDULED;
    }

    public boolean canRetry() {
        return this == COMPLETED || this == PARTIAL_FAILURE || this == FAILED;
    }
}
