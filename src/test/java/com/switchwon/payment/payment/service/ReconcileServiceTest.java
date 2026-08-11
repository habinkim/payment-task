package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.external.ExternalInquiry;
import com.switchwon.payment.external.ExternalInquiryResult;
import com.switchwon.payment.external.ExternalPaymentClient;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReconcileServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final String PAYMENT_NO = "TIMEOUT-001";

    @Mock
    private PaymentTransactionService transaction;

    @Mock
    private ExternalPaymentClient gateway;

    @InjectMocks
    private ReconcileService service;

    private Payment payment(String paymentNo) {
        return new Payment(paymentNo, 1L, new BigDecimal("100"), "USD");
    }

    private Payment unknownPayment() {
        Payment payment = payment(PAYMENT_NO);
        payment.markUnknown("TIMEOUT", NOW);
        return payment;
    }

    @Test
    @DisplayName("결과 미상 건을 조회해 확정한다")
    void confirmsUnknownPayment() {
        Payment target = unknownPayment();
        ExternalInquiry inquiry = ExternalInquiry.approved("TXN-1", "0000");
        given(transaction.findExisting(PAYMENT_NO)).willReturn(Optional.of(target));
        given(gateway.inquire(PAYMENT_NO)).willReturn(inquiry);
        given(transaction.confirm(target, inquiry)).willReturn(target);

        service.reconcile(PAYMENT_NO);

        verify(gateway).inquire(PAYMENT_NO);
        verify(transaction).confirm(target, inquiry);
    }

    @Test
    @DisplayName("완료된 결제는 게이트웨이를 호출하지 않는다")
    void completedPaymentSkipsGateway() {
        Payment completed = payment("PAY-001");
        completed.complete("TXN-9", "0000", NOW);
        given(transaction.findExisting("PAY-001")).willReturn(Optional.of(completed));

        Payment result = service.reconcile("PAY-001");

        assertThat(result).isSameAs(completed);
        verify(gateway, never()).inquire(anyString());
        verify(transaction, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("실패한 결제는 게이트웨이를 호출하지 않는다")
    void failedPaymentSkipsGateway() {
        Payment failed = payment("PAY-002");
        failed.fail(FailureReason.PAYMENT_DECLINED, false, "DECLINED", NOW);
        given(transaction.findExisting("PAY-002")).willReturn(Optional.of(failed));

        service.reconcile("PAY-002");

        verify(gateway, never()).inquire(anyString());
    }

    @Test
    @DisplayName("아직 처리 중인 결제는 게이트웨이를 호출하지 않는다")
    void pendingPaymentSkipsGateway() {
        given(transaction.findExisting("PAY-003")).willReturn(Optional.of(payment("PAY-003")));

        service.reconcile("PAY-003");

        verify(gateway, never()).inquire(anyString());
    }

    @Test
    @DisplayName("없는 결제번호를 확정하려 하면 찾을 수 없다고 응답한다")
    void missingPaymentThrows() {
        given(transaction.findExisting("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconcile("NOPE"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("게이트웨이 조회가 실패하면 여전히 결과 미상으로 다룬다")
    void gatewayExceptionStaysUnknown() {
        Payment target = unknownPayment();
        given(transaction.findExisting(PAYMENT_NO)).willReturn(Optional.of(target));
        given(gateway.inquire(PAYMENT_NO)).willThrow(new RuntimeException("connection reset"));
        given(transaction.confirm(any(), any())).willReturn(target);

        service.reconcile(PAYMENT_NO);

        verify(transaction).confirm(
                org.mockito.ArgumentMatchers.eq(target),
                org.mockito.ArgumentMatchers.argThat(i -> i.result() == ExternalInquiryResult.STILL_UNKNOWN));
    }

    @Test
    @DisplayName("확인 대상은 오래된 결과 미상 건부터 가져온다")
    void findsOldestTargets() {
        service.findTargets(10);

        verify(transaction).findOldestUnknown(10);
    }
}
