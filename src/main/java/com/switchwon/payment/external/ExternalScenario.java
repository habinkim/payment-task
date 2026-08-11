package com.switchwon.payment.external;

import java.util.Arrays;

public enum ExternalScenario {

    TIMEOUT("TIMEOUT-"),
    SERVER_ERROR("ERR500-"),
    BAD_REQUEST("ERR400-"),
    DECLINED("DECLINE-"),
    SLOW("SLOW-"),
    APPROVED("");

    private final String prefix;

    ExternalScenario(String prefix) {
        this.prefix = prefix;
    }

    public static ExternalScenario from(String paymentNo) {
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
