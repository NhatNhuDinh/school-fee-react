package com.dth.frw.module.school.fee.notification.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on what the notification feature needs: its two property sets and the scheduler
 * that {@link FeeNotificationDispatcher} polls with. Nothing else in the application is
 * scheduled yet, so this is the only {@code @EnableScheduling}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({FeeNotificationProperties.class, FeePaymentProperties.class})
public class FeeNotificationConfig {
}
