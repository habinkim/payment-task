package com.switchwon.payment.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.payment.domain.PaymentLedgerStore;
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

@SpringBootTest(properties = "payment.reconcile.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ReconcileApiIT {

    private static final Long RICH_WALLET = 1L;
    private static final String AMOUNT = "100.0000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentLedgerStore ledgerStore;

    @Autowired
    private WalletStore walletStore;

    private void pay(String paymentNo) throws Exception {
        String body = objectMapper.writeValueAsString(
                new PaymentRequest(paymentNo, RICH_WALLET, new BigDecimal(AMOUNT), "USD"));
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private BigDecimal balance() {
        return walletStore.findById(RICH_WALLET).orElseThrow().balance();
    }

    private Payment ledgerOf(String paymentNo) {
        return ledgerStore.findByPaymentNo(paymentNo).orElseThrow();
    }

    @Test
    @DisplayName("결과 미상 건을 확정하면 승인으로 바뀌고 그때 잔액이 차감된다")
    void confirmsUnknownAndDeducts() throws Exception {
        pay("TIMEOUT-RC-001");
        assertThat(ledgerOf("TIMEOUT-RC-001").status()).isEqualTo(PaymentStatus.UNKNOWN);
        BigDecimal beforeReconcile = balance();

        mockMvc.perform(post("/api/v1/admin/payments/TIMEOUT-RC-001/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.status").value("COMPLETED"))
                .andExpect(jsonPath("$.returnObject.externalTransactionId").exists());

        assertThat(balance()).isEqualByComparingTo(beforeReconcile.subtract(new BigDecimal(AMOUNT)));
    }

    @Test
    @DisplayName("확정 전에는 잔액이 차감되지 않는다")
    void doesNotDeductBeforeReconcile() throws Exception {
        BigDecimal before = balance();

        pay("TIMEOUT-RC-002");

        assertThat(ledgerOf("TIMEOUT-RC-002").status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(balance()).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("완료된 결제에 확정을 호출해도 상태와 잔액이 변하지 않는다")
    void completedPaymentIsUntouched() throws Exception {
        pay("PAY-RC-003");
        BigDecimal afterPay = balance();

        mockMvc.perform(post("/api/v1/admin/payments/PAY-RC-003/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.status").value("COMPLETED"));

        assertThat(balance()).isEqualByComparingTo(afterPay);
    }

    @Test
    @DisplayName("실패한 결제에 확정을 호출해도 상태가 변하지 않는다")
    void failedPaymentIsUntouched() throws Exception {
        pay("DECLINE-RC-004");
        BigDecimal afterPay = balance();

        mockMvc.perform(post("/api/v1/admin/payments/DECLINE-RC-004/reconcile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.status").value("FAILED"))
                .andExpect(jsonPath("$.returnObject.failureReason").value("PAYMENT_DECLINED"));

        assertThat(balance()).isEqualByComparingTo(afterPay);
    }

    @Test
    @DisplayName("없는 결제번호를 확정하려 하면 찾을 수 없다고 응답한다")
    void missingPaymentReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/payments/NOPE-999/reconcile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("확정한 뒤에는 결과 미상 목록에서 빠진다")
    void confirmedPaymentLeavesUnknownList() throws Exception {
        pay("TIMEOUT-RC-005");
        assertThat(ledgerStore.findOldestUnknown(100))
                .extracting(Payment::paymentNo).contains("TIMEOUT-RC-005");

        mockMvc.perform(post("/api/v1/admin/payments/TIMEOUT-RC-005/reconcile"));

        assertThat(ledgerStore.findOldestUnknown(100))
                .extracting(Payment::paymentNo).doesNotContain("TIMEOUT-RC-005");
    }
}
