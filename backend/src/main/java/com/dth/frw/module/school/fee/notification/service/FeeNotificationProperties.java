package com.dth.frw.module.school.fee.notification.service;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the sending behaves, bound from {@code school-fee.notification.*}.
 *
 * @param dispatchBatchSize recipients sent between two refreshes of the counters
 * @param timeZone          the school's clock: "today" for the deadline and the time a
 *                          notification is scheduled for
 * @param pollIntervalMs    how soon a notification that is due is picked up
 * @param dispatchEnabled   off in tests and on a node that must not send
 * @param senderUnit        the line under the school's name in the e-mail
 * @param from              the sender address; the SMTP account when blank
 */
@ConfigurationProperties(prefix = "school-fee.notification")
public record FeeNotificationProperties(
        int dispatchBatchSize,
        String timeZone,
        long pollIntervalMs,
        boolean dispatchEnabled,
        String senderUnit,
        String from) {

    public FeeNotificationProperties {
        dispatchBatchSize = dispatchBatchSize <= 0 ? 100 : dispatchBatchSize;
        timeZone = timeZone == null || timeZone.isBlank() ? "Asia/Ho_Chi_Minh" : timeZone;
        pollIntervalMs = pollIntervalMs <= 0 ? 5_000 : pollIntervalMs;
        senderUnit = senderUnit == null ? "" : senderUnit;
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
