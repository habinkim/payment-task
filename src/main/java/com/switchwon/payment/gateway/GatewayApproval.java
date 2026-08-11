package com.switchwon.payment.gateway;

import java.util.Objects;

public record GatewayApproval(
        GatewayResult result,
        String externalTransactionId,
        String externalResponseCode
) {
    public GatewayApproval {
        Objects.requireNonNull(result, "result");
    }

    public static GatewayApproval approved(String externalTransactionId, String externalResponseCode) {
        return new GatewayApproval(GatewayResult.APPROVED, externalTransactionId, externalResponseCode);
    }

    public static GatewayApproval declined(String externalResponseCode) {
        return new GatewayApproval(GatewayResult.DECLINED, null, externalResponseCode);
    }

    public static GatewayApproval failed(String externalResponseCode) {
        return new GatewayApproval(GatewayResult.FAILED, null, externalResponseCode);
    }

    public static GatewayApproval inDoubt(String externalResponseCode) {
        return new GatewayApproval(GatewayResult.IN_DOUBT, null, externalResponseCode);
    }
}
