package com.switchwon.payment.payment.service;

import com.switchwon.payment.gateway.ExternalApproval;
import com.switchwon.payment.gateway.ExternalInquiry;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentLedgerStore;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

@SpringBootTest
class SettleAtomicityIT {

    private static final Long RICH_WALLET = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("50.0000");

    @Autowired
    private PaymentTransactionService transaction;

    @Autowired
    private WalletStore walletStore;

    @MockitoSpyBean
    private PaymentLedgerStore ledgerStore;

    @MockitoSpyBean
    private PaymentMetrics metrics;

    private BigDecimal balance() {
        return walletStore.findById(RICH_WALLET).orElseThrow().balance();
    }

    private Payment pending(String prefix) {
        return transaction.openPending(prefix + System.nanoTime(), RICH_WALLET, AMOUNT, "USD");
    }

    private Payment unknown(String prefix) {
        Payment payment = pending(prefix);
        return transaction.settle(payment, ExternalApproval.inDoubt("TIMEOUT"));
    }

    @Test
    @DisplayName("승인 확정 중 마지막 단계가 실패하면 차감과 원장 갱신이 함께 롤백된다")
    void settleRollsBackBothWrites() {
        Payment payment = pending("ATOMIC-");
        BigDecimal before = balance();

        willThrow(new RuntimeException("커밋 직전 폭발")).given(metrics).recordResult(any());

        assertThatThrownBy(() -> transaction.settle(payment, ExternalApproval.approved("TXN-A", "0000")))
                .isInstanceOf(RuntimeException.class);

        assertThat(balance()).as("차감이 롤백되어야 한다").isEqualByComparingTo(before);
        assertThat(transaction.findExisting(payment.paymentNo()).orElseThrow().status())
                .as("원장도 확정 전 상태로 남아야 한다")
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("차감과 원장 갱신 사이에서 실패해도 차감이 롤백된다")
    void settleRollsBackDeductionWhenLedgerFails() {
        Payment payment = pending("ATOMIC-MID-");
        BigDecimal before = balance();

        willThrow(new RuntimeException("두 쓰기 사이에서 폭발")).given(ledgerStore).updateState(any());

        assertThatThrownBy(() -> transaction.settle(payment, ExternalApproval.approved("TXN-B", "0000")))
                .isInstanceOf(RuntimeException.class);

        assertThat(balance())
                .as("차감이 먼저 실행됐더라도 롤백되어야 한다")
                .isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("결과 미상 확정 중 실패하면 차감과 원장 갱신이 함께 롤백된다")
    void confirmRollsBackBothWrites() {
        Payment payment = unknown("TIMEOUT-ATOMIC-");
        BigDecimal before = balance();

        willThrow(new RuntimeException("확정 직전 폭발")).given(metrics).recordResult(any());

        assertThatThrownBy(() -> transaction.confirm(payment, ExternalInquiry.approved("TXN-C", "0000")))
                .isInstanceOf(RuntimeException.class);

        assertThat(balance()).as("차감이 롤백되어야 한다").isEqualByComparingTo(before);
        assertThat(transaction.findExisting(payment.paymentNo()).orElseThrow().status())
                .as("원장은 결과 미상 그대로여야 한다")
                .isEqualTo(PaymentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("원장 기록은 확정과 다른 트랜잭션이라 확정이 실패해도 남는다")
    void openPendingCommitsIndependently() {
        Payment payment = pending("ATOMIC-WAL-");

        willThrow(new RuntimeException("확정 실패")).given(metrics).recordResult(any());

        assertThatThrownBy(() -> transaction.settle(payment, ExternalApproval.approved("TXN-D", "0000")))
                .isInstanceOf(RuntimeException.class);

        assertThat(transaction.findExisting(payment.paymentNo()))
                .as("확정이 실패해도 처리 중 원장은 남아 정합성 확인이 주워갈 수 있다")
                .isPresent();
    }
}
