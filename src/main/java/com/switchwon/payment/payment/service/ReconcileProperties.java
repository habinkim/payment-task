package com.switchwon.payment.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "payment.reconcile")
public record ReconcileProperties(boolean enabled, Duration fixedDelay, int batchSize) {

    public ReconcileProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("한 번에 처리할 건수는 1 이상이어야 합니다: " + batchSize);
        }
    }
}
