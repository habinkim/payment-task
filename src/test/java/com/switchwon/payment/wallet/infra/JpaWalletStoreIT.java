package com.switchwon.payment.wallet.infra;

import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.payment.domain.PaymentLedgerStore;
import com.switchwon.payment.wallet.domain.Wallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class JpaWalletStoreIT {

    @Autowired
    private JpaWalletStore store;

    @Autowired
    private PaymentLedgerStore ledgerStore;

    private static final Long RICH_WALLET = 1L;

    private static final Long POOR_WALLET = 2L;

    @Test
    @DisplayName("잔액이 충분하면 차감되고 정확한 금액이 빠진다")
    void deductWhenEnough() {
        BigDecimal before = store.findById(RICH_WALLET).orElseThrow().balance();

        boolean deducted = store.deductIfEnough(RICH_WALLET, new BigDecimal("100.0000"));

        assertThat(deducted).isTrue();
        assertThat(store.findById(RICH_WALLET).orElseThrow().balance())
                .isEqualByComparingTo(before.subtract(new BigDecimal("100.0000")));
    }

    @Test
    @DisplayName("잔액이 부족하면 차감되지 않고 잔액이 그대로다")
    void doesNotDeductWhenInsufficient() {
        BigDecimal before = store.findById(POOR_WALLET).orElseThrow().balance();

        boolean deducted = store.deductIfEnough(POOR_WALLET, new BigDecimal("1000.0000"));

        assertThat(deducted).isFalse();
        assertThat(store.findById(POOR_WALLET).orElseThrow().balance()).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("잔액과 같은 금액은 차감할 수 있고 잔액이 0이 된다")
    void deductExactBalance() {
        BigDecimal balance = store.findById(POOR_WALLET).orElseThrow().balance();

        boolean deducted = store.deductIfEnough(POOR_WALLET, balance);

        assertThat(deducted).isTrue();
        assertThat(store.findById(POOR_WALLET).orElseThrow().balance()).isZero();
    }

    @Test
    @DisplayName("충전하면 잔액이 늘어난다")
    void chargeIncreasesBalance() {
        BigDecimal before = store.findById(RICH_WALLET).orElseThrow().balance();

        boolean charged = store.charge(RICH_WALLET, new BigDecimal("50.0000"));

        assertThat(charged).isTrue();
        assertThat(store.findById(RICH_WALLET).orElseThrow().balance())
                .isEqualByComparingTo(before.add(new BigDecimal("50.0000")));
    }

    @Test
    @DisplayName("없는 지갑을 차감하면 실패로 판정된다")
    void deductMissingWalletFails() {
        assertThat(store.deductIfEnough(9999L, BigDecimal.ONE)).isFalse();
    }

    @Test
    @DisplayName("지갑을 조회하면 도메인 객체로 돌아온다")
    void findReturnsDomain() {
        Wallet wallet = store.findById(RICH_WALLET).orElseThrow();

        assertThat(wallet.id()).isEqualTo(RICH_WALLET);
        assertThat(wallet.currency()).isEqualTo("USD");
        assertThat(wallet.balance()).isPositive();
    }

    @Test
    @DisplayName("차감 이후에도 같은 트랜잭션에서 원장을 갱신할 수 있다")
    void ledgerUpdateSurvivesContextClear() {
        Payment payment = new Payment("PAY-CLEAR", RICH_WALLET, new BigDecimal("10.0000"), "USD");
        ledgerStore.append(payment);

        boolean deducted = store.deductIfEnough(RICH_WALLET, new BigDecimal("10.0000"));
        assertThat(deducted).isTrue();

        payment.complete("TXN-CLEAR", "0000", Instant.parse("2026-08-11T00:00:00Z"));
        ledgerStore.updateState(payment);

        Payment found = ledgerStore.findByPaymentNo("PAY-CLEAR").orElseThrow();
        assertThat(found.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(found.externalTransactionId()).isEqualTo("TXN-CLEAR");
    }
}
