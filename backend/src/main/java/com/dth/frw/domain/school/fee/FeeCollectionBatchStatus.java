package com.dth.frw.domain.school.fee;

/**
 * Where a đợt thu stands. Never stored and never set by a person: it is read off the
 * money, so it cannot disagree with it.
 */
public enum FeeCollectionBatchStatus {
    NOT_STARTED,
    COLLECTING,
    COMPLETED
}
