package com.dth.frw.school;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Money arriving by transfer: the counter asks for a transfer, the bank reports it, and the application
 * decides. Each test is one of the ways that report can go — paid, paid twice, short, unknown, by hand —
 * and what has to be true afterwards is the point: a receipt exactly when the money matched, a line for
 * a person to read otherwise, and never two receipts for one transfer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "school-fee.sepay.webhook.api-key=test-key",
        "school-fee.sepay.organization-code=NBK",
        "school-fee.payment.bank-account.number=22226376222",
        "school-fee.payment.bank-account.bank=TPBank",
        "school-fee.payment.bank-account.holder=NHU DINH NHAT"
})
class SepayWebhookTest {

    private static final String PASSWORD = "Admin@123";
    private static final String NBK = "nbk.ketoan";
    private static final String ACCOUNT = "22226376222";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private MockMvc mvc;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private String token;
    private long year;
    private long enrollment;
    private List<Integer> receivables;

    /** A school year with one class, one student, one mandatory khoản thu and a đợt thu for all of it. */
    @BeforeEach
    void aStudentWhoOwes() throws Exception {
        token = bearer(NBK);
        year = id(post("/api/academic_years"), "{\"startDate\": \"2085-09-01\", \"endDate\": \"2086-05-31\"}");
        long group = id(post("/api/class_groups"), "{\"academicYear\": {\"id\": %d}, \"name\": \"Khối 10\"}".formatted(year));
        long klass = id(post("/api/school_classes"), "{\"classGroup\": {\"id\": %d}, \"name\": \"10A1\"}".formatted(group));
        id(post("/api/students"), "{\"academicYear\": {\"id\": %d}, \"schoolClass\": {\"id\": %d}, \"fullName\": \"Học Sinh Thử\"}".formatted(year, klass));
        long unit = id(post("/api/measurement_units"), "{\"name\": \"Tháng thử " + IDS.incrementAndGet() + "\"}");
        long item = id(post("/api/fee_items"), ("{\"academicYear\": {\"id\": %d}, \"name\": \"Học phí\", \"scope\": \"ALL_SCHOOL\", \"priceMode\": \"SINGLE\", \"basePrice\": 500000, "
                + "\"measurementUnit\": {\"id\": %d}, \"vatRate\": 0, \"collectionCycle\": \"MONTHLY\", \"mandatory\": true, \"active\": true, "
                + "\"collectionSchedules\": [{\"sequence\": 1, \"collectionMonth\": 11}]}").formatted(year, unit));
        id(post("/api/fee_collection_batches"), ("{\"academicYear\": {\"id\": %d}, \"name\": \"Đợt thu tháng 11\", \"collectionMonth\": 11, "
                + "\"scope\": \"ALL_SCHOOL\", \"items\": [{\"feeItemId\": %d, \"quantity\": 1}]}").formatted(year, item));

        String hits = mvc.perform(get("/api/fee_payments/students").param("academicYearId", "" + year).param("q", "")
                        .header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        enrollment = ((Number) JsonPath.read(hits, "$[0].enrollmentId")).longValue();
        String debts = mvc.perform(get("/api/fee_payments/students/" + enrollment + "/debts")
                        .param("academicYearId", "" + year).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        receivables = JsonPath.read(debts, "$.batches[0].lines[?(@.outstanding > 0)].receivableId");
    }

    /** Creates something through the API as the school accountant and returns its id. */
    private long id(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String json) throws Exception {
        String response = mvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("\"id\""), "create failed: " + response);
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void aCallWithoutTheKeyIsRefusedAndRecordsNothing() throws Exception {
        mvc.perform(post("/api/sepay/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content(body(1000, "NBKAAAAAAAAAAAAAA", "in", ACCOUNT)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
        mvc.perform(post("/api/sepay/webhook").header("Authorization", "Apikey wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(1000, "NBKAAAAAAAAAAAAAA", "in", ACCOUNT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnreadableBodyIsABadRequest() throws Exception {
        mvc.perform(post("/api/sepay/webhook").header("Authorization", "Apikey test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"id\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void theMatchingTransferPaysTheRequestAndAReplayDoesNothing() throws Exception {
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        String code = JsonPath.read(request, "$.orderCode");
        long amount = ((Number) JsonPath.read(request, "$.amount")).longValue();
        long sepayId = IDS.incrementAndGet();

        webhook(sepayId, amount, code, "in", ACCOUNT).andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));

        mvc.perform(get("/api/fee_payments/transfer-requests/" + id).param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.paidBy", is("BANK")))
                .andExpect(jsonPath("$.receipts[0].method", is("TRANSFER")))
                .andExpect(jsonPath("$.receipts[0].collectorName", is("SePay (tự động)")));
        int receipts = receiptCount();

        // SePay retries (or a manual check sees the same line): answered success, no second receipt.
        webhook(sepayId, amount, code, "in", ACCOUNT).andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));
        org.junit.jupiter.api.Assertions.assertEquals(receipts, receiptCount());
        mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "processed").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.sepayId == %d)]".formatted(sepayId), hasSize(1)))
                .andExpect(jsonPath("$[?(@.sepayId == %d)].status".formatted(sepayId), hasItem("MATCHED")));
    }

    @Test
    void aBankThatDroppedThePunctuationAndChangedTheCaseStillMatches() throws Exception {
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        String code = JsonPath.read(request, "$.orderCode");
        long amount = ((Number) JsonPath.read(request, "$.amount")).longValue();

        webhook(IDS.incrementAndGet(), amount, "ck hoc phi " + code.toLowerCase() + " - nguyen van a", "in", ACCOUNT)
                .andExpect(status().isOk());

        mvc.perform(get("/api/fee_payments/transfer-requests/" + id).param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.status", is("PAID")));
    }

    @Test
    void aShortTransferIsNeverPartPaidItGoesToReview() throws Exception {
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        String code = JsonPath.read(request, "$.orderCode");
        long amount = ((Number) JsonPath.read(request, "$.amount")).longValue();
        long sepayId = IDS.incrementAndGet();
        String studentName = JsonPath.read(request, "$.studentName");
        int before = receiptCount();

        webhook(sepayId, amount - 20000, code, "in", ACCOUNT).andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));

        org.junit.jupiter.api.Assertions.assertEquals(before, receiptCount());
        mvc.perform(get("/api/fee_payments/transfer-requests/" + id).param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.status", is("PENDING")));
        mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "review").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.sepayId == %d)].reason".formatted(sepayId), hasItem("AMOUNT_MISMATCH")))
                .andExpect(jsonPath("$[?(@.sepayId == %d)].suggestedStudentName".formatted(sepayId), hasItem(studentName)));
    }

    @Test
    void linesThatCannotBeMatchedAreKeptForAPersonToRead() throws Exception {
        long noCode = IDS.incrementAndGet();
        long unknown = IDS.incrementAndGet();
        long outgoing = IDS.incrementAndGet();
        long elsewhere = IDS.incrementAndGet();

        webhook(noCode, 500000, "chuyen tien hoc phi", "in", ACCOUNT).andExpect(status().isOk());
        webhook(unknown, 500001, "NBK0000000000000Z", "in", ACCOUNT).andExpect(status().isOk());
        webhook(outgoing, 500002, "NBK0000000000000Z", "out", ACCOUNT).andExpect(status().isOk());
        webhook(elsewhere, 500003, "NBK0000000000000Z", "in", "99999999").andExpect(status().isOk());

        String review = mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "review").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(List.of("NO_CODE"), JsonPath.read(review, "$[?(@.sepayId == %d)].reason".formatted(noCode)));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("UNKNOWN_CODE"), JsonPath.read(review, "$[?(@.sepayId == %d)].reason".formatted(unknown)));
        // Money out and money into some other account are not ours to collect: filed, but not for review.
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), JsonPath.read(review, "$[?(@.sepayId == %d)]".formatted(outgoing)));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(), JsonPath.read(review, "$[?(@.sepayId == %d)]".formatted(elsewhere)));
        mvc.perform(get("/api/fee_payments/bank-transactions/summary").param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.review", is(2)));
    }

    @Test
    void aTransferTheCashierAlreadyConfirmedIsNotPaidTwiceWhenTheBankLineArrives() throws Exception {
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        String code = JsonPath.read(request, "$.orderCode");
        long amount = ((Number) JsonPath.read(request, "$.amount")).longValue();

        mvc.perform(post("/api/fee_payments/transfer-requests/" + id + "/confirm").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"academicYearId\": %d}".formatted(year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.paidBy", is("MANUAL")));
        int receipts = receiptCount();

        long sepayId = IDS.incrementAndGet();
        webhook(sepayId, amount, code, "in", ACCOUNT).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(receipts, receiptCount());
        mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "processed").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.sepayId == %d)].status".formatted(sepayId), hasItem("MATCHED")));
    }

    @Test
    void aSecondTransferForAnAlreadyPaidRequestIsAnOverpaymentForReview() throws Exception {
        String request = createRequest();
        String code = JsonPath.read(request, "$.orderCode");
        long amount = ((Number) JsonPath.read(request, "$.amount")).longValue();
        webhook(IDS.incrementAndGet(), amount, code, "in", ACCOUNT).andExpect(status().isOk());
        int receipts = receiptCount();

        long again = IDS.incrementAndGet();
        webhook(again, amount, code, "in", ACCOUNT).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(receipts, receiptCount());
        mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "review").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.sepayId == %d)].reason".formatted(again), hasItem("ALREADY_PAID")));
    }

    @Test
    void anAccountantCanSetAReviewLineAside() throws Exception {
        long sepayId = IDS.incrementAndGet();
        webhook(sepayId, 500000, "khong co ma", "in", ACCOUNT).andExpect(status().isOk());
        long txId = ((Number) ((List<?>) JsonPath.read(
                mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                                .param("tab", "review").header("Authorization", token))
                        .andReturn().getResponse().getContentAsString(),
                "$[?(@.sepayId == %d)].id".formatted(sepayId))).get(0)).longValue();

        mvc.perform(post("/api/fee_payments/bank-transactions/" + txId + "/ignore").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\": %d, \"note\": \"Chuyển nhầm, đã hoàn tiền\"}".formatted(year)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/fee_payments/bank-transactions").param("academicYearId", "" + year)
                        .param("tab", "processed").header("Authorization", token))
                .andExpect(jsonPath("$[?(@.id == %d)].reason".formatted(txId), hasItem("MANUAL_IGNORED")))
                .andExpect(jsonPath("$[?(@.id == %d)].note".formatted(txId), hasItem("Chuyển nhầm, đã hoàn tiền")));
    }

    @Test
    void askingAgainForTheSameKhoanThuReturnsTheSameCode() throws Exception {
        String first = createRequest();
        String second = createRequest();

        org.junit.jupiter.api.Assertions.assertEquals(
                ((Number) JsonPath.read(first, "$.id")).longValue(), ((Number) JsonPath.read(second, "$.id")).longValue());
        org.junit.jupiter.api.Assertions.assertEquals(
                (String) JsonPath.read(first, "$.orderCode"), (String) JsonPath.read(second, "$.orderCode"));
    }

    @Test
    void aCounterRequestForWhatAnEmailAskedForUsesTheEmailsCode() throws Exception {
        long mail = emailRequest("NBKEMAILSHARE0001");

        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        org.junit.jupiter.api.Assertions.assertEquals("NBKEMAILSHARE0001", (String) JsonPath.read(request, "$.orderCode"));

        int before = receiptCount();
        webhook(IDS.incrementAndGet(), owed(), "NBKEMAILSHARE0001", "in", ACCOUNT).andExpect(status().isOk());

        // One code, two rows in two tables: the line pays once, through the counter's request, and closes both.
        org.junit.jupiter.api.Assertions.assertEquals(before + 1, receiptCount());
        mvc.perform(get("/api/fee_payments/transfer-requests/" + id).param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.status", is("PAID")));
        org.junit.jupiter.api.Assertions.assertEquals("PAID",
                jdbc.queryForObject("select status from fee_payment_requests where id = ?", String.class, mail));
    }

    @Test
    void whenTheCounterRequestIsGoneTheEmailsQrStillPays() throws Exception {
        long mail = emailRequest("NBKEMAILSHARE0002");
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();
        mvc.perform(post("/api/fee_payments/transfer-requests/" + id + "/cancel").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"academicYearId\": %d}".formatted(year)))
                .andExpect(status().isOk());

        int before = receiptCount();
        webhook(IDS.incrementAndGet(), owed(), "NBKEMAILSHARE0002", "in", ACCOUNT).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(before + 1, receiptCount());
        org.junit.jupiter.api.Assertions.assertEquals("PAID",
                jdbc.queryForObject("select status from fee_payment_requests where id = ?", String.class, mail));
    }

    @Test
    void cashTakenAtTheCounterClosesTheOpenTransferRequest() throws Exception {
        String request = createRequest();
        long id = ((Number) JsonPath.read(request, "$.id")).longValue();

        mvc.perform(post("/api/fee_payments/collect-student").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\": %d, \"enrollmentId\": %d, \"method\": \"CASH\", \"paidOn\": \"%s\", \"receivableIds\": %s}"
                                .formatted(year, enrollment, java.time.LocalDate.now(), receivables)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/fee_payments/transfer-requests/" + id).param("academicYearId", "" + year)
                        .header("Authorization", token))
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    // ---- helpers

    /** What the student still owes, in total. */
    private long owed() throws Exception {
        String debts = mvc.perform(get("/api/fee_payments/students/" + enrollment + "/debts")
                        .param("academicYearId", "" + year).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        List<Number> amounts = JsonPath.read(debts, "$.batches[0].lines[?(@.outstanding > 0)].outstanding");
        return amounts.stream().mapToLong(Number::longValue).sum();
    }

    /** An e-mailed request for everything the student owes in the đợt, as the notification feature writes it. */
    private long emailRequest(String code) throws Exception {
        long batchStudent = jdbc.queryForObject(
                "select id from fee_collection_batch_students where enrollment_id = ?", Long.class, enrollment);
        long organization = jdbc.queryForObject("select organization_id from academic_years where id = ?", Long.class, year);
        return jdbc.queryForObject("""
                insert into fee_payment_requests (organization_id, academic_year_id, batch_student_id, order_code,
                    requested_amount, bank_account_number, bank_code, bank_account_holder, viet_qr_base_url,
                    viet_qr_template, viet_qr_show_info, status, deadline)
                values (?, ?, ?, ?, ?, ?, 'TPBank', 'NHU DINH NHAT', 'https://img.vietqr.io', 'compact', true, 'PENDING', ?)
                returning id
                """, Long.class, organization, year, batchStudent, code, owed(), ACCOUNT,
                java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(7)));
    }

    private String createRequest() throws Exception {
        return mvc.perform(post("/api/fee_payments/transfer-requests").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicYearId\": %d, \"enrollmentId\": %d, \"receivableIds\": %s}"
                                .formatted(year, enrollment, receivables)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();
    }

    private int receiptCount() throws Exception {
        String history = mvc.perform(get("/api/fee_payments/history").param("academicYearId", "" + year)
                        .header("Authorization", token)).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(history, "$.total")).intValue();
    }

    private org.springframework.test.web.servlet.ResultActions webhook(long id, long amount, String content, String type,
                                                                         String account) throws Exception {
        return mvc.perform(post("/api/sepay/webhook").header("Authorization", "Apikey test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body(id, amount, content, type, account)));
    }

    private static String body(long id, String content, String type, String account) {
        return body(id, 1000, content, type, account);
    }

    private static String body(long id, long amount, String content, String type, String account) {
        String now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0).minusMinutes(5).minusSeconds(id % 3000).format(TIME);
        return """
                {"id": %d, "gateway": "TPBank", "transactionDate": "%s", "accountNumber": "%s", "code": null,
                 "content": "%s", "transferType": "%s", "transferAmount": %d, "accumulated": 0, "subAccount": null,
                 "referenceCode": "FT%d", "description": ""}
                """.formatted(id, now, account, content, type, amount, id);
    }

    private String bearer(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(response, "$.accessToken");
    }
}
