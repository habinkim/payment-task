package com.switchwon.payment.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.payment.infra.PaymentLedgerStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "payment.reconcile.enabled=true",
        "payment.reconcile.batch-size=3",
        "payment.reconcile.fixed-delay=1h"
})
@AutoConfigureMockMvc
class ReconcileSchedulerIT {

    private static final Long RICH_WALLET = 1L;

    @Autowired
    private ReconcileScheduler scheduler;

    @Autowired
    private PaymentLedgerStore ledgerStore;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private List<ScheduledTaskHolder> taskHolders;

    private void pay(String paymentNo) throws Exception {
        String body = objectMapper.writeValueAsString(
                new PaymentRequest(paymentNo, RICH_WALLET, new BigDecimal("1.0000"), "USD"));
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long unknownCount() {
        return ledgerStore.findOldestUnknown(100).size();
    }

    @Test
    @DisplayName("결과 미상 건을 집어 확정한다")
    void reconcilesUnknownPayments() throws Exception {
        String paymentNo = "TIMEOUT-SCH-" + System.nanoTime();
        pay(paymentNo);
        assertThat(ledgerStore.findByPaymentNo(paymentNo).orElseThrow().status())
                .isEqualTo(PaymentStatus.UNKNOWN);

        scheduler.reconcilePending();

        assertThat(ledgerStore.findByPaymentNo(paymentNo).orElseThrow().status())
                .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("한 번에 배치 크기만큼만 처리한다")
    void respectsBatchSize() throws Exception {
        for (int i = 0; i < 5; i++) {
            pay("TIMEOUT-BATCH-" + System.nanoTime() + "-" + i);
        }
        long before = unknownCount();
        assertThat(before).isGreaterThanOrEqualTo(5);

        scheduler.reconcilePending();

        assertThat(unknownCount()).isEqualTo(before - 3);
    }

    @Test
    @DisplayName("확정할 대상이 없으면 아무 일도 하지 않는다")
    void doesNothingWhenNoTargets() {
        while (unknownCount() > 0) {
            scheduler.reconcilePending();
        }

        scheduler.reconcilePending();

        assertThat(unknownCount()).isZero();
    }

    @Test
    @DisplayName("정합성 확인이 스케줄에 등록되어 있다")
    void schedulerIsRegistered() {
        boolean registered = taskHolders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(task -> task.getTask().toString())
                .anyMatch(description -> description.contains("reconcilePending"));

        assertThat(registered).isTrue();
    }
}
