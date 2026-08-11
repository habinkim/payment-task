package com.switchwon.payment.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentQueryApiIT {

    private static final Long RICH_WALLET = 1L;
    private static final Long POOR_WALLET = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void pay(String merchantPaymentNo, Long walletId, String amount) throws Exception {
        String body = objectMapper.writeValueAsString(
                new PaymentRequest(merchantPaymentNo, walletId, new BigDecimal(amount), "USD"));
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("결제번호로 조회하면 게이트웨이 대조에 필요한 값이 함께 나온다")
    void findByMerchantPaymentNoReturnsReconcilableFields() throws Exception {
        pay("QRY-001", RICH_WALLET, "100");

        mockMvc.perform(get("/api/v1/payments/QRY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.merchantPaymentNo").value("QRY-001"))
                .andExpect(jsonPath("$.returnObject.status").value("COMPLETED"))
                .andExpect(jsonPath("$.returnObject.externalTransactionId").exists())
                .andExpect(jsonPath("$.returnObject.externalResponseCode").exists())
                .andExpect(jsonPath("$.returnObject.requestedAt").exists())
                .andExpect(jsonPath("$.returnObject.respondedAt").exists())
                .andExpect(jsonPath("$.returnObject.createdAt").exists());
    }

    @Test
    @DisplayName("실패한 결제를 조회하면 재시도 가능 여부를 알 수 있다")
    void failedPaymentExposesRetriable() throws Exception {
        pay("ERR500-QRY-002", RICH_WALLET, "100");

        mockMvc.perform(get("/api/v1/payments/ERR500-QRY-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.status").value("FAILED"))
                .andExpect(jsonPath("$.returnObject.failureReason").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.returnObject.retriable").value(true));
    }

    @Test
    @DisplayName("결제가 실패했어도 조회 자체는 성공으로 응답한다")
    void queryingFailedPaymentStillReturnsOk() throws Exception {
        pay("DECLINE-QRY-003", RICH_WALLET, "100");

        mockMvc.perform(get("/api/v1/payments/DECLINE-QRY-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.status").value("FAILED"));
    }

    @Test
    @DisplayName("없는 결제번호를 조회하면 찾을 수 없다고 응답한다")
    void missingPaymentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/NOPE-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록을 조회하면 페이지 정보가 함께 나온다")
    void listReturnsPageMetadata() throws Exception {
        pay("QRY-LIST-001", RICH_WALLET, "10");
        pay("QRY-LIST-002", RICH_WALLET, "10");

        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content").isArray())
                .andExpect(jsonPath("$.returnObject.page").value(0))
                .andExpect(jsonPath("$.returnObject.size").value(20))
                .andExpect(jsonPath("$.returnObject.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.returnObject.hasNext").exists());
    }

    @Test
    @DisplayName("상태로 걸러 조회한다")
    void filtersByStatus() throws Exception {
        pay("QRY-ST-001", RICH_WALLET, "10");
        pay("DECLINE-QRY-ST-002", RICH_WALLET, "10");

        mockMvc.perform(get("/api/v1/payments").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].status")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("FAILED"))));
    }

    @Test
    @DisplayName("지갑으로 걸러 조회한다")
    void filtersByWallet() throws Exception {
        pay("QRY-W-001", RICH_WALLET, "10");
        pay("QRY-W-002", POOR_WALLET, "5");

        mockMvc.perform(get("/api/v1/payments").param("walletId", POOR_WALLET.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].walletId")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(POOR_WALLET.intValue()))));
    }

    @Test
    @DisplayName("생성 시각이 시작 기준보다 이른 건은 제외된다")
    void excludesPaymentsBeforeFrom() throws Exception {
        pay("QRY-FROM-001", RICH_WALLET, "10");
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/payments").param("from", future.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].merchantPaymentNo")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("QRY-FROM-001"))));
    }

    @Test
    @DisplayName("생성 시각이 종료 기준보다 늦은 건은 제외된다")
    void excludesPaymentsAfterTo() throws Exception {
        pay("QRY-TO-001", RICH_WALLET, "10");
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/payments").param("to", past.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].merchantPaymentNo")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("QRY-TO-001"))));
    }

    @Test
    @DisplayName("기간 안에 있는 건은 조회된다")
    void includesPaymentsWithinRange() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        pay("QRY-RANGE-001", RICH_WALLET, "10");
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(get("/api/v1/payments")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].merchantPaymentNo")
                        .value(org.hamcrest.Matchers.hasItem("QRY-RANGE-001")));
    }

    @Test
    @DisplayName("상태와 기간을 함께 걸러 조회한다")
    void filtersByStatusAndRange() throws Exception {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        pay("QRY-MIX-001", RICH_WALLET, "10");
        pay("DECLINE-QRY-MIX-002", RICH_WALLET, "10");

        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "FAILED")
                        .param("from", from.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.content[*].merchantPaymentNo")
                        .value(org.hamcrest.Matchers.hasItem("DECLINE-QRY-MIX-002")))
                .andExpect(jsonPath("$.returnObject.content[*].merchantPaymentNo")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("QRY-MIX-001"))));
    }

    @Test
    @DisplayName("페이지 크기는 100을 넘지 않는다")
    void pageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/api/v1/payments").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.size").value(100));
    }

    @Test
    @DisplayName("페이지 크기가 0 이하면 거부한다")
    void rejectsNonPositivePageSize() throws Exception {
        mockMvc.perform(get("/api/v1/payments").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지갑을 조회하면 잔액과 통화가 나온다")
    void walletQueryReturnsBalance() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/" + RICH_WALLET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.walletId").value(RICH_WALLET))
                .andExpect(jsonPath("$.returnObject.currency").value("USD"))
                .andExpect(jsonPath("$.returnObject.balance").exists());
    }

    @Test
    @DisplayName("없는 지갑을 조회하면 찾을 수 없다고 응답한다")
    void missingWalletReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }
}
