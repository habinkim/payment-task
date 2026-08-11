package com.switchwon.payment.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentApiIT {

    private static final Long RICH_WALLET = 1L;
    private static final Long POOR_WALLET = 2L;
    private static final Long JPY_WALLET = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletStore walletStore;

    private String body(String merchantPaymentNo, Long walletId, String amount, String currency) throws Exception {
        return objectMapper.writeValueAsString(
                new PaymentRequest(merchantPaymentNo, walletId, new BigDecimal(amount), currency));
    }

    private BigDecimal balanceOf(Long walletId) {
        return walletStore.findById(walletId).orElseThrow().balance();
    }

    @Test
    @DisplayName("정상 결제는 완료되고 잔액이 차감된다")
    void completesAndDeducts() throws Exception {
        BigDecimal before = balanceOf(RICH_WALLET);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY-IT-001", RICH_WALLET, "100.0000", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.status").value("COMPLETED"))
                .andExpect(jsonPath("$.returnObject.externalTransactionId").exists());

        assertThat(balanceOf(RICH_WALLET)).isEqualByComparingTo(before.subtract(new BigDecimal("100")));
    }

    @Test
    @DisplayName("잔액이 부족하면 결제가 실패하고 잔액이 그대로다")
    void insufficientBalanceKeepsBalance() throws Exception {
        BigDecimal before = balanceOf(POOR_WALLET);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY-IT-002", POOR_WALLET, "1000.0000", "USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.returnObject.status").value("FAILED"))
                .andExpect(jsonPath("$.returnObject.externalTransactionId").doesNotExist());

        assertThat(balanceOf(POOR_WALLET)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("게이트웨이가 거절하면 잔액이 차감되지 않는다")
    void declinedKeepsBalance() throws Exception {
        BigDecimal before = balanceOf(RICH_WALLET);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("DECLINE-IT-003", RICH_WALLET, "100.0000", "USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.returnObject.failureReason").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.returnObject.retriable").value(false));

        assertThat(balanceOf(RICH_WALLET)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("서버 오류는 재시도 가능한 실패로 기록된다")
    void serverErrorIsRetriable() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ERR500-IT-004", RICH_WALLET, "100.0000", "USD")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.returnObject.retriable").value(true));
    }

    @Test
    @DisplayName("잘못된 요청은 재시도 불가한 실패로 기록된다")
    void badRequestIsPermanent() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ERR400-IT-005", RICH_WALLET, "100.0000", "USD")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.returnObject.retriable").value(false));
    }

    @Test
    @DisplayName("타임아웃은 결과 미상으로 남고 잔액을 건드리지 않는다")
    void timeoutIsInDoubt() throws Exception {
        BigDecimal before = balanceOf(RICH_WALLET);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("TIMEOUT-IT-006", RICH_WALLET, "100.0000", "USD")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_DOUBT"))
                .andExpect(jsonPath("$.returnObject.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.returnObject.retriable").value(true));

        assertThat(balanceOf(RICH_WALLET)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("완료된 결제를 다시 요청하면 최초 결과가 반환되고 재차감되지 않는다")
    void duplicateReturnsOriginal() throws Exception {
        String request = body("PAY-IT-007", RICH_WALLET, "100.0000", "USD");
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
        BigDecimal afterFirst = balanceOf(RICH_WALLET);

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.status").value("COMPLETED"));

        assertThat(balanceOf(RICH_WALLET)).isEqualByComparingTo(afterFirst);
    }

    @Test
    @DisplayName("지갑 통화와 다르면 요청이 거부된다")
    void currencyMismatchRejected() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY-IT-008", JPY_WALLET, "100.0000", "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("없는 지갑이면 찾을 수 없다고 응답한다")
    void walletNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY-IT-009", 9999L, "100.0000", "USD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("금액이 0 이하면 요청 단계에서 거부된다")
    void rejectsNonPositiveAmount() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY-IT-010", RICH_WALLET, "0", "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("결제번호 형식이 맞지 않으면 요청 단계에서 거부된다")
    void rejectsInvalidMerchantPaymentNo() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("PAY IT 011", RICH_WALLET, "100", "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
