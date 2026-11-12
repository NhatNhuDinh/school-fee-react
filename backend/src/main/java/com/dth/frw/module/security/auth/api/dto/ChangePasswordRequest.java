package com.dth.frw.module.security.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/change-password} payload: the signed-in user changing their
 * own password.
 *
 * <p>The current password is required even though the request is already
 * authenticated. A valid session proves the browser is signed in, not that the
 * person at the keyboard is the account owner, and locking the owner out is
 * exactly what an attacker on an unattended machine would do first.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
        String currentPassword,

        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 8, max = 100, message = "Mật khẩu mới phải từ 8 đến 100 ký tự")
        String newPassword) {
}
