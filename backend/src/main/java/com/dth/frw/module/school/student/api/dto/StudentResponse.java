package com.dth.frw.module.school.student.api.dto;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.ClassGroup;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.catalog.TrainingSystem;
import com.dth.frw.domain.school.student.Gender;
import com.dth.frw.domain.school.student.Student;
import com.dth.frw.domain.school.student.StudentEnrollment;
import com.dth.frw.module.security.rowlevel.service.RowLevelSecurity;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Wire shape of a student in a year: the enrollment, with the student's own
 * fields under {@code student}. {@code id} is the enrollment's — it is what the
 * list, the edit page and the delete address. The nested summaries are declared
 * here rather than imported, so widening another API cannot widen this one.
 */
public record StudentResponse(
        Long id,
        StudentSummary student,
        AcademicYearSummary academicYear,
        ClassSummary schoolClass,
        NameSummary trainingSystem,
        LocalDate transferInDate,
        String note,
        Instant createdAt,
        Instant updatedAt,
        /** What this caller may do to this row, as {@code {"write": true, "delete": false}}. */
        @JsonProperty("_can") Map<String, Boolean> can) {

    public record StudentSummary(
            Long id,
            String studentCode,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String personalId,
            String passportNumber,
            String statisticCode,
            Integer admissionYear,
            String healthInsuranceCode,
            String province,
            String district,
            String ward,
            String area,
            String houseNumber,
            String hamlet,
            String address,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String bankName,
            String bankAccountHolder,
            String bankAccountNumber,
            boolean needsInvoice) {

        static StudentSummary from(Student s) {
            return new StudentSummary(
                    s.getId(), s.getStudentCode(), s.getFullName(), s.getDateOfBirth(), s.getGender(),
                    s.getPersonalId(), s.getPassportNumber(), s.getStatisticCode(), s.getAdmissionYear(),
                    s.getHealthInsuranceCode(), s.getProvince(), s.getDistrict(), s.getWard(), s.getArea(),
                    s.getHouseNumber(), s.getHamlet(), s.getAddress(), s.getGuardianName(), s.getGuardianPhone(),
                    s.getGuardianEmail(), s.getBankName(), s.getBankAccountHolder(), s.getBankAccountNumber(),
                    s.isNeedsInvoice());
        }
    }

    public record AcademicYearSummary(Long id, String name) {
        static AcademicYearSummary from(AcademicYear year) {
            return new AcademicYearSummary(year.getId(), year.getName());
        }
    }

    public record NameSummary(Long id, String name) {
        static NameSummary from(TrainingSystem system) {
            return system == null ? null : new NameSummary(system.getId(), system.getName());
        }
    }

    public record ClassSummary(Long id, String name, NameSummary classGroup) {
        static ClassSummary from(SchoolClass schoolClass) {
            if (schoolClass == null) {
                return null;
            }
            ClassGroup group = schoolClass.getClassGroup();
            return new ClassSummary(
                    schoolClass.getId(), schoolClass.getName(), new NameSummary(group.getId(), group.getName()));
        }
    }

    /** Without row flags — for the echo of a deleted row. */
    public static StudentResponse from(StudentEnrollment row) {
        return from(row, null);
    }

    public static StudentResponse from(StudentEnrollment row, RowLevelSecurity rowLevel) {
        return new StudentResponse(
                row.getId(),
                StudentSummary.from(row.getStudent()),
                AcademicYearSummary.from(row.getAcademicYear()),
                ClassSummary.from(row.getSchoolClass()),
                NameSummary.from(row.getTrainingSystem()),
                row.getTransferInDate(),
                row.getNote(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                rowLevel == null ? Map.of() : rowLevel.capabilities(row));
    }
}
