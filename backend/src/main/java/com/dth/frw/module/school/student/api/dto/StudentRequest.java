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
 * {@code POST /students} payload: a student and their enrollment in a year, in
 * one. {@code studentCode} may be left out, in which case one is made up from
 * the school, the year and the class. The year decides the school.
 */
public record StudentRequest(
        @NotNull(message = "Vui lòng chọn năm học") @Valid Ref academicYear,
        @NotNull(message = "Vui lòng chọn lớp") @Valid Ref schoolClass,
        @Valid Ref trainingSystem,
        @NotBlank(message = "Vui lòng nhập họ và tên") @Size(max = 255, message = "Tối đa 255 ký tự") String fullName,
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

    /** Another record, by id. Only the id is read. */
    public record Ref(@NotNull(message = "Thiếu mã") Long id) {
    }
}
