package com.switchwon.payment.gateway;

import java.util.Arrays;

public enum GatewayScenario {

    TIMEOUT("TIMEOUT-"),
    SERVER_ERROR("ERR500-"),
    BAD_REQUEST("ERR400-"),
    DECLINED("DECLINE-"),
    SLOW("SLOW-"),
    APPROVED("");

    private final String prefix;

    GatewayScenario(String prefix) {
        this.prefix = prefix;
    }

    public static GatewayScenario from(String paymentNo) {
        if (paymentNo == null) {
            return APPROVED;
        }
        return Arrays.stream(values())
                .filter(scenario -> !scenario.prefix.isEmpty())
                .filter(scenario -> paymentNo.startsWith(scenario.prefix))
                .findFirst()
                .orElse(APPROVED);
    }

    public String prefix() {
        return prefix;
    }
}
