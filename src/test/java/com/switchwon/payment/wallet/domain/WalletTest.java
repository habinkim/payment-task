package com.switchwon.payment.wallet.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private Wallet walletWith(String balance) {
        return new Wallet(1L, "USD", new BigDecimal(balance));
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("통화가 세 글자가 아니면 생성할 수 없다")
        void rejectsInvalidCurrencyLength() {
            assertThatThrownBy(() -> new Wallet(1L, "USDD", BigDecimal.TEN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("잔액이 음수면 생성할 수 없다")
        void rejectsNegativeBalance() {
            assertThatThrownBy(() -> new Wallet(1L, "USD", new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("잔액이 0인 지갑은 생성할 수 있다")
        void allowsZeroBalance() {
            assertThat(new Wallet(1L, "USD", BigDecimal.ZERO).balance()).isZero();
        }
    }

    @Nested
    @DisplayName("잔액 판정")
    class Affordability {

        @Test
        @DisplayName("잔액이 결제 금액보다 많으면 결제할 수 있다")
        void affordsWhenBalanceIsGreater() {
            assertThat(walletWith("100").canAfford(new BigDecimal("80"))).isTrue();
        }

        @Test
        @DisplayName("잔액과 결제 금액이 같으면 결제할 수 있다")
        void affordsWhenBalanceEquals() {
            assertThat(walletWith("100").canAfford(new BigDecimal("100"))).isTrue();
        }

        @Test
        @DisplayName("잔액이 결제 금액보다 적으면 결제할 수 없다")
        void doesNotAffordWhenBalanceIsLess() {
            assertThat(walletWith("100").canAfford(new BigDecimal("100.0001"))).isFalse();
        }

        @Test
        @DisplayName("소수점 이하 자릿수가 달라도 금액을 정확히 비교한다")
        void comparesScaleIndependently() {
            assertThat(walletWith("100.0000").canAfford(new BigDecimal("100"))).isTrue();
        }
    }

    @Nested
    @DisplayName("차감")
    class Withdrawal {

        @Test
        @DisplayName("차감하면 잔액이 정확히 줄어든다")
        void withdrawReducesBalance() {
            Wallet wallet = walletWith("100.0000");

            wallet.withdraw(new BigDecimal("30.5000"));

            assertThat(wallet.balance()).isEqualByComparingTo("69.5000");
        }

        @Test
        @DisplayName("잔액 전액을 차감하면 0이 된다")
        void withdrawAllLeavesZero() {
            Wallet wallet = walletWith("100");

            wallet.withdraw(new BigDecimal("100"));

            assertThat(wallet.balance()).isZero();
        }

        @Test
        @DisplayName("잔액보다 많이 차감하면 예외가 발생한다")
        void rejectsOverdraft() {
            Wallet wallet = walletWith("100");

            assertThatThrownBy(() -> wallet.withdraw(new BigDecimal("101")))
                    .isInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("차감에 실패하면 잔액이 변하지 않는다")
        void keepsBalanceWhenWithdrawFails() {
            Wallet wallet = walletWith("100");

            assertThatThrownBy(() -> wallet.withdraw(new BigDecimal("101")))
                    .isInstanceOf(InsufficientBalanceException.class);

            assertThat(wallet.balance()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("차감 금액이 0이면 예외가 발생한다")
        void rejectsZeroWithdrawal() {
            assertThatThrownBy(() -> walletWith("100").withdraw(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("충전")
    class Charge {

        @Test
        @DisplayName("충전하면 잔액이 정확히 늘어난다")
        void chargeIncreasesBalance() {
            Wallet wallet = walletWith("100.0000");

            wallet.charge(new BigDecimal("0.0001"));

            assertThat(wallet.balance()).isEqualByComparingTo("100.0001");
        }

        @Test
        @DisplayName("충전 금액이 0이면 예외가 발생한다")
        void rejectsZeroCharge() {
            assertThatThrownBy(() -> walletWith("100").charge(BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("충전 금액이 음수면 예외가 발생한다")
        void rejectsNegativeCharge() {
            assertThatThrownBy(() -> walletWith("100").charge(new BigDecimal("-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("통화")
    class Currency {

        @Test
        @DisplayName("지갑 통화와 같으면 처리할 수 있다")
        void supportsSameCurrency() {
            assertThat(walletWith("100").supports("USD")).isTrue();
        }

        @Test
        @DisplayName("지갑 통화와 다르면 처리할 수 없다")
        void rejectsDifferentCurrency() {
            assertThat(walletWith("100").supports("JPY")).isFalse();
        }
    }
}
