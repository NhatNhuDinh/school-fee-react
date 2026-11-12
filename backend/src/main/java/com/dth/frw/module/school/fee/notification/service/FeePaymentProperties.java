package com.dth.frw.module.school.fee.notification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the money goes, bound from {@code school-fee.payment.*}: the bank account named in
 * the e-mail, the VietQR image that carries it and the prefix every transfer description
 * starts with. The values are copied onto each payment request when it is made, so changing
 * them later does not change what a student was already asked to pay into.
 *
 * @param orderCodePrefix starts every order code, so a bank line can be matched to its request
 */
@ConfigurationProperties(prefix = "school-fee.payment")
public record FeePaymentProperties(BankAccount bankAccount, VietQr vietQr, String orderCodePrefix) {

    public FeePaymentProperties {
        bankAccount = bankAccount == null ? new BankAccount(null, null, null) : bankAccount;
        vietQr = vietQr == null ? new VietQr(null, null, null) : vietQr;
        orderCodePrefix = orderCodePrefix == null || orderCodePrefix.isBlank() ? "NBK" : orderCodePrefix;
    }

    /** The account the transfer is made into. */
    public record BankAccount(String number, String bank, String holder) {
    }

    /** The VietQR image service and how it draws the code. */
    public record VietQr(String baseUrl, String template, String showInfo) {
    }

    /** True when every value a payment request needs is present. */
    public boolean configured() {
        return notBlank(bankAccount.number()) && notBlank(bankAccount.bank()) && notBlank(bankAccount.holder())
                && notBlank(vietQr.baseUrl()) && notBlank(vietQr.template()) && notBlank(vietQr.showInfo());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
