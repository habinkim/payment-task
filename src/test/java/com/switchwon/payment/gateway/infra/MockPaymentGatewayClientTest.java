package com.switchwon.payment.gateway.infra;

import com.switchwon.payment.gateway.GatewayApproval;
import com.switchwon.payment.gateway.GatewayApprovalRequest;
import com.switchwon.payment.gateway.GatewayChaosProperties;
import com.switchwon.payment.gateway.GatewayInquiry;
import com.switchwon.payment.gateway.GatewayResult;
import com.switchwon.payment.gateway.InquiryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayClientTest {

    private static final long SEED = 42L;

    private MockPaymentGatewayClient clientWith(GatewayChaosProperties chaos) {
        return new MockPaymentGatewayClient(chaos, new Random(SEED));
    }

    private MockPaymentGatewayClient plainClient() {
        return clientWith(GatewayChaosProperties.disabled());
    }

    private GatewayApproval approve(MockPaymentGatewayClient client, String paymentNo) {
        return client.approve(new GatewayApprovalRequest(paymentNo, new BigDecimal("100"), "USD"));
    }

    @Nested
    @DisplayName("승인 요청")
    class Approve {

        @Test
        @DisplayName("접두어가 없으면 승인하고 외부 거래번호를 발급한다")
        void approvesWithTransactionId() {
            GatewayApproval approval = approve(plainClient(), "PAY-001");

            assertThat(approval.result()).isEqualTo(GatewayResult.APPROVED);
            assertThat(approval.externalTransactionId()).startsWith("TXN-");
            assertThat(approval.externalResponseCode()).isEqualTo("0000");
        }

        @Test
        @DisplayName("승인 건마다 서로 다른 거래번호를 발급한다")
        void issuesDistinctTransactionIds() {
            MockPaymentGatewayClient client = plainClient();

            assertThat(approve(client, "PAY-001").externalTransactionId())
                    .isNotEqualTo(approve(client, "PAY-002").externalTransactionId());
        }

        @Test
        @DisplayName("타임아웃은 결과 미상으로 응답하고 거래번호를 남기지 않는다")
        void timeoutIsInDoubt() {
            GatewayApproval approval = approve(plainClient(), "TIMEOUT-001");

            assertThat(approval.result()).isEqualTo(GatewayResult.IN_DOUBT);
            assertThat(approval.externalTransactionId()).isNull();
            assertThat(approval.externalResponseCode()).isEqualTo("TIMEOUT");
        }

        @Test
        @DisplayName("서버 오류는 재시도 가능한 실패로 응답한다")
        void serverErrorIsRetriable() {
            GatewayApproval approval = approve(plainClient(), "ERR500-001");

            assertThat(approval.result()).isEqualTo(GatewayResult.FAILED_RETRIABLE);
            assertThat(approval.externalResponseCode()).isEqualTo("500");
        }

        @Test
        @DisplayName("잘못된 요청은 재시도 불가한 실패로 응답한다")
        void badRequestIsPermanent() {
            GatewayApproval approval = approve(plainClient(), "ERR400-001");

            assertThat(approval.result()).isEqualTo(GatewayResult.FAILED_PERMANENT);
            assertThat(approval.externalResponseCode()).isEqualTo("400");
        }

        @Test
        @DisplayName("승인 거절은 거절로 응답하고 거래번호를 남기지 않는다")
        void declinedHasNoTransactionId() {
            GatewayApproval approval = approve(plainClient(), "DECLINE-001");

            assertThat(approval.result()).isEqualTo(GatewayResult.DECLINED);
            assertThat(approval.externalTransactionId()).isNull();
        }

        @Test
        @DisplayName("지연 응답도 결국 승인으로 끝난다")
        void slowStillApproves() {
            assertThat(approve(plainClient(), "SLOW-001").result()).isEqualTo(GatewayResult.APPROVED);
        }
    }

    @Nested
    @DisplayName("장애 주입")
    class ChaosInjection {

        private List<GatewayResult> resultsOf(GatewayChaosProperties chaos, int times) {
            MockPaymentGatewayClient client = clientWith(chaos);
            return IntStream.range(0, times)
                    .mapToObj(i -> approve(client, "PAY-" + i).result())
                    .toList();
        }

        @Test
        @DisplayName("꺼져 있으면 접두어 없는 요청은 항상 승인된다")
        void disabledAlwaysApproves() {
            assertThat(resultsOf(GatewayChaosProperties.disabled(), 50))
                    .containsOnly(GatewayResult.APPROVED);
        }

        @Test
        @DisplayName("타임아웃 확률이 1이면 모두 결과 미상이 된다")
        void fullTimeoutRate() {
            assertThat(resultsOf(new GatewayChaosProperties(true, 1.0, 0.0), 20))
                    .containsOnly(GatewayResult.IN_DOUBT);
        }

        @Test
        @DisplayName("실패 확률이 1이면 모두 재시도 가능한 실패가 된다")
        void fullFailureRate() {
            assertThat(resultsOf(new GatewayChaosProperties(true, 0.0, 1.0), 20))
                    .containsOnly(GatewayResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("같은 시드로는 같은 결과가 재현된다")
        void sameSeedReproducesSameSequence() {
            GatewayChaosProperties chaos = new GatewayChaosProperties(true, 0.3, 0.2);

            assertThat(resultsOf(chaos, 30)).isEqualTo(resultsOf(chaos, 30));
        }

        @Test
        @DisplayName("확률을 켜면 승인과 실패가 섞인다")
        void mixesResults() {
            List<GatewayResult> results = resultsOf(new GatewayChaosProperties(true, 0.3, 0.2), 100);

            assertThat(results).contains(GatewayResult.APPROVED);
            assertThat(results).contains(GatewayResult.IN_DOUBT);
            assertThat(results).contains(GatewayResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("접두어로 지정한 시나리오는 확률과 무관하게 그대로 나온다")
        void prefixOverridesChance() {
            MockPaymentGatewayClient client = clientWith(new GatewayChaosProperties(true, 1.0, 0.0));

            assertThat(approve(client, "DECLINE-001").result()).isEqualTo(GatewayResult.DECLINED);
            assertThat(approve(client, "ERR400-001").result()).isEqualTo(GatewayResult.FAILED_PERMANENT);
            assertThat(approve(client, "ERR500-001").result()).isEqualTo(GatewayResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("확률의 합이 1을 넘으면 설정을 만들 수 없다")
        void rejectsImpossibleRates() {
            assertThatThrownBy(() -> new GatewayChaosProperties(true, 0.7, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("확률이 음수면 설정을 만들 수 없다")
        void rejectsNegativeRates() {
            assertThatThrownBy(() -> new GatewayChaosProperties(true, -0.1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class Inquire {

        @Test
        @DisplayName("타임아웃 건을 재조회하면 승인으로 확정된다")
        void timeoutResolvesToApproved() {
            GatewayInquiry inquiry = plainClient().inquire("TIMEOUT-001");

            assertThat(inquiry.result()).isEqualTo(InquiryResult.APPROVED);
            assertThat(inquiry.externalTransactionId()).startsWith("TXN-");
        }

        @Test
        @DisplayName("거절된 건은 조회해도 거절이며 거래번호가 함께 온다")
        void declinedKeepsTransactionId() {
            GatewayInquiry inquiry = plainClient().inquire("DECLINE-001");

            assertThat(inquiry.result()).isEqualTo(InquiryResult.DECLINED);
            assertThat(inquiry.externalTransactionId()).isNotNull();
        }

        @Test
        @DisplayName("서버 오류 건은 조회해도 여전히 결과를 알 수 없다")
        void serverErrorStaysUnknown() {
            assertThat(plainClient().inquire("ERR500-001").result()).isEqualTo(InquiryResult.STILL_UNKNOWN);
        }

        @Test
        @DisplayName("잘못된 요청은 게이트웨이에 기록이 없다")
        void badRequestIsNotFound() {
            GatewayInquiry inquiry = plainClient().inquire("ERR400-001");

            assertThat(inquiry.result()).isEqualTo(InquiryResult.NOT_FOUND);
            assertThat(inquiry.externalTransactionId()).isNull();
        }

        @Test
        @DisplayName("접두어가 없으면 승인된 것으로 조회된다")
        void plainPaymentIsApproved() {
            assertThat(plainClient().inquire("PAY-001").result()).isEqualTo(InquiryResult.APPROVED);
        }
    }
}
