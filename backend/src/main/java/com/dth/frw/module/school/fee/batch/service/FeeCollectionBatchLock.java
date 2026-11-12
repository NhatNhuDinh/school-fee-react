package com.dth.frw.module.school.fee.batch.service;

/**
 * Something that has promised a đợt thu's figures to somebody, and so keeps them still.
 *
 * <p>The notification feature implements it. It is an interface in the batch slice, reached
 * through an {@code ObjectProvider}, because the notification service needs the batch service
 * to read a đợt and the batch service needs to ask whether anything has been sent: injecting
 * it directly would be a cycle Spring refuses to construct.
 */
public interface FeeCollectionBatchLock {

    /** A notification that has been scheduled or sent exists: the khoản thu may no longer change. */
    boolean locksChanges(Long batchId);

    /** Any notification at all, drafts included: the đợt may not be deleted from under it. */
    boolean hasNotifications(Long batchId);
}
