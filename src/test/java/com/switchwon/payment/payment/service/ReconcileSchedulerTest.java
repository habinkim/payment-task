package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.payment.domain.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReconcileSchedulerTest {

    private static final int BATCH_SIZE = 10;

    @Mock
    private ReconcileService reconcileService;

    private ReconcileScheduler scheduler(int batchSize) {
        return new ReconcileScheduler(reconcileService, new ReconcileProperties(true, null, batchSize));
    }

    private Payment payment(String merchantPaymentNo) {
        return new Payment(merchantPaymentNo, 1L, new BigDecimal("100"), "USD");
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 확정한다")
    void keepsGoingAfterOneFailure() {
        given(reconcileService.findTargets(BATCH_SIZE))
                .willReturn(List.of(payment("RC-1"), payment("RC-2"), payment("RC-3")));
        willThrow(new ApiException(ResponseCode.PAYMENT_NOT_FOUND))
                .given(reconcileService).reconcile("RC-2");

        scheduler(BATCH_SIZE).reconcilePending();

        verify(reconcileService).reconcile("RC-1");
        verify(reconcileService).reconcile("RC-2");
        verify(reconcileService).reconcile("RC-3");
    }

    @Test
    @DisplayName("모든 건이 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsEveryFailure() {
        given(reconcileService.findTargets(BATCH_SIZE))
                .willReturn(List.of(payment("RC-1"), payment("RC-2")));
        willThrow(new IllegalStateException("종료 상태에서는 전이할 수 없습니다"))
                .given(reconcileService).reconcile(org.mockito.ArgumentMatchers.anyString());

        assertThatCode(() -> scheduler(BATCH_SIZE).reconcilePending()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("확정할 대상이 없으면 확정을 시도하지 않는다")
    void skipsWhenNoTargets() {
        given(reconcileService.findTargets(BATCH_SIZE)).willReturn(List.of());

        scheduler(BATCH_SIZE).reconcilePending();

        verify(reconcileService, never()).reconcile(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("설정한 배치 크기로 대상을 가져온다")
    void usesConfiguredBatchSize() {
        given(reconcileService.findTargets(3)).willReturn(List.of());

        scheduler(3).reconcilePending();

        verify(reconcileService).findTargets(3);
    }
}
