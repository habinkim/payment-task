package com.switchwon.payment.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayScenarioTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("결제번호 접두어로 시나리오를 판정한다")
    @CsvSource({
            "TIMEOUT-20260811-001, TIMEOUT",
            "ERR500-20260811-001,  SERVER_ERROR",
            "ERR400-20260811-001,  BAD_REQUEST",
            "DECLINE-20260811-001, DECLINED",
            "SLOW-20260811-001,    SLOW",
            "PAY-20260811-001,     APPROVED"
    })
    void resolvesByPrefix(String paymentNo, GatewayScenario expected) {
        assertThat(GatewayScenario.from(paymentNo)).isEqualTo(expected);
    }

    @Test
    @DisplayName("접두어가 없으면 정상 승인으로 판정한다")
    void defaultsToApproved() {
        assertThat(GatewayScenario.from("ORDER-1")).isEqualTo(GatewayScenario.APPROVED);
    }

    @Test
    @DisplayName("결제번호가 없으면 정상 승인으로 판정한다")
    void nullPaymentNoIsApproved() {
        assertThat(GatewayScenario.from(null)).isEqualTo(GatewayScenario.APPROVED);
    }

    @Test
    @DisplayName("접두어가 중간에 있으면 시나리오로 보지 않는다")
    void prefixMustBeAtStart() {
        assertThat(GatewayScenario.from("PAY-TIMEOUT-001")).isEqualTo(GatewayScenario.APPROVED);
    }
}
