package com.dth.frw.module.school.fee.payment.sepay;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;
import tools.jackson.databind.JsonNode;

/**
 * One bank line as SePay reports it, whichever way it reached us: the webhook or the list-transactions
 * API. The two spell their fields differently and identify a line differently (the webhook by an
 * integer, the API by a UUID), so this is the one shape the rest of the code reads.
 *
 * @param transactionDate the bank's own time, Vietnam local time, as SePay prints it
 */
public record SepayPayload(Long id, String uuid, String gateway, String accountNumber,
                           LocalDateTime transactionDate, BigDecimal amount, String transferType,
                           String content, String code, String referenceCode, String raw) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** A webhook body. Returns null when a field the matching depends on is missing or malformed. */
    public static SepayPayload fromWebhook(JsonNode node, String raw) {
        if (node == null || !node.isObject()) {
            return null;
        }
        LocalDateTime time = parseTime(text(node, "transactionDate"));
        BigDecimal amount = amount(node.get("transferAmount"));
        String type = text(node, "transferType");
        if (time == null || amount == null || type == null) {
            return null;
        }
        Long id = node.hasNonNull("id") && node.get("id").canConvertToLong() ? node.get("id").asLong() : null;
        return new SepayPayload(id, null, text(node, "gateway"), text(node, "accountNumber"), time, amount,
                type.toLowerCase(Locale.ROOT), text(node, "content"), text(node, "code"),
                text(node, "referenceCode"), raw);
    }

    /** One element of {@code data} in the v2 list-transactions response. Null when it cannot be read. */
    public static SepayPayload fromApi(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        LocalDateTime time = parseTime(text(node, "transaction_date"));
        String type = text(node, "transfer_type");
        BigDecimal in = amount(node.get("amount_in"));
        BigDecimal out = amount(node.get("amount_out"));
        if (time == null || type == null) {
            return null;
        }
        BigDecimal amount = "in".equalsIgnoreCase(type) ? in : out;
        if (amount == null) {
            return null;
        }
        return new SepayPayload(null, text(node, "id"), text(node, "bank_brand_name"), text(node, "account_number"),
                time, amount, type.toLowerCase(Locale.ROOT), text(node, "transaction_content"), text(node, "code"),
                text(node, "reference_number"), node.toString());
    }

    /**
     * What identifies this line no matter which way it arrived, so the webhook and a later manual check
     * of the same line collapse into one row. The reference code is left out on purpose: the two sources
     * do not promise to spell it the same way, while the account, the second, the amount and the memo
     * are the same facts in both.
     */
    public String dedupeKey() {
        String material = digits(accountNumber) + "|" + transactionDate.format(TIME) + "|"
                + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString() + "|" + alnum(content);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Upper-case letters and digits only: how a bank's memo is compared, whatever punctuation it dropped. */
    public static String alnum(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static BigDecimal amount(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            BigDecimal amount = value.isNumber() ? value.decimalValue() : new BigDecimal(value.asString().trim());
            return amount.signum() < 0 ? null : amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
