package com.dth.frw.module.system.notification.api.dto;

import com.dth.frw.domain.system.notification.NotificationLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Raising a notification for somebody, over HTTP.
 *
 * <p>Not how the application normally raises one — features call
 * {@code NotificationService.notify} directly, in the same transaction as the
 * work that warranted it. This exists so that the mechanism can be exercised
 * from the admin UI and from a test without inventing a feature first, which is
 * worth having in a framework whose whole job is to be built on.
 *
 * @param recipientId who it is for. Required: there is no such thing as a
 *                    notification for nobody, and a broadcast is that many rows
 * @param level       null means {@code INFO}
 */
public record NotificationRequest(
        @NotNull(message = "Vui lòng chọn người nhận")
        Long recipientId,

        NotificationLevel level,

        @NotBlank(message = "Vui lòng nhập tiêu đề")
        @Size(max = 255, message = "Tiêu đề tối đa 255 ký tự")
        String title,

        @Size(max = 2000, message = "Nội dung tối đa 2000 ký tự")
        String body,

        @Size(max = 500, message = "Liên kết tối đa 500 ký tự")
        String link) {
}
