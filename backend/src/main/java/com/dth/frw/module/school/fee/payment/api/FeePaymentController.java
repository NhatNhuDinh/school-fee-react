package com.dth.frw.module.school.fee.payment.api;

import com.dth.frw.module.school.fee.payment.sepay.BankTransactionService;
import com.dth.frw.module.school.fee.payment.sepay.BankTransactionService.CheckSummary;
import com.dth.frw.module.school.fee.payment.sepay.BankTransactionService.Row;
import com.dth.frw.module.school.fee.payment.service.FeePaymentService;
import com.dth.frw.module.school.fee.payment.service.FeeTransferRequestService;
import com.dth.frw.module.school.fee.payment.service.FeeTransferRequestService.View;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.ClassRow;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Collected;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.History;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.Receipt;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentDebts;
import com.dth.frw.module.school.fee.payment.service.FeePaymentViews.StudentHit;
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
    private final FeeTransferRequestService transferRequests;
    private final BankTransactionService bank;

    public FeePaymentController(FeePaymentService service, FeeTransferRequestService transferRequests,
                                BankTransactionService bank) {
        this.service = service;
        this.transferRequests = transferRequests;
        this.bank = bank;
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

    // ---- transfer requests (the QR at the counter)

    public record CreateTransfer(@NotNull Long academicYearId, @NotNull Long enrollmentId,
                                 @NotEmpty List<Long> receivableIds) {
    }

    public record YearOnly(@NotNull Long academicYearId) {
    }

    public record Assign(@NotNull Long academicYearId, @NotNull Long enrollmentId, @NotEmpty List<Long> receivableIds) {
    }

    public record Ignore(@NotNull Long academicYearId, String note) {
    }

    public record Check(@NotNull Long academicYearId, Integer days) {
    }

    public record Checked(CheckSummary summary, View request) {
    }

    @PostMapping("/transfer-requests")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public View createTransfer(@Valid @RequestBody CreateTransfer request) {
        return transferRequests.create(request.academicYearId(), request.enrollmentId(), request.receivableIds());
    }

    @GetMapping("/transfer-requests")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public List<View> waitingTransfers(@RequestParam Long academicYearId) {
        return transferRequests.waiting(academicYearId);
    }

    @GetMapping("/transfer-requests/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public View transfer(@PathVariable Long id, @RequestParam Long academicYearId) {
        return transferRequests.get(academicYearId, id);
    }

    @PostMapping("/transfer-requests/{id}/defer")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public View deferTransfer(@PathVariable Long id, @Valid @RequestBody YearOnly request) {
        return transferRequests.defer(request.academicYearId(), id);
    }

    @PostMapping("/transfer-requests/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public View cancelTransfer(@PathVariable Long id, @Valid @RequestBody YearOnly request) {
        return transferRequests.cancel(request.academicYearId(), id);
    }

    /** "Đã nhận tiền": the cashier saw the money; settles exactly as a matching bank line would. */
    @PostMapping("/transfer-requests/{id}/confirm")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public View confirmTransfer(@PathVariable Long id, @Valid @RequestBody YearOnly request) {
        return transferRequests.confirmManually(request.academicYearId(), id);
    }

    /** "Kiểm tra giao dịch" on one request: look at the bank, then report where the request stands. */
    @PostMapping("/transfer-requests/{id}/check")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public Checked checkTransfer(@PathVariable Long id, @Valid @RequestBody YearOnly request) {
        CheckSummary summary = bank.check(request.academicYearId(), null);
        return new Checked(summary, transferRequests.get(request.academicYearId(), id));
    }

    // ---- bank lines

    @GetMapping("/bank-transactions")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public List<Row> bankTransactions(@RequestParam Long academicYearId,
                                      @RequestParam(defaultValue = "review") String tab,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String q) {
        return bank.list(academicYearId, tab, from, to, q);
    }

    /** What the toolbar badge and the tabs count. */
    @GetMapping("/bank-transactions/summary")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_READ + "')")
    public Map<String, Object> summary(@RequestParam Long academicYearId) {
        return Map.of("review", bank.reviewCount(academicYearId), "waiting", transferRequests.waitingCount(academicYearId));
    }

    @PostMapping("/bank-transactions/check")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public CheckSummary checkBank(@Valid @RequestBody Check request) {
        return bank.check(request.academicYearId(), request.days());
    }

    @PostMapping("/bank-transactions/{id}/ignore")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public Map<String, Object> ignore(@PathVariable Long id, @Valid @RequestBody Ignore request) {
        bank.ignore(request.academicYearId(), id, request.note());
        return Map.of("ok", true);
    }

    @PostMapping("/bank-transactions/{id}/assign")
    @PreAuthorize("hasAuthority('" + Permissions.FEE_PAYMENTS_WRITE + "')")
    public Map<String, Object> assign(@PathVariable Long id, @Valid @RequestBody Assign request) {
        bank.assign(request.academicYearId(), id, request.enrollmentId(), request.receivableIds());
        return Map.of("ok", true);
    }
}
