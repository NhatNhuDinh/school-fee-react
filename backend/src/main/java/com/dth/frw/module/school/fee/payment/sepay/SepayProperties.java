package com.dth.frw.module.school.fee.payment.sepay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code school-fee.sepay.*}. None of the secrets belong in version control: they live in
 * {@code backend/config/application.properties} or the environment.
 *
 * @param webhook          how a webhook is authenticated — every method that has a secret configured must pass
 * @param apiToken         the token for SePay's list-transactions API, used by "Kiểm tra giao dịch"
 * @param apiBaseUrl       the SePay API root
 * @param organizationCode the school whose bank account this is; a bank line that matches nothing is filed under
 *                         it so that school's accountant can review it
 * @param checkWindowDays  how far back a manual check looks
 * @param requestTtl       how long a transfer request stays payable
 */
@ConfigurationProperties(prefix = "school-fee.sepay")
public record SepayProperties(Webhook webhook, String apiToken, String apiBaseUrl, String organizationCode,
                              Integer checkWindowDays, Duration requestTtl) {

    public SepayProperties {
        webhook = webhook == null ? new Webhook(null, null, null) : webhook;
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "https://userapi.sepay.vn/v2" : apiBaseUrl;
        checkWindowDays = checkWindowDays == null || checkWindowDays < 1 ? 3 : checkWindowDays;
        requestTtl = requestTtl == null ? Duration.ofHours(48) : requestTtl;
    }

    /**
     * @param apiKey           sent by SePay as {@code Authorization: Apikey <key>}
     * @param hmacSecret       secret of the HMAC-SHA256 method ({@code X-SePay-Signature} / {@code X-SePay-Timestamp})
     * @param toleranceSeconds how far the signed timestamp may be from now
     */
    public record Webhook(String apiKey, String hmacSecret, Integer toleranceSeconds) {
        public Webhook {
            toleranceSeconds = toleranceSeconds == null || toleranceSeconds < 1 ? 300 : toleranceSeconds;
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public boolean hasHmac() {
            return hmacSecret != null && !hmacSecret.isBlank();
        }

        /** An endpoint with no secret would take anyone's word that money arrived, so it answers nobody. */
        public boolean configured() {
            return hasApiKey() || hasHmac();
        }
    }

    public boolean apiConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }
}
