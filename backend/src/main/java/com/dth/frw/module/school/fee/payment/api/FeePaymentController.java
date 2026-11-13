package com.dth.frw.module.school.fee.payment.api;

import com.dth.frw.module.school.fee.payment.service.FeePaymentService;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.ClassRow;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Collected;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.History;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Receipt;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentDebts;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentHit;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.TransferInfo;
import com.dth.frw.shared.security.Permissions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/fee_payments} — thu tiền at the counter. Not the data provider's dialect: the
 * screens read a student's debts, a đợt's classes and the receipts, and write a collection,
 * so each is its own endpoint. Money is always taken whole; there is no amount in any request.
 */
@RestController
@RequestMapping("/api/fee_payments")
public class FeePaymentController {

    private final FeePaymentService service;

    public FeePaymentController(FeePaymentService service) {
        this.service = service;
    }

    public record CollectStudent(@NotNull Long academicYearId, @NotNull Long enrollmentId, @NotNull String method,
                                 LocalDate paidOn, @NotEmpty List<Long> receivableIds, String transferContent) {
    }

    public record CollectClasses(@NotNull Long academicYearId, @NotNull Long batchId,
                                 @NotEmpty List<Long> classIds, LocalDate paidOn) {
    }

    public record Cancel(@NotNull Long academicYearId, @NotEmpty List<Long> ids) {
    }

    @GetMapping("/students")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public List<StudentHit> students(@RequestParam Long academicYearId, @RequestParam(required = false) String q) {
        return service.searchStudents(academicYearId, q);
    }

    @GetMapping("/students/{enrollmentId}/debts")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public StudentDebts debts(@PathVariable Long enrollmentId, @RequestParam Long academicYearId) {
        return service.debts(academicYearId, enrollmentId);
    }

    @GetMapping("/transfer-info")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public TransferInfo transferInfo(@RequestParam Long academicYearId, @RequestParam BigDecimal amount) {
        return service.transferInfo(academicYearId, amount);
    }

    @PostMapping("/collect-student")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public Collected collectStudent(@Valid @RequestBody CollectStudent request) {
        return service.collectStudent(request.academicYearId(), request.enrollmentId(), request.method(),
                request.paidOn(), request.receivableIds(), request.transferContent());
    }

    @GetMapping("/classes")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public List<ClassRow> classes(@RequestParam Long academicYearId, @RequestParam Long batchId) {
        return service.classes(academicYearId, batchId);
    }

    @PostMapping("/collect-classes")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public Collected collectClasses(@Valid @RequestBody CollectClasses request) {
        return service.collectClasses(request.academicYearId(), request.batchId(), request.classIds(),
                request.paidOn());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public History history(@RequestParam Long academicYearId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) Long batchId,
                           @RequestParam(required = false) String q) {
        return service.history(academicYearId, from, to, batchId, q);
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public List<Receipt> receipts(@RequestParam Long academicYearId, @RequestParam List<Long> ids) {
        return service.receipts(academicYearId, ids);
    }

    @PostMapping("/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_DELETE + "')")
    public Map<String, Object> cancel(@Valid @RequestBody Cancel request) {
        return Map.of("cancelled", service.cancel(request.academicYearId(), request.ids()));
    }
}
