package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.external.ExternalApproval;
import com.switchwon.payment.external.ExternalApprovalRequest;
import com.switchwon.payment.external.ExternalPaymentClient;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.wallet.domain.Wallet;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final Long WALLET_ID = 1L;

    @Mock
    private PaymentTransactionService transaction;

    @Mock
    private ExternalPaymentClient gateway;

    @Spy
    private PaymentMetrics metrics = new PaymentMetrics(new SimpleMeterRegistry());

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(transaction, gateway, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PaymentCommand command(String paymentNo, String amount) {
        return new PaymentCommand(paymentNo, WALLET_ID, new BigDecimal(amount), "USD");
    }

    private Payment pending(String paymentNo, String amount) {
        return new Payment(paymentNo, WALLET_ID, new BigDecimal(amount), "USD");
    }

    private void givenWallet(String balance) {
        given(transaction.loadWallet(WALLET_ID)).willReturn(new Wallet(WALLET_ID, "USD", new BigDecimal(balance)));
    }

    @Test
    @DisplayName("잔액이 부족하면 게이트웨이를 호출하지 않는다")
    void insufficientBalanceSkipsGateway() {
        Payment payment = pending("PAY-001", "500");
        given(transaction.findExisting("PAY-001")).willReturn(java.util.Optional.empty());
        givenWallet("100");
        given(transaction.openPending(anyString(), anyLong(), any(), anyString())).willReturn(payment);
        given(transaction.rejectWithoutGateway(payment, FailureReason.INSUFFICIENT_BALANCE)).willReturn(payment);

        service.pay(command("PAY-001", "500"));

        verify(gateway, never()).approve(any());
        verify(transaction).rejectWithoutGateway(payment, FailureReason.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("통화가 지갑과 다르면 원장도 만들지 않고 거부한다")
    void currencyMismatchRejectsEarly() {
        given(transaction.findExisting("PAY-002")).willReturn(java.util.Optional.empty());
        given(transaction.loadWallet(WALLET_ID)).willReturn(new Wallet(WALLET_ID, "JPY", new BigDecimal("100000")));

        assertThatThrownBy(() -> service.pay(command("PAY-002", "100")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.INVALID_REQUEST);

        verify(gateway, never()).approve(any());
        verify(transaction, never()).openPending(anyString(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("잔액이 충분하면 게이트웨이를 호출하고 결과를 확정한다")
    void sufficientBalanceCallsGateway() {
        Payment payment = pending("PAY-003", "100");
        given(transaction.findExisting("PAY-003")).willReturn(java.util.Optional.empty());
        givenWallet("1000");
        given(transaction.openPending(anyString(), anyLong(), any(), anyString())).willReturn(payment);
        ExternalApproval approval = ExternalApproval.approved("TXN-1", "0000");
        given(gateway.approve(any(ExternalApprovalRequest.class))).willReturn(approval);
        given(transaction.settle(payment, approval)).willReturn(payment);

        service.pay(command("PAY-003", "100"));

        verify(gateway).approve(any(ExternalApprovalRequest.class));
        verify(transaction).settle(payment, approval);
    }

    @Test
    @DisplayName("게이트웨이 호출 직전에 요청 시각을 기록한다")
    void marksRequestedTime() {
        Payment payment = pending("PAY-004", "100");
        given(transaction.findExisting("PAY-004")).willReturn(java.util.Optional.empty());
        givenWallet("1000");
        given(transaction.openPending(anyString(), anyLong(), any(), anyString())).willReturn(payment);
        given(gateway.approve(any())).willReturn(ExternalApproval.approved("TXN-1", "0000"));
        given(transaction.settle(any(), any())).willReturn(payment);

        service.pay(command("PAY-004", "100"));

        assertThat(payment.requestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("게이트웨이가 예외를 던지면 결과 미상으로 확정한다")
    void gatewayExceptionBecomesInDoubt() {
        Payment payment = pending("PAY-005", "100");
        given(transaction.findExisting("PAY-005")).willReturn(java.util.Optional.empty());
        givenWallet("1000");
        given(transaction.openPending(anyString(), anyLong(), any(), anyString())).willReturn(payment);
        given(gateway.approve(any())).willThrow(new RuntimeException("connection reset"));
        given(transaction.settle(any(), any())).willReturn(payment);

        service.pay(command("PAY-005", "100"));

        verify(transaction).settle(org.mockito.ArgumentMatchers.eq(payment),
                org.mockito.ArgumentMatchers.argThat(
                        a -> a.result() == com.switchwon.payment.external.ExternalApprovalResult.IN_DOUBT));
    }

    @Test
    @DisplayName("이미 종료된 결제를 다시 요청하면 최초 결과를 그대로 반환한다")
    void duplicateTerminalReturnsOriginal() {
        Payment completed = pending("PAY-006", "100");
        completed.complete("TXN-9", "0000", NOW);
        given(transaction.findExisting("PAY-006")).willReturn(java.util.Optional.of(completed));

        Payment result = service.pay(command("PAY-006", "100"));

        assertThat(result.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.externalTransactionId()).isEqualTo("TXN-9");
        verify(gateway, never()).approve(any());
    }

    @Test
    @DisplayName("처리 중인 결제를 다시 요청하면 중복으로 거부한다")
    void duplicateInProgressRejects() {
        given(transaction.findExisting("PAY-007")).willReturn(java.util.Optional.of(pending("PAY-007", "100")));

        assertThatThrownBy(() -> service.pay(command("PAY-007", "100")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.DUPLICATE_PAYMENT_NO);

        verify(gateway, never()).approve(any());
    }
}
