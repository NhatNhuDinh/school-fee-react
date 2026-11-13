package com.dth.frw.module.school.fee.payment.sepay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dth.frw.shared.exception.ConflictException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** "Kiểm tra giao dịch" against a stand-in for SePay's list-transactions API: what is asked, and what is read back. */
class SepayClientTest {

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startSePay() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v2/transactions", exchange -> {
            requests.add(exchange.getRequestURI().getRawQuery());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            boolean second = exchange.getRequestURI().getRawQuery().contains("page=2");
            String body = second
                    ? page("[" + line("b2", "2026-11-13 10:05:00", 750000, "NBKDDDDDDDDDDDDDD") + "]", false)
                    : page("[" + line("b1", "2026-11-13 10:00:00", 520000, "NBKCCCCCCCCCCCCCC") + ","
                            + "{\"id\": \"b0\", \"transaction_date\": \"garbled\"}]", true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopSePay() {
        server.stop(0);
    }

    private static String line(String id, String date, long amount, String content) {
        return """
                {"id": "%s", "transaction_date": "%s", "account_number": "22226376222", "transfer_type": "in",
                 "amount_in": %d, "amount_out": 0, "transaction_content": "%s", "reference_number": "FT1",
                 "code": null, "bank_brand_name": "TPBank"}
                """.formatted(id, date, amount, content);
    }

    private static String page(String data, boolean more) {
        return "{\"status\": \"success\", \"data\": %s, \"meta\": {\"pagination\": {\"has_more\": %s}}}".formatted(data, more);
    }

    private SepayClient client(String token) {
        String base = "http://localhost:" + server.getAddress().getPort() + "/v2";
        return new SepayClient(new SepayProperties(null, token, base, null, null, null), new ObjectMapper());
    }

    @Test
    void readsEveryPageAndSkipsLinesItCannotRead() {
        List<SepayPayload> lines = client("secret-token").incomingSince(LocalDateTime.of(2026, 11, 10, 0, 0));

        assertEquals(2, lines.size());
        assertEquals("b1", lines.get(0).uuid());
        assertEquals(520000, lines.get(0).amount().longValue());
        assertEquals("NBKDDDDDDDDDDDDDD", lines.get(1).content());
    }

    @Test
    void asksForIncomingLinesSinceTheStartOfTheWindowWithTheTokenInAHeader() {
        client("secret-token").incomingSince(LocalDateTime.of(2026, 11, 10, 0, 0));

        assertEquals(2, requests.size());
        assertTrue(requests.get(0).contains("transfer_type=in"));
        String first = java.net.URLDecoder.decode(requests.get(0), StandardCharsets.UTF_8);
        assertTrue(first.contains("transaction_date_from=2026-11-10 00:00:00"), first);
        assertTrue(requests.get(0).contains("transaction_date_sort=asc"));
        assertEquals("Bearer secret-token", authorizations.get(0));
        // The secret travels in a header, never in the URL.
        assertTrue(requests.stream().noneMatch(query -> query.contains("secret-token")));
    }

    @Test
    void withoutAnApiTokenItRefusesBeforeCallingAnyone() {
        assertThrows(ConflictException.class, () -> client(" ").incomingSince(LocalDateTime.now()));
        assertEquals(0, requests.size());
    }
}
