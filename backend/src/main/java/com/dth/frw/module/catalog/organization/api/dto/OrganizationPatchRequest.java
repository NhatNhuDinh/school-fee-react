package com.dth.frw.module.catalog.organization.api.dto;

import com.dth.frw.domain.catalog.organization.OrganizationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /organizations/{id}} payload.
 *
 * <p>A {@code null} field means "leave it unchanged". The admin UI happens to
 * always send the whole form, but keeping the patch semantics honest means the
 * endpoint is also usable from anything that sends a real partial update.
 *
 * <p>The optional text fields are the exception to that rule in one direction:
 * an empty string clears them, because that is the only way a form with a
 * cleared input can say so.
 */
public record OrganizationPatchRequest(
        @Size(min = 1, max = 64, message = "Mã phải từ 1 đến 64 ký tự")
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Mã chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang")
        String code,

        @Size(min = 1, max = 255, message = "Tên phải từ 1 đến 255 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @Size(max = 64, message = "Số điện thoại tối đa 64 ký tự")
        String phone,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        /** Ảnh logo dạng data URL (png, jpeg, webp hoặc svg). Chuỗi rỗng để xóa logo. */
        @Size(max = 400_000, message = "Logo quá lớn, hãy chọn ảnh nhỏ hơn")
        @Pattern(
                regexp = "^$|^data:image/(png|jpeg|webp|svg[+]xml);base64,[A-Za-z0-9+/=]+$",
                message = "Logo phải là ảnh png, jpeg, webp hoặc svg")
        String logo,

        OrganizationStatus status) {
}
