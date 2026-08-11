package com.switchwon.payment.wallet.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletChargeTest {

    private static final Long WALLET_ID = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.0000");

    private WalletCharge charge(String chargeNo) {
        return new WalletCharge(chargeNo, WALLET_ID, AMOUNT, "USD");
    }

    @Test
    @DisplayName("충전 이력은 요청 값을 그대로 보관한다")
    void keepsRequestedValues() {
        WalletCharge charge = charge("CHG-001");

        assertThat(charge.chargeNo()).isEqualTo("CHG-001");
        assertThat(charge.walletId()).isEqualTo(WALLET_ID);
        assertThat(charge.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(charge.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("새로 만든 이력은 아직 기록 시각을 갖지 않는다")
    void hasNoCreatedAtBeforePersisting() {
        assertThat(charge("CHG-002").createdAt()).isNull();
    }

    @Test
    @DisplayName("저장된 이력을 복원하면 기록 시각이 함께 살아난다")
    void restoreKeepsCreatedAt() {
        Instant createdAt = Instant.parse("2026-08-12T00:00:00Z");

        WalletCharge restored = WalletCharge.restore("CHG-003", WALLET_ID, AMOUNT, "USD", createdAt);

        assertThat(restored.createdAt()).isEqualTo(createdAt);
        assertThat(restored.chargeNo()).isEqualTo("CHG-003");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "CHG_001", "충전-001", "CHG 001"})
    @DisplayName("충전번호가 영숫자와 하이픈 형식이 아니면 만들 수 없다")
    void rejectsMalformedChargeNo(String chargeNo) {
        assertThatThrownBy(() -> charge(chargeNo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chargeNo");
    }

    @Test
    @DisplayName("충전번호가 64자를 넘으면 만들 수 없다")
    void rejectsTooLongChargeNo() {
        assertThatThrownBy(() -> charge("C".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지갑이 없으면 만들 수 없다")
    void rejectsMissingWallet() {
        assertThatThrownBy(() -> new WalletCharge("CHG-004", null, AMOUNT, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("walletId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.0001"})
    @DisplayName("충전 금액이 0 이하면 만들 수 없다")
    void rejectsNonPositiveAmount(String amount) {
        assertThatThrownBy(() -> new WalletCharge("CHG-005", WALLET_ID, new BigDecimal(amount), "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"US", "USDD", ""})
    @DisplayName("통화가 세 글자가 아니면 만들 수 없다")
    void rejectsMalformedCurrency(String currency) {
        assertThatThrownBy(() -> new WalletCharge("CHG-006", WALLET_ID, AMOUNT, currency))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }
}
