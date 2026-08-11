package com.switchwon.payment.payment.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    private Payment newPayment() {
        return new Payment("PAY-001", 1L, new BigDecimal("100.0000"), "USD");
    }

    @Nested
    @DisplayName("생성")
    class Creation {

        @Test
        @DisplayName("생성 직후 상태는 PENDING이다")
        void startsAsPending() {
            assertThat(newPayment().status()).isEqualTo(PaymentStatus.PENDING);
        }

        @Test
        @DisplayName("결제번호가 없으면 생성할 수 없다")
        void rejectsNullMerchantPaymentNo() {
            assertThatThrownBy(() -> new Payment(null, 1L, BigDecimal.ONE, "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("결제번호가 64자를 넘으면 생성할 수 없다")
        void rejectsTooLongMerchantPaymentNo() {
            String tooLong = "A".repeat(65);

            assertThatThrownBy(() -> new Payment(tooLong, 1L, BigDecimal.ONE, "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("결제번호에 허용되지 않은 문자가 있으면 생성할 수 없다")
        void rejectsIllegalCharacterInMerchantPaymentNo() {
            assertThatThrownBy(() -> new Payment("PAY 001", 1L, BigDecimal.ONE, "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("지갑 식별자가 없으면 생성할 수 없다")
        void rejectsNullWalletId() {
            assertThatThrownBy(() -> new Payment("PAY-001", null, BigDecimal.ONE, "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("금액이 0이면 생성할 수 없다")
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> new Payment("PAY-001", 1L, BigDecimal.ZERO, "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("금액이 음수면 생성할 수 없다")
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> new Payment("PAY-001", 1L, new BigDecimal("-1"), "USD"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("통화가 세 글자가 아니면 생성할 수 없다")
        void rejectsInvalidCurrencyLength() {
            assertThatThrownBy(() -> new Payment("PAY-001", 1L, BigDecimal.ONE, "US"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transition {

        @Test
        @DisplayName("승인되면 COMPLETED가 되고 외부 거래번호가 기록된다")
        void completeRecordsExternalTransaction() {
            Payment payment = newPayment();

            payment.complete("TXN-1", "0000", NOW);

            assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.externalTransactionId()).isEqualTo("TXN-1");
            assertThat(payment.externalResponseCode()).isEqualTo("0000");
            assertThat(payment.respondedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("실패하면 사유와 재시도 가능 여부가 함께 기록된다")
        void failRecordsReasonAndRetriable() {
            Payment payment = newPayment();

            payment.fail(FailureReason.SYSTEM_ERROR, true, "500", NOW);

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason()).isEqualTo(FailureReason.SYSTEM_ERROR);
            assertThat(payment.retriable()).isTrue();
        }

        @Test
        @DisplayName("실패에는 사유가 반드시 있어야 한다")
        void failRequiresReason() {
            Payment payment = newPayment();

            assertThatThrownBy(() -> payment.fail(null, false, "500", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("결과를 알 수 없으면 UNKNOWN이 되고 재시도 가능으로 표시된다")
        void markUnknownIsRetriable() {
            Payment payment = newPayment();

            payment.markUnknown("TIMEOUT", NOW);

            assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
            assertThat(payment.retriable()).isTrue();
            assertThat(payment.failureReason()).isEqualTo(FailureReason.SYSTEM_ERROR);
        }

        @Test
        @DisplayName("결과 미상은 종료 상태가 아니라 승인으로 확정할 수 있다")
        void unknownCanBeConfirmedAsCompleted() {
            Payment payment = newPayment();
            payment.markUnknown("TIMEOUT", NOW);

            assertThatCode(() -> payment.complete("TXN-1", "0000", NOW))
                    .doesNotThrowAnyException();
            assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("결과 미상은 실패로도 확정할 수 있다")
        void unknownCanBeConfirmedAsFailed() {
            Payment payment = newPayment();
            payment.markUnknown("TIMEOUT", NOW);

            payment.fail(FailureReason.PAYMENT_DECLINED, false, "DECLINED", NOW);

            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("종료 상태 보호")
    class TerminalGuard {

        @Test
        @DisplayName("완료된 결제는 다시 실패시킬 수 없다")
        void completedCannotFail() {
            Payment payment = newPayment();
            payment.complete("TXN-1", "0000", NOW);

            assertThatThrownBy(() -> payment.fail(FailureReason.SYSTEM_ERROR, true, "500", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("완료된 결제는 다시 완료시킬 수 없다")
        void completedCannotCompleteAgain() {
            Payment payment = newPayment();
            payment.complete("TXN-1", "0000", NOW);

            assertThatThrownBy(() -> payment.complete("TXN-2", "0000", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패한 결제는 다시 완료시킬 수 없다")
        void failedCannotComplete() {
            Payment payment = newPayment();
            payment.fail(FailureReason.PAYMENT_DECLINED, false, "DECLINED", NOW);

            assertThatThrownBy(() -> payment.complete("TXN-1", "0000", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("완료된 결제는 결과 미상으로 되돌릴 수 없다")
        void completedCannotBecomeUnknown() {
            Payment payment = newPayment();
            payment.complete("TXN-1", "0000", NOW);

            assertThatThrownBy(() -> payment.markUnknown("TIMEOUT", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 결과 미상인 결제를 다시 결과 미상으로 만들 수 없다")
        void unknownCannotRepeat() {
            Payment payment = newPayment();
            payment.markUnknown("TIMEOUT", NOW);

            assertThatThrownBy(() -> payment.markUnknown("TIMEOUT", NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
