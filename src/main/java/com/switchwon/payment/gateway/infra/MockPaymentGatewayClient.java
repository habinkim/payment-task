package com.switchwon.payment.gateway.infra;

import com.switchwon.payment.gateway.GatewayApproval;
import com.switchwon.payment.gateway.GatewayApprovalRequest;
import com.switchwon.payment.gateway.GatewayScenario;
import com.switchwon.payment.gateway.PaymentGatewayClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "mock", matchIfMissing = true)
@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    private static final String APPROVED_CODE = "0000";
    private static final String DECLINED_CODE = "DECLINED";
    private static final String SERVER_ERROR_CODE = "500";
    private static final String BAD_REQUEST_CODE = "400";
    private static final String TIMEOUT_CODE = "TIMEOUT";

    @Override
    public GatewayApproval approve(GatewayApprovalRequest request) {
        return switch (GatewayScenario.from(request.paymentNo())) {
            case TIMEOUT -> GatewayApproval.inDoubt(TIMEOUT_CODE);
            case SERVER_ERROR -> GatewayApproval.failed(SERVER_ERROR_CODE);
            case BAD_REQUEST -> GatewayApproval.failed(BAD_REQUEST_CODE);
            case DECLINED -> GatewayApproval.declined(DECLINED_CODE);
            case SLOW, APPROVED -> GatewayApproval.approved(newTransactionId(), APPROVED_CODE);
        };
    }

    private String newTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
