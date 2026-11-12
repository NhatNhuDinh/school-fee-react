package com.dth.frw.module.school.fee.registration.api.dto;

import com.dth.frw.domain.school.academic.AcademicYear;
import com.dth.frw.domain.school.academic.SchoolClass;
import com.dth.frw.domain.school.fee.FeeItem;
import com.dth.frw.domain.school.fee.FeePriceMode;
import com.dth.frw.domain.school.fee.StudentFeeRegistration;
import com.dth.frw.domain.school.student.StudentEnrollment;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code GET /fee_registrations/matrix}: everything the registration screen draws for
 * one lớp — the columns (the khoản thu it may register for), the rows (its students)
 * and which cells are ticked. One answer rather than a list of rows, because the
 * screen is a grid and not a table of records.
 *
 * <p>Registrations of a khoản thu that is no longer a column (stopped, or made
 * mandatory since) are left out: the screen cannot show them, and saving leaves them
 * alone.
 */
public record FeeRegistrationMatrixResponse(
        ClassSummary schoolClass,
        YearSummary academicYear,
        List<FeeItemColumn> feeItems,
        List<StudentRow> students,
        List<Registered> registrations) {

    public record ClassSummary(Long id, String name, Long classGroupId, String classGroupName) {
    }

    public record YearSummary(Long id, String name) {
    }

    /** A column: what the khoản thu is called and what it costs, for the header. */
    public record FeeItemColumn(
            Long id, String name, FeePriceMode priceMode, BigDecimal basePrice, String unit, BigDecimal vatRate) {
    }

    /** A row: the enrollment is what a registration is addressed to. */
    public record StudentRow(Long enrollmentId, String studentCode, String fullName) {
    }

    /** One ticked cell. */
    public record Registered(Long enrollmentId, Long feeItemId) {
    }

    public static FeeRegistrationMatrixResponse of(
            SchoolClass schoolClass,
            AcademicYear year,
            List<FeeItem> feeItems,
            List<StudentEnrollment> students,
            List<StudentFeeRegistration> registrations) {
        return new FeeRegistrationMatrixResponse(
                new ClassSummary(
                        schoolClass.getId(),
                        schoolClass.getName(),
                        schoolClass.getClassGroup().getId(),
                        schoolClass.getClassGroup().getName()),
                new YearSummary(year.getId(), year.getName()),
                feeItems.stream()
                        .map(item -> new FeeItemColumn(
                                item.getId(),
                                item.getName(),
                                item.getPriceMode(),
                                item.getBasePrice(),
                                item.getMeasurementUnit().getName(),
                                item.getVatRate()))
                        .toList(),
                students.stream()
                        .map(row -> new StudentRow(
                                row.getId(), row.getStudent().getStudentCode(), row.getStudent().getFullName()))
                        .toList(),
                registrations.stream()
                        .map(row -> new Registered(row.getEnrollment().getId(), row.getFeeItem().getId()))
                        .toList());
    }
}
