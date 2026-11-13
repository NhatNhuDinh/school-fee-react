package com.dth.frw.module.school.fee.payment.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Reading SePay's two spellings of a bank line, and recognising the same line arriving twice. */
class SepayPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SepayPayload webhook(String json) {
        return SepayPayload.fromWebhook(MAPPER.readTree(json), json);
    }

    private static final String WEBHOOK = """
            {"id": 92704, "gateway": "TPBank", "transactionDate": "2026-11-13 14:32:05", "accountNumber": "22226376222",
             "code": null, "content": "NBK3KG90GMH95YB8Z chuyen tien", "transferType": "in", "transferAmount": 2340500,
             "accumulated": 0, "subAccount": null, "referenceCode": "FT26317ABC", "description": ""}
            """;

    @Test
    void aWebhookBodyIsRead() {
        SepayPayload p = webhook(WEBHOOK);

        assertNotNull(p);
        assertEquals(92704L, p.id());
        assertEquals("in", p.transferType());
        assertEquals(0, new BigDecimal("2340500").compareTo(p.amount()));
        assertEquals("22226376222", p.accountNumber());
        assertNull(p.code());
        assertEquals("FT26317ABC", p.referenceCode());
    }

    @Test
    void aBodyMissingWhatMatchingNeedsIsRefusedNotGuessed() {
        assertNull(webhook("{\"id\": 1, \"transferType\": \"in\"}"));
        assertNull(webhook("{\"id\": 1, \"transactionDate\": \"13/11/2026\", \"transferType\": \"in\", \"transferAmount\": 5}"));
        assertNull(webhook("{\"id\": 1, \"transactionDate\": \"2026-11-13 14:32:05\", \"transferType\": \"in\", \"transferAmount\": -5}"));
        assertNull(webhook("[1, 2]"));
    }

    @Test
    void theSameLineFromTheWebhookAndFromTheApiHasOneKey() {
        SepayPayload viaWebhook = webhook(WEBHOOK);
        String api = """
                {"id": "a1b2c3d4-0000-0000-0000-000000000001", "transaction_date": "2026-11-13 14:32:05",
                 "account_number": "22226376222", "transfer_type": "in", "amount_in": 2340500, "amount_out": 0,
                 "transaction_content": "NBK3KG90GMH95YB8Z chuyen tien", "reference_number": "FT26317-ABC",
                 "code": "NBK3KG90GMH95YB8Z", "bank_brand_name": "TPBank"}
                """;
        SepayPayload viaApi = SepayPayload.fromApi(MAPPER.readTree(api));

        assertNotNull(viaApi);
        assertEquals("a1b2c3d4-0000-0000-0000-000000000001", viaApi.uuid());
        assertNull(viaApi.id());
        assertEquals(viaWebhook.dedupeKey(), viaApi.dedupeKey());
    }

    @Test
    void punctuationAndCaseInTheMemoDoNotMakeANewLine() {
        SepayPayload a = webhook(WEBHOOK);
        SepayPayload b = webhook(WEBHOOK.replace("NBK3KG90GMH95YB8Z chuyen tien", "nbk3kg90gmh95yb8z - Chuyen  Tien"));
        assertEquals(a.dedupeKey(), b.dedupeKey());
    }

    @Test
    void differentMoneyIsADifferentLine() {
        SepayPayload a = webhook(WEBHOOK);
        assertNotEquals(a.dedupeKey(), webhook(WEBHOOK.replace("2340500", "2340600")).dedupeKey());
        assertNotEquals(a.dedupeKey(), webhook(WEBHOOK.replace("14:32:05", "14:32:06")).dedupeKey());
        assertNotEquals(a.dedupeKey(), webhook(WEBHOOK.replace("22226376222", "22226376223")).dedupeKey());
    }
}
