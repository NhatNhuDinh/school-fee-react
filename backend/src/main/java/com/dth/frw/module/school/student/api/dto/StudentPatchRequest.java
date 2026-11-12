package com.dth.frw.module.school.student.api.dto;

import com.dth.frw.domain.school.student.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code PATCH /students/{id}} payload — a {@code null} field means "leave it
 * unchanged", and a blank optional string clears it. The year is not patchable.
 *
 * <p>{@code schoolClass} and {@code trainingSystem} carry a nullable id so the
 * training system can be cleared: absent leaves it, {@code {"id": null}} clears
 * it, {@code {"id": n}} sets it. A class can be changed but not cleared.
 */
public record StudentPatchRequest(
        Ref schoolClass,
        Ref trainingSystem,
        @Pattern(regexp = ".*\\S.*", message = "Vui lòng nhập họ và tên") @Size(max = 255, message = "Tối đa 255 ký tự") String fullName,
        @Size(max = 100, message = "Tối đa 100 ký tự") String studentCode,
        LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 50, message = "Tối đa 50 ký tự") String personalId,
        @Size(max = 50, message = "Tối đa 50 ký tự") String passportNumber,
        @Size(max = 100, message = "Tối đa 100 ký tự") String statisticCode,
        @Size(max = 50, message = "Tối đa 50 ký tự") String healthInsuranceCode,
        @Min(value = 1900, message = "Năm nhập học không hợp lệ") @Max(value = 2200, message = "Năm nhập học không hợp lệ") Integer admissionYear,
        @Size(max = 255, message = "Tối đa 255 ký tự") String province,
        @Size(max = 255, message = "Tối đa 255 ký tự") String district,
        @Size(max = 255, message = "Tối đa 255 ký tự") String ward,
        @Size(max = 255, message = "Tối đa 255 ký tự") String area,
        @Size(max = 100, message = "Tối đa 100 ký tự") String houseNumber,
        @Size(max = 255, message = "Tối đa 255 ký tự") String hamlet,
        @Size(max = 1000, message = "Tối đa 1000 ký tự") String address,
        @Email(message = "Email không hợp lệ") @Size(max = 255, message = "Tối đa 255 ký tự") String guardianEmail,
        @Size(max = 255, message = "Tối đa 255 ký tự") String guardianName,
        @Size(max = 50, message = "Tối đa 50 ký tự") String guardianPhone,
        @Size(max = 255, message = "Tối đa 255 ký tự") String bankName,
        @Size(max = 255, message = "Tối đa 255 ký tự") String bankAccountHolder,
        @Size(max = 100, message = "Tối đa 100 ký tự") String bankAccountNumber,
        Boolean needsInvoice,
        LocalDate transferInDate,
        @Size(max = 1000, message = "Tối đa 1000 ký tự") String note) {

    /** Another record, by id; the id may be null to clear. */
    public record Ref(Long id) {
    }
}
