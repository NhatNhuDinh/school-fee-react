package com.dth.frw.module.security.rowlevel.api.dto;

import jakarta.validation.constraints.NotNull;

/** "Would this account be allowed to do this to that row?" */
public record SimulateRequest(
        @NotNull(message = "Vui lòng chọn đối tượng") String resource,
        @NotNull(message = "Vui lòng chọn hành động") String action,
        @NotNull(message = "Vui lòng chọn người dùng") Long userId,
        @NotNull(message = "Vui lòng chọn bản ghi") Long recordId) {
}
