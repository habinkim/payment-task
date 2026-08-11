package com.switchwon.payment.payment.service;

import com.switchwon.payment.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@ConditionalOnProperty(name = "payment.reconcile.enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class ReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconcileScheduler.class);

    private final ReconcileService reconcileService;
    private final ReconcileProperties properties;

    @Scheduled(fixedDelayString = "${payment.reconcile.fixed-delay}")
    public void reconcilePending() {
        List<Payment> targets = reconcileService.findTargets(properties.batchSize());
        if (targets.isEmpty()) {
            return;
        }

        log.info("결과 미상 결제 {}건의 정합성을 확인합니다", targets.size());
        targets.forEach(this::reconcileQuietly);
    }

    private void reconcileQuietly(Payment payment) {
        try {
            reconcileService.reconcile(payment.paymentNo());
        } catch (RuntimeException e) {
            log.warn("정합성 확인에 실패했습니다. paymentNo={}", payment.paymentNo(), e);
        }
    }
}
