package com.switchwon.payment.external.infra;

import com.switchwon.payment.external.ExternalApproval;
import com.switchwon.payment.external.ExternalApprovalRequest;
import com.switchwon.payment.external.ExternalChaosProperties;
import com.switchwon.payment.external.ExternalInquiry;
import com.switchwon.payment.external.ExternalApprovalResult;
import com.switchwon.payment.external.ExternalInquiryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockExternalPaymentClientTest {

    private static final long SEED = 42L;

    private MockExternalPaymentClient clientWith(ExternalChaosProperties chaos) {
        return new MockExternalPaymentClient(chaos, new Random(SEED));
    }

    private MockExternalPaymentClient plainClient() {
        return clientWith(ExternalChaosProperties.disabled());
    }

    private ExternalApproval approve(MockExternalPaymentClient client, String merchantPaymentNo) {
        return client.approve(new ExternalApprovalRequest(merchantPaymentNo, new BigDecimal("100"), "USD"));
    }

    @Nested
    @DisplayName("승인 요청")
    class Approve {

        @Test
        @DisplayName("접두어가 없으면 승인하고 외부 거래번호를 발급한다")
        void approvesWithTransactionId() {
            ExternalApproval approval = approve(plainClient(), "PAY-001");

            assertThat(approval.result()).isEqualTo(ExternalApprovalResult.APPROVED);
            assertThat(approval.externalTransactionId()).startsWith("TXN-");
            assertThat(approval.externalResponseCode()).isEqualTo("0000");
        }

        @Test
        @DisplayName("승인 건마다 서로 다른 거래번호를 발급한다")
        void issuesDistinctTransactionIds() {
            MockExternalPaymentClient client = plainClient();

            assertThat(approve(client, "PAY-001").externalTransactionId())
                    .isNotEqualTo(approve(client, "PAY-002").externalTransactionId());
        }

        @Test
        @DisplayName("타임아웃은 결과 미상으로 응답하고 거래번호를 남기지 않는다")
        void timeoutIsInDoubt() {
            ExternalApproval approval = approve(plainClient(), "TIMEOUT-001");

            assertThat(approval.result()).isEqualTo(ExternalApprovalResult.IN_DOUBT);
            assertThat(approval.externalTransactionId()).isNull();
            assertThat(approval.externalResponseCode()).isEqualTo("TIMEOUT");
        }

        @Test
        @DisplayName("서버 오류는 재시도 가능한 실패로 응답한다")
        void serverErrorIsRetriable() {
            ExternalApproval approval = approve(plainClient(), "ERR500-001");

            assertThat(approval.result()).isEqualTo(ExternalApprovalResult.FAILED_RETRIABLE);
            assertThat(approval.externalResponseCode()).isEqualTo("500");
        }

        @Test
        @DisplayName("잘못된 요청은 재시도 불가한 실패로 응답한다")
        void badRequestIsPermanent() {
            ExternalApproval approval = approve(plainClient(), "ERR400-001");

            assertThat(approval.result()).isEqualTo(ExternalApprovalResult.FAILED_PERMANENT);
            assertThat(approval.externalResponseCode()).isEqualTo("400");
        }

        @Test
        @DisplayName("승인 거절은 거절로 응답하고 거래번호를 남기지 않는다")
        void declinedHasNoTransactionId() {
            ExternalApproval approval = approve(plainClient(), "DECLINE-001");

            assertThat(approval.result()).isEqualTo(ExternalApprovalResult.DECLINED);
            assertThat(approval.externalTransactionId()).isNull();
        }

        @Test
        @DisplayName("지연 응답도 결국 승인으로 끝난다")
        void slowStillApproves() {
            assertThat(approve(plainClient(), "SLOW-001").result()).isEqualTo(ExternalApprovalResult.APPROVED);
        }
    }

    @Nested
    @DisplayName("장애 주입")
    class ChaosInjection {

        private List<ExternalApprovalResult> resultsOf(ExternalChaosProperties chaos, int times) {
            MockExternalPaymentClient client = clientWith(chaos);
            return IntStream.range(0, times)
                    .mapToObj(i -> approve(client, "PAY-" + i).result())
                    .toList();
        }

        @Test
        @DisplayName("꺼져 있으면 접두어 없는 요청은 항상 승인된다")
        void disabledAlwaysApproves() {
            assertThat(resultsOf(ExternalChaosProperties.disabled(), 50))
                    .containsOnly(ExternalApprovalResult.APPROVED);
        }

        @Test
        @DisplayName("타임아웃 확률이 1이면 모두 결과 미상이 된다")
        void fullTimeoutRate() {
            assertThat(resultsOf(new ExternalChaosProperties(true, 1.0, 0.0), 20))
                    .containsOnly(ExternalApprovalResult.IN_DOUBT);
        }

        @Test
        @DisplayName("실패 확률이 1이면 모두 재시도 가능한 실패가 된다")
        void fullFailureRate() {
            assertThat(resultsOf(new ExternalChaosProperties(true, 0.0, 1.0), 20))
                    .containsOnly(ExternalApprovalResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("같은 시드로는 같은 결과가 재현된다")
        void sameSeedReproducesSameSequence() {
            ExternalChaosProperties chaos = new ExternalChaosProperties(true, 0.3, 0.2);

            assertThat(resultsOf(chaos, 30)).isEqualTo(resultsOf(chaos, 30));
        }

        @Test
        @DisplayName("확률을 켜면 승인과 실패가 섞인다")
        void mixesResults() {
            List<ExternalApprovalResult> results = resultsOf(new ExternalChaosProperties(true, 0.3, 0.2), 100);

            assertThat(results).contains(ExternalApprovalResult.APPROVED);
            assertThat(results).contains(ExternalApprovalResult.IN_DOUBT);
            assertThat(results).contains(ExternalApprovalResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("접두어로 지정한 시나리오는 확률과 무관하게 그대로 나온다")
        void prefixOverridesChance() {
            MockExternalPaymentClient client = clientWith(new ExternalChaosProperties(true, 1.0, 0.0));

            assertThat(approve(client, "DECLINE-001").result()).isEqualTo(ExternalApprovalResult.DECLINED);
            assertThat(approve(client, "ERR400-001").result()).isEqualTo(ExternalApprovalResult.FAILED_PERMANENT);
            assertThat(approve(client, "ERR500-001").result()).isEqualTo(ExternalApprovalResult.FAILED_RETRIABLE);
        }

        @Test
        @DisplayName("확률의 합이 1을 넘으면 설정을 만들 수 없다")
        void rejectsImpossibleRates() {
            assertThatThrownBy(() -> new ExternalChaosProperties(true, 0.7, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("확률이 음수면 설정을 만들 수 없다")
        void rejectsNegativeRates() {
            assertThatThrownBy(() -> new ExternalChaosProperties(true, -0.1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class Inquire {

        @Test
        @DisplayName("타임아웃 건을 재조회하면 승인으로 확정된다")
        void timeoutResolvesToApproved() {
            ExternalInquiry inquiry = plainClient().inquire("TIMEOUT-001");

            assertThat(inquiry.result()).isEqualTo(ExternalInquiryResult.APPROVED);
            assertThat(inquiry.externalTransactionId()).startsWith("TXN-");
        }

        @Test
        @DisplayName("거절된 건은 조회해도 거절이며 거래번호가 함께 온다")
        void declinedKeepsTransactionId() {
            ExternalInquiry inquiry = plainClient().inquire("DECLINE-001");

            assertThat(inquiry.result()).isEqualTo(ExternalInquiryResult.DECLINED);
            assertThat(inquiry.externalTransactionId()).isNotNull();
        }

        @Test
        @DisplayName("서버 오류 건은 조회해도 여전히 결과를 알 수 없다")
        void serverErrorStaysUnknown() {
            assertThat(plainClient().inquire("ERR500-001").result()).isEqualTo(ExternalInquiryResult.STILL_UNKNOWN);
        }

        @Test
        @DisplayName("잘못된 요청은 게이트웨이에 기록이 없다")
        void badRequestIsNotFound() {
            ExternalInquiry inquiry = plainClient().inquire("ERR400-001");

            assertThat(inquiry.result()).isEqualTo(ExternalInquiryResult.NOT_FOUND);
            assertThat(inquiry.externalTransactionId()).isNull();
        }

        @Test
        @DisplayName("접두어가 없으면 승인된 것으로 조회된다")
        void plainPaymentIsApproved() {
            assertThat(plainClient().inquire("PAY-001").result()).isEqualTo(ExternalInquiryResult.APPROVED);
        }
    }
}
