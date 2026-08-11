package com.switchwon.payment.gateway.infra;

import com.switchwon.payment.gateway.GatewayApproval;
import com.switchwon.payment.gateway.GatewayApprovalRequest;
import com.switchwon.payment.gateway.GatewayResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayClientTest {

    private final MockPaymentGatewayClient client = new MockPaymentGatewayClient();

    private GatewayApproval approve(String paymentNo) {
        return client.approve(new GatewayApprovalRequest(paymentNo, new BigDecimal("100"), "USD"));
    }

    @Test
    @DisplayName("접두어가 없으면 승인하고 외부 거래번호를 발급한다")
    void approvesWithTransactionId() {
        GatewayApproval approval = approve("PAY-001");

        assertThat(approval.result()).isEqualTo(GatewayResult.APPROVED);
        assertThat(approval.externalTransactionId()).startsWith("TXN-");
        assertThat(approval.externalResponseCode()).isEqualTo("0000");
    }

    @Test
    @DisplayName("승인 건마다 서로 다른 거래번호를 발급한다")
    void issuesDistinctTransactionIds() {
        String first = approve("PAY-001").externalTransactionId();
        String second = approve("PAY-002").externalTransactionId();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("타임아웃은 결과 미상으로 응답하고 거래번호를 남기지 않는다")
    void timeoutIsInDoubt() {
        GatewayApproval approval = approve("TIMEOUT-001");

        assertThat(approval.result()).isEqualTo(GatewayResult.IN_DOUBT);
        assertThat(approval.externalTransactionId()).isNull();
        assertThat(approval.externalResponseCode()).isEqualTo("TIMEOUT");
    }

    @Test
    @DisplayName("서버 오류는 실패로 응답한다")
    void serverErrorFails() {
        GatewayApproval approval = approve("ERR500-001");

        assertThat(approval.result()).isEqualTo(GatewayResult.FAILED);
        assertThat(approval.externalResponseCode()).isEqualTo("500");
    }

    @Test
    @DisplayName("잘못된 요청은 실패로 응답한다")
    void badRequestFails() {
        GatewayApproval approval = approve("ERR400-001");

        assertThat(approval.result()).isEqualTo(GatewayResult.FAILED);
        assertThat(approval.externalResponseCode()).isEqualTo("400");
    }

    @Test
    @DisplayName("승인 거절은 거절로 응답하고 거래번호를 남기지 않는다")
    void declinedHasNoTransactionId() {
        GatewayApproval approval = approve("DECLINE-001");

        assertThat(approval.result()).isEqualTo(GatewayResult.DECLINED);
        assertThat(approval.externalTransactionId()).isNull();
    }

    @Test
    @DisplayName("지연 응답도 결국 승인으로 끝난다")
    void slowStillApproves() {
        assertThat(approve("SLOW-001").result()).isEqualTo(GatewayResult.APPROVED);
    }
}
