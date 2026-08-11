package com.switchwon.payment.gateway.infra;

import com.switchwon.payment.gateway.ExternalApproval;
import com.switchwon.payment.gateway.ExternalApprovalRequest;
import com.switchwon.payment.gateway.ExternalChaosProperties;
import com.switchwon.payment.gateway.ExternalInquiry;
import com.switchwon.payment.gateway.ExternalScenario;
import com.switchwon.payment.gateway.ExternalPaymentClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "mock", matchIfMissing = true)
@Component
public class MockExternalPaymentClient implements ExternalPaymentClient {

    private static final String APPROVED_CODE = "0000";
    private static final String DECLINED_CODE = "DECLINED";
    private static final String SERVER_ERROR_CODE = "500";
    private static final String BAD_REQUEST_CODE = "400";
    private static final String TIMEOUT_CODE = "TIMEOUT";
    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String CHAOS_TIMEOUT_CODE = "CHAOS_TIMEOUT";
    private static final String CHAOS_FAILURE_CODE = "CHAOS_FAILURE";

    private final ExternalChaosProperties chaos;
    private final Random random;

    public MockExternalPaymentClient(ExternalChaosProperties chaos, Random random) {
        this.chaos = chaos;
        this.random = random;
    }

    @Override
    public ExternalApproval approve(ExternalApprovalRequest request) {
        ExternalScenario scenario = ExternalScenario.from(request.paymentNo());
        if (scenario != ExternalScenario.APPROVED && scenario != ExternalScenario.SLOW) {
            return byScenario(scenario);
        }
        return chaos.enabled() ? byChance() : approved();
    }

    @Override
    public ExternalInquiry inquire(String paymentNo) {
        return switch (ExternalScenario.from(paymentNo)) {
            case TIMEOUT -> ExternalInquiry.approved(newTransactionId(), APPROVED_CODE);
            case DECLINED -> ExternalInquiry.declined(newTransactionId(), DECLINED_CODE);
            case SERVER_ERROR -> ExternalInquiry.stillUnknown(SERVER_ERROR_CODE);
            case BAD_REQUEST -> ExternalInquiry.notFound(NOT_FOUND_CODE);
            case SLOW, APPROVED -> ExternalInquiry.approved(newTransactionId(), APPROVED_CODE);
        };
    }

    private ExternalApproval byScenario(ExternalScenario scenario) {
        return switch (scenario) {
            case TIMEOUT -> ExternalApproval.inDoubt(TIMEOUT_CODE);
            case SERVER_ERROR -> ExternalApproval.failedRetriable(SERVER_ERROR_CODE);
            case BAD_REQUEST -> ExternalApproval.failedPermanent(BAD_REQUEST_CODE);
            case DECLINED -> ExternalApproval.declined(DECLINED_CODE);
            case SLOW, APPROVED -> approved();
        };
    }

    private ExternalApproval byChance() {
        double drawn = random.nextDouble();
        if (drawn < chaos.timeoutRate()) {
            return ExternalApproval.inDoubt(CHAOS_TIMEOUT_CODE);
        }
        if (drawn < chaos.failureThreshold()) {
            return ExternalApproval.failedRetriable(CHAOS_FAILURE_CODE);
        }
        return approved();
    }

    private ExternalApproval approved() {
        return ExternalApproval.approved(newTransactionId(), APPROVED_CODE);
    }

    private String newTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
