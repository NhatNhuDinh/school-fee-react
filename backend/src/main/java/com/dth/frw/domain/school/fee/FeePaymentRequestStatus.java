package com.dth.frw.domain.school.fee;

/** A payment request is only payable once the notification carrying it has been scheduled. */
public enum FeePaymentRequestStatus {
    DRAFT,
    PENDING,
    PAID,
    CANCELLED
}
