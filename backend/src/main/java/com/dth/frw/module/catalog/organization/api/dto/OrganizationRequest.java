package com.dth.frw.module.catalog.organization.api.dto;

import com.dth.frw.domain.catalog.organization.OrganizationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /organizations} payload.
 *
 * <p>{@code status} is optional — an organization created without one starts
 * active, which is what the form's default already sends.
 */
public record OrganizationRequest(
        @NotBlank(message = "Vui lòng nhập mã")
        @Size(max = 64, message = "Mã tối đa 64 ký tự")
        // The same shape a role code has: something a person can type into a
        // filter or quote in a spreadsheet without quoting rules getting
        // involved.
        @Pattern(
                regexp = "^[A-Za-z0-9._-]+$",
                message = "Mã chỉ gồm chữ cái, chữ số, dấu chấm, gạch dưới và gạch ngang")
        String code,

        @NotBlank(message = "Vui lòng nhập tên")
        @Size(max = 255, message = "Tên tối đa 255 ký tự")
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
