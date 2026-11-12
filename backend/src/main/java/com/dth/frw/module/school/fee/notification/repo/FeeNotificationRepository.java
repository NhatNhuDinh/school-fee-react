package com.dth.frw.module.school.fee.notification.repo;

import com.dth.frw.domain.school.fee.FeeNotification;
import com.dth.frw.shared.data.SecuredRepository;

/**
 * A {@link SecuredRepository}: every read has to be handed a scope, so the school boundary
 * cannot be skipped by calling an inherited method.
 */
public interface FeeNotificationRepository extends SecuredRepository<FeeNotification, Long> {
}
