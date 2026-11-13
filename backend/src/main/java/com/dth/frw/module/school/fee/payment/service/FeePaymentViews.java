package com.dth.frw.module.school.fee.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the thu tiền screens read and what a collection answers with. */
public final class FeePaymentViews {

    private FeePaymentViews() {
    }

    /** A student found by the search box, with what they owe across the year's đợt thu. */
    public record StudentHit(long enrollmentId, String studentCode, String fullName, LocalDate dateOfBirth, String className,
                             String classGroupName, BigDecimal outstanding) {
    }

    /** One khoản thu a student owes in a đợt. */
    public record DebtLine(long receivableId, String itemName, String unitName, int quantity,
                           BigDecimal receivable, BigDecimal collected, BigDecimal outstanding) {
    }

    /** What a student owes in one đợt thu. */
    public record BatchDebt(long batchId, String batchName, String scopeLabel, long batchStudentId,
                            BigDecimal receivable, BigDecimal collected, BigDecimal outstanding,
                            List<DebtLine> lines) {
    }

    public record StudentDebts(long enrollmentId, String studentCode, String fullName, String className,
                               String classGroupName, List<BatchDebt> batches) {
    }

    /** What the counter shows for a transfer: the bank account and the VietQR image that carries the amount. */
    public record TransferInfo(boolean configured, String bank, String accountNumber, String accountHolder,
                               String content, BigDecimal amount, String qrUrl) {
    }

    public record ClassRow(long classId, String className, String classGroupName, long studentCount,
                           long owingStudentCount, BigDecimal receivable, BigDecimal collected,
                           BigDecimal outstanding) {
    }

    public record Receipt(long id, long receiptNo, String receiptCode, long batchId, String batchName,
                          String studentCode, String studentName, String className, String method,
                          BigDecimal amount, LocalDate paidOn, String status, String collectorName,
                          String transferContent, String cancelledByName, List<ReceiptLine> lines) {
    }

    public record ReceiptLine(String itemName, String unitName, int quantity, BigDecimal amount) {
    }

    public record Collected(List<Receipt> receipts, BigDecimal total, int studentCount) {
    }

    public record History(List<Receipt> rows, long total, BigDecimal amount) {
    }
}
