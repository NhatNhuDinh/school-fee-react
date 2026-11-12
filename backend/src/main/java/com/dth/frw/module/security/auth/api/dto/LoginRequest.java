package com.dth.frw.module.security.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /auth/login} payload.
 *
 * <p>The field is called {@code username} but accepts an email address too, so
 * that nobody has to remember which of the two this system decided to key on.
 * No length or format rules: a credential is either right or wrong, and telling
 * someone their guess was too short only helps them guess better.
 */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập hoặc email")
        String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
        String password) {
}
