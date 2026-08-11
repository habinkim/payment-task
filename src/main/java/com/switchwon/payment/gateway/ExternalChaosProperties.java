package com.switchwon.payment.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.gateway.chaos")
public record ExternalChaosProperties(boolean enabled, double timeoutRate, double failureRate) {

    public ExternalChaosProperties {
        if (timeoutRate < 0 || failureRate < 0) {
            throw new IllegalArgumentException(
                    "장애 주입 확률은 음수일 수 없습니다: timeoutRate=" + timeoutRate + ", failureRate=" + failureRate);
        }
        if (timeoutRate + failureRate > 1.0) {
            throw new IllegalArgumentException(
                    "장애 주입 확률의 합이 1을 넘으면 정상 승인이 발생할 수 없습니다: "
                            + timeoutRate + " + " + failureRate + " = " + (timeoutRate + failureRate));
        }
    }

    public static ExternalChaosProperties disabled() {
        return new ExternalChaosProperties(false, 0.0, 0.0);
    }

    public double failureThreshold() {
        return timeoutRate + failureRate;
    }
}
