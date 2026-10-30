package com.finsecure.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.finsecure.validator.requestlog.ApiRequestLog;
import com.finsecure.validator.requestlog.ApiRequestLogRepository;
import com.finsecure.validator.requestlog.RequestLogService;
import com.finsecure.validator.transaction.TransactionRecordRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiRequestLogRepository logRepository;

    @Autowired
    private TransactionRecordRepository transactionRepository;

    @Autowired
    private RequestLogService logService;

    @BeforeEach
    void clean() {
        logRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    private static String txn(String id, String amount, String currency) {
        return """
                {"transactionId":"%s","accountNumber":"4111111111111111","merchantId":"MER1001",
                 "amount":%s,"currency":"%s","country":"SG"}
                """.formatted(id, amount, currency);
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").exists())
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    void validTransactionIsApproved() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(txn("TXN-200001", "120.50", "SGD")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(transactionRepository.existsByTransactionId("TXN-200001")).isTrue();
    }

    @Test
    void invalidTransactionIsRejectedWithReasons() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(txn("TXN-200002", "50000", "XYZ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.errors[*].code").value(hasItem("AMOUNT_LIMIT_EXCEEDED")))
                .andExpect(jsonPath("$.errors[*].code").value(hasItem("CURRENCY_UNSUPPORTED")));

        assertThat(transactionRepository.existsByTransactionId("TXN-200002")).isFalse();
    }

    @Test
    void sameTransactionTwiceIsDuplicate() throws Exception {
        String body = txn("TXN-200003", "10.00", "USD");
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TXN_DUPLICATE"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void everyApiCallIsStoredWithMaskedAccount() throws Exception {
        MvcResult result = mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "junit")
                        .content(txn("TXN-200004", "75.00", "EUR")))
                .andExpect(status().isOk())
                .andReturn();
        mvc.perform(get("/health")).andExpect(status().isOk());

        String requestId = result.getResponse().getHeader("X-Request-Id");
        ApiRequestLog entry = logRepository.findByRequestId(requestId).orElseThrow();

        assertThat(entry.getMethod()).isEqualTo("POST");
        assertThat(entry.getPath()).isEqualTo("/validate");
        assertThat(entry.getStatusCode()).isEqualTo(200);
        assertThat(entry.getOutcome()).isEqualTo("APPROVED");
        assertThat(entry.getTransactionId()).isEqualTo("TXN-200004");
        assertThat(entry.getUserAgent()).isEqualTo("junit");
        assertThat(entry.getRequestBody()).contains("************1111").doesNotContain("4111111111111111");
        assertThat(entry.getResponseBody()).contains("APPROVED");
        assertThat(logRepository.count()).isEqualTo(2);
    }

    @Test
    void dashboardStatsReflectStoredRequests() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(txn("TXN-200005", "10.00", "SGD")));
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(txn("TXN-200006", "10.00", "JPY")));
        mvc.perform(get("/health"));

        var stats = logService.stats(RequestLogService.Window.DAY);
        assertThat(stats.totalRequests()).isEqualTo(3);
        assertThat(stats.approved()).isEqualTo(1);
        assertThat(stats.rejected()).isEqualTo(1);
        assertThat(stats.clientErrors()).isEqualTo(1);
        assertThat(stats.timelineCounts()).hasSize(24);
        assertThat(stats.timelineCounts().stream().mapToLong(Long::longValue).sum()).isEqualTo(3);
        assertThat(stats.endpoints()).extracting(e -> e.path()).containsAll(List.of("/validate", "/health"));
    }

    @Test
    void prometheusEndpointExposesCustomMetrics() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content(txn("TXN-200007", "10.00", "SGD")));
        MvcResult result = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .contains("finsecure_transactions_total")
                .contains("http_server_requests_seconds");
    }

    @Test
    void probesAreAvailable() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
