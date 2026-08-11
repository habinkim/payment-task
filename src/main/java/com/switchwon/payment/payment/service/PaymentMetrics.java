package com.switchwon.payment.payment.service;

import com.switchwon.payment.payment.domain.Payment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private static final Logger log = LoggerFactory.getLogger(PaymentMetrics.class);

    private static final String RESULT = "payment.result";
    private static final String ORPHAN = "payment.orphan.total";
    private static final String GATEWAY_DURATION = "gateway.approve.duration";
    private static final String RECONCILE = "payment.reconcile";
    private static final String NONE = "none";

    private final MeterRegistry registry;

    public void recordResult(Payment payment) {
        registry.counter(RESULT,
                        "status", payment.status().name(),
                        "reason", payment.failureReason() == null ? NONE : payment.failureReason().name())
                .increment();
    }

    public void recordOrphan(Payment payment) {
        registry.counter(ORPHAN).increment();
        log.warn("외부 승인 후 잔액 차감에 실패했습니다. paymentNo={}, walletId={}, externalTransactionId={}",
                payment.paymentNo(), payment.walletId(), payment.externalTransactionId());
    }

    public void recordReconcile(String result) {
        registry.counter(RECONCILE, "result", result).increment();
    }

    public Timer.Sample startGatewayTimer() {
        return Timer.start(registry);
    }

    public void stopGatewayTimer(Timer.Sample sample, String result) {
        sample.stop(registry.timer(GATEWAY_DURATION, "result", result));
    }
}
