package com.switchwon.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.payment.infra.PaymentLedgerStore;
import com.switchwon.payment.wallet.infra.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

class ChaosPaymentIT {

    private static final Long RICH_WALLET = 1L;
    private static final String AMOUNT = "100.0000";

    abstract static class ChaosTestBase {

        @Autowired
        protected MockMvc mockMvc;

        @Autowired
        protected ObjectMapper objectMapper;

        @Autowired
        protected PaymentLedgerStore ledgerStore;

        @Autowired
        protected WalletStore walletStore;

        protected String body(String paymentNo) throws Exception {
            return objectMapper.writeValueAsString(
                    new PaymentRequest(paymentNo, RICH_WALLET, new BigDecimal(AMOUNT), "USD"));
        }

        protected BigDecimal balance() {
            return walletStore.findById(RICH_WALLET).orElseThrow().balance();
        }

        protected Payment ledgerOf(String paymentNo) {
            return ledgerStore.findByPaymentNo(paymentNo).orElseThrow();
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "payment.gateway.chaos.enabled=true",
            "payment.gateway.chaos.timeout-rate=1.0",
            "payment.gateway.chaos.failure-rate=0.0",
            "payment.reconcile.enabled=false"
    })
    @AutoConfigureMockMvc
    @Transactional
    @DisplayName("타임아웃 장애가 주입된 상태")
    class WhenTimeoutInjected extends ChaosTestBase {

        @Test
        @DisplayName("결과 미상으로 기록되고 잔액이 차감되지 않는다")
        void recordsUnknownWithoutDeduction() throws Exception {
            BigDecimal before = balance();

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-T-001")))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.code").value("PAYMENT_IN_DOUBT"));

            Payment saved = ledgerOf("CHAOS-T-001");
            assertThat(saved.status()).isEqualTo(PaymentStatus.UNKNOWN);
            assertThat(saved.retriable()).isTrue();
            assertThat(balance()).isEqualByComparingTo(before);
        }

        @Test
        @DisplayName("장애 주입으로 실패했음이 응답 코드로 원장에 남는다")
        void recordsChaosOrigin() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-T-002")));

            Payment saved = ledgerOf("CHAOS-T-002");
            assertThat(saved.externalResponseCode()).isEqualTo("CHAOS_TIMEOUT");
            assertThat(saved.externalTransactionId()).isNull();
        }

        @Test
        @DisplayName("게이트웨이 호출 시각과 응답 시각이 남는다")
        void recordsTimestamps() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-T-003")));

            Payment saved = ledgerOf("CHAOS-T-003");
            assertThat(saved.requestedAt()).isNotNull();
            assertThat(saved.respondedAt()).isNotNull();
            assertThat(saved.respondedAt()).isAfterOrEqualTo(saved.requestedAt());
        }

        @Test
        @DisplayName("접두어로 지정한 승인은 장애 주입과 무관하게 성공한다")
        void prefixStillOverridesChaos() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(body("DECLINE-CHAOS-004")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.returnObject.failureReason").value("PAYMENT_DECLINED"));

            assertThat(ledgerOf("DECLINE-CHAOS-004").externalResponseCode()).isEqualTo("DECLINED");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "payment.gateway.chaos.enabled=true",
            "payment.gateway.chaos.timeout-rate=0.0",
            "payment.gateway.chaos.failure-rate=1.0",
            "payment.reconcile.enabled=false"
    })
    @AutoConfigureMockMvc
    @Transactional
    @DisplayName("실패 장애가 주입된 상태")
    class WhenFailureInjected extends ChaosTestBase {

        @Test
        @DisplayName("재시도 가능한 실패로 기록되고 잔액이 차감되지 않는다")
        void recordsRetriableFailure() throws Exception {
            BigDecimal before = balance();

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-F-001")))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("SYSTEM_ERROR"));

            Payment saved = ledgerOf("CHAOS-F-001");
            assertThat(saved.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(saved.failureReason().name()).isEqualTo("SYSTEM_ERROR");
            assertThat(saved.retriable()).isTrue();
            assertThat(saved.externalResponseCode()).isEqualTo("CHAOS_FAILURE");
            assertThat(balance()).isEqualByComparingTo(before);
        }

        @Test
        @DisplayName("실패해도 원장은 남아 조회할 수 있다")
        void failedPaymentIsStillQueryable() throws Exception {
            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-F-002")));

            assertThat(ledgerStore.findByPaymentNo("CHAOS-F-002")).isPresent();
        }

        @Test
        @DisplayName("같은 결제번호로 다시 요청해도 재차감되지 않는다")
        void retryDoesNotDoubleCharge() throws Exception {
            BigDecimal before = balance();
            String request = body("CHAOS-F-003");

            mockMvc.perform(post("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON).content(request));
            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isInternalServerError());

            assertThat(balance()).isEqualByComparingTo(before);
        }
    }

    @Nested
    @SpringBootTest(properties = "payment.reconcile.enabled=false")
    @AutoConfigureMockMvc
    @Transactional
    @DisplayName("장애 주입이 꺼진 상태")
    class WhenChaosDisabled extends ChaosTestBase {

        @Test
        @DisplayName("접두어 없는 결제는 정상 승인되고 잔액이 차감된다")
        void completesNormally() throws Exception {
            BigDecimal before = balance();

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON).content(body("CHAOS-OFF-001")))
                    .andExpect(status().isOk());

            Payment saved = ledgerOf("CHAOS-OFF-001");
            assertThat(saved.status()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(saved.externalResponseCode()).isEqualTo("0000");
            assertThat(balance()).isEqualByComparingTo(before.subtract(new BigDecimal(AMOUNT)));
        }
    }
}
