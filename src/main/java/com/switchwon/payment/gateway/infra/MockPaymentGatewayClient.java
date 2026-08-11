package com.switchwon.payment.gateway.infra;

import com.switchwon.payment.gateway.GatewayApproval;
import com.switchwon.payment.gateway.GatewayApprovalRequest;
import com.switchwon.payment.gateway.GatewayChaosProperties;
import com.switchwon.payment.gateway.GatewayInquiry;
import com.switchwon.payment.gateway.GatewayScenario;
import com.switchwon.payment.gateway.PaymentGatewayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "mock", matchIfMissing = true)
@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    private static final String APPROVED_CODE = "0000";
    private static final String DECLINED_CODE = "DECLINED";
    private static final String SERVER_ERROR_CODE = "500";
    private static final String BAD_REQUEST_CODE = "400";
    private static final String TIMEOUT_CODE = "TIMEOUT";
    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String CHAOS_TIMEOUT_CODE = "CHAOS_TIMEOUT";
    private static final String CHAOS_FAILURE_CODE = "CHAOS_FAILURE";

    private final GatewayChaosProperties chaos;
    private final Random random;

    public MockPaymentGatewayClient(GatewayChaosProperties chaos, Random random) {
        this.chaos = chaos;
        this.random = random;
    }

    @Override
    public GatewayApproval approve(GatewayApprovalRequest request) {
        GatewayScenario scenario = GatewayScenario.from(request.paymentNo());
        if (scenario != GatewayScenario.APPROVED && scenario != GatewayScenario.SLOW) {
            return byScenario(scenario);
        }
        return chaos.enabled() ? byChance() : approved();
    }

    @Override
    public GatewayInquiry inquire(String paymentNo) {
        return switch (GatewayScenario.from(paymentNo)) {
            case TIMEOUT -> GatewayInquiry.approved(newTransactionId(), APPROVED_CODE);
            case DECLINED -> GatewayInquiry.declined(newTransactionId(), DECLINED_CODE);
            case SERVER_ERROR -> GatewayInquiry.stillUnknown(SERVER_ERROR_CODE);
            case BAD_REQUEST -> GatewayInquiry.notFound(NOT_FOUND_CODE);
            case SLOW, APPROVED -> GatewayInquiry.approved(newTransactionId(), APPROVED_CODE);
        };
    }

    private GatewayApproval byScenario(GatewayScenario scenario) {
        return switch (scenario) {
            case TIMEOUT -> GatewayApproval.inDoubt(TIMEOUT_CODE);
            case SERVER_ERROR -> GatewayApproval.failedRetriable(SERVER_ERROR_CODE);
            case BAD_REQUEST -> GatewayApproval.failedPermanent(BAD_REQUEST_CODE);
            case DECLINED -> GatewayApproval.declined(DECLINED_CODE);
            case SLOW, APPROVED -> approved();
        };
    }

    private GatewayApproval byChance() {
        double drawn = random.nextDouble();
        if (drawn < chaos.timeoutRate()) {
            return GatewayApproval.inDoubt(CHAOS_TIMEOUT_CODE);
        }
        if (drawn < chaos.failureThreshold()) {
            return GatewayApproval.failedRetriable(CHAOS_FAILURE_CODE);
        }
        return approved();
    }

    private GatewayApproval approved() {
        return GatewayApproval.approved(newTransactionId(), APPROVED_CODE);
    }

    private String newTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
