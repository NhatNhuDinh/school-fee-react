package com.dth.frw.module.school.fee.payment.sepay;

import com.dth.frw.shared.exception.ConflictException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads incoming bank lines from SePay's list-transactions API (v2), for the "Kiểm tra giao dịch" button:
 * the way to catch up when a webhook was lost. Only reads; nothing here changes anything at SePay.
 */
@Component
public class SepayClient {

    private static final Logger log = LoggerFactory.getLogger(SepayClient.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;

    private final SepayProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    public SepayClient(SepayProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return properties.apiConfigured();
    }

    /** Incoming lines since {@code from} (Vietnam time), oldest first; at most {@code MAX_PAGES} pages of 100. */
    public List<SepayPayload> incomingSince(LocalDateTime from) {
        if (!configured()) {
            throw new ConflictException("Chưa cấu hình API token của SePay (school-fee.sepay.api-token)");
        }
        List<SepayPayload> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = fetch(from, page);
            JsonNode data = body.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    SepayPayload payload = SepayPayload.fromApi(item);
                    if (payload != null) {
                        result.add(payload);
                    }
                }
            }
            if (!body.path("meta").path("pagination").path("has_more").asBoolean(false)) {
                break;
            }
        }
        return result;
    }

    private JsonNode fetch(LocalDateTime from, int page) {
        try {
            String json = client.get()
                    .uri(properties.apiBaseUrl() + "/transactions?transfer_type=in&transaction_date_from={from}"
                            + "&transaction_date_sort=asc&per_page={size}&page={page}",
                            from.format(TIME), PAGE_SIZE, page)
                    .header("Authorization", "Bearer " + properties.apiToken())
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(json == null ? "{}" : json);
        } catch (RestClientException e) {
            // The message can carry the request line; it never carries the token, which is a header.
            log.warn("SePay list-transactions failed: {}", e.getMessage());
            throw new ConflictException("Không kết nối được SePay để kiểm tra giao dịch, vui lòng thử lại sau");
        }
    }
}
