package com.switchwon.payment.payment.infra;

import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class JpaPaymentLedgerStoreIT {

    @Autowired
    private JpaPaymentLedgerStore store;

    @Autowired
    private EntityManager em;

    private Payment newPayment(String merchantPaymentNo) {
        return new Payment(merchantPaymentNo, 1L, new BigDecimal("100.0000"), "USD");
    }

    @Test
    @DisplayName("원장을 저장하면 결제번호로 다시 찾을 수 있다")
    void appendAndFind() {
        store.append(newPayment("PAY-001"));
        em.flush();
        em.clear();

        Payment found = store.findByMerchantPaymentNo("PAY-001").orElseThrow();

        assertThat(found.merchantPaymentNo()).isEqualTo("PAY-001");
        assertThat(found.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(found.amount()).isEqualByComparingTo("100.0000");
        assertThat(found.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("같은 결제번호를 두 번 저장하면 유일 제약에 걸린다")
    void rejectsDuplicateMerchantPaymentNo() {
        store.append(newPayment("PAY-DUP"));
        em.flush();

        assertThatThrownBy(() -> {
            store.append(newPayment("PAY-DUP"));
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("확정된 상태가 원장에 반영된다")
    void updateStatePersistsTransition() {
        Payment payment = newPayment("PAY-002");
        store.append(payment);
        em.flush();

        payment.complete("TXN-1", "0000", Instant.parse("2026-08-11T00:00:00Z"));
        store.updateState(payment);
        em.flush();
        em.clear();

        Payment found = store.findByMerchantPaymentNo("PAY-002").orElseThrow();
        assertThat(found.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(found.externalTransactionId()).isEqualTo("TXN-1");
    }

    @Test
    @DisplayName("실패 사유와 재시도 가능 여부가 원장에 남는다")
    void updateStatePersistsFailure() {
        Payment payment = newPayment("PAY-003");
        store.append(payment);
        em.flush();

        payment.fail(FailureReason.SYSTEM_ERROR, true, "500", Instant.parse("2026-08-11T00:00:00Z"));
        store.updateState(payment);
        em.flush();
        em.clear();

        Payment found = store.findByMerchantPaymentNo("PAY-003").orElseThrow();
        assertThat(found.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(found.failureReason()).isEqualTo(FailureReason.SYSTEM_ERROR);
        assertThat(found.retriable()).isTrue();
        assertThat(found.externalResponseCode()).isEqualTo("500");
    }

    @Test
    @DisplayName("복원한 결제는 저장 당시의 상태에서 다시 전이할 수 있다")
    void restoredPaymentCanTransition() {
        Payment payment = newPayment("PAY-004");
        store.append(payment);
        payment.markUnknown("TIMEOUT", Instant.parse("2026-08-11T00:00:00Z"));
        store.updateState(payment);
        em.flush();
        em.clear();

        Payment restored = store.findByMerchantPaymentNo("PAY-004").orElseThrow();
        assertThat(restored.status()).isEqualTo(PaymentStatus.UNKNOWN);

        restored.complete("TXN-9", "0000", Instant.parse("2026-08-11T00:01:00Z"));
        assertThat(restored.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("없는 결제번호를 조회하면 비어 있다")
    void findMissingReturnsEmpty() {
        assertThat(store.findByMerchantPaymentNo("NOPE")).isEmpty();
    }
}
