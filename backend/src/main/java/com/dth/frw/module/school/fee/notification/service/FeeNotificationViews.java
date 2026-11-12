package com.dth.frw.module.school.fee.notification.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** The shapes the notification service returns; the controller turns them into JSON. */
public final class FeeNotificationViews {

    private FeeNotificationViews() {
    }

    /** What a student has been told so far, read off the recipient rows of non-draft notifications. */
    public enum NotifyStatus {
        /** Nothing has been sent or scheduled. */
        NONE,
        /** Waiting in a scheduled or running notification. */
        QUEUED,
        SENT,
        FAILED,
        MISSING_EMAIL
    }

    /** How much of what a student owes has come in. */
    public enum CollectStatus {
        /** Owes nothing in this đợt. */
        NO_DEBT,
        UNPAID,
        PARTIAL,
        PAID
    }

    /** Which students a notification is being picked for. */
    public enum Mode {
        /** Everybody in the đợt. */
        ALL,
        /** Not told yet, or told and it failed. */
        UNNOTIFIED,
        /** Still owes something. */
        DEBT
    }

    /** One student a notification could go to. */
    public record Candidate(
            long batchStudentId,
            String studentName,
            String studentCode,
            long classGroupId,
            String classGroupName,
            long schoolClassId,
            String className,
            BigDecimal receivableAmount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount,
            CollectStatus collectStatus,
            NotifyStatus notifyStatus,
            boolean hasEmail) {
    }

    /** A student the filters reach, with what is still owed, so a selection can add itself up. */
    public record IdAmount(long id, BigDecimal outstanding) {
    }

    /** A page of candidates plus what the whole filtered set comes to. */
    public record CandidatePage(List<Candidate> rows, long total, BigDecimal outstandingTotal, BigDecimal receivableTotal) {
    }

    /** One lớp in the tree, with how many students it has under the current mode. */
    public record TreeClass(long id, String name, int count) {
    }

    /** One khối in the tree. */
    public record TreeGroup(long id, String name, int count, List<TreeClass> classes) {
    }

    public record Tree(int total, List<TreeGroup> groups) {
    }

    /** A line of the khoản thu a student owes, frozen when the notification is made. */
    public record FeeLine(String name, BigDecimal amount) {
    }

    /** One recipient of a notification. */
    public record Recipient(
            long id,
            long batchStudentId,
            String studentName,
            String studentCode,
            String classGroupName,
            String className,
            String guardianName,
            String email,
            BigDecimal totalAmount,
            String status,
            int attempts,
            Instant sentAt,
            String errorMessage,
            String orderCode) {
    }

    public record RecipientPage(List<Recipient> rows, long total, Map<String, Long> countsByStatus) {
    }

    /** What making a notification came to: the row, and why some of those picked were left out. */
    public record Created(
            com.dth.frw.domain.school.fee.FeeNotification notification,
            int requested,
            int skippedAlreadyNotified,
            int skippedNoDebt) {
    }

    /** The e-mail a student would get, rendered. */
    public record Rendered(String subject, String html, String studentName, String className, String email) {
    }

    public record Requeued(int requeued, int stillMissingEmail) {
    }

    /** The pieces of an e-mail that depend on who it is for. */
    public record MailContext(
            String schoolName,
            String senderUnit,
            String subject,
            String content,
            LocalDate deadline,
            String studentName,
            String studentCode,
            String className,
            List<FeeLine> lines,
            BigDecimal total,
            String orderCode,
            String bankAccountNumber,
            String bankCode,
            String bankAccountHolder,
            String vietQrBaseUrl,
            String vietQrTemplate,
            String vietQrShowInfo) {
    }
}
