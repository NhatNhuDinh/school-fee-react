package com.dth.frw.domain.school.fee;

/** What happened to one student's e-mail. */
public enum FeeNotificationRecipientStatus {
    /** Waiting for its turn. */
    PENDING,
    /** The guardian has no e-mail address on record, so nothing can be sent. */
    MISSING_EMAIL,
    SENT,
    FAILED
}
