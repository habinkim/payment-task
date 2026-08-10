package com.switchwon.payment.gateway;

import java.util.Objects;

/**
 * 외부 승인 결과다.
 * 성공과 실패를 넘어 결과 미상(IN_DOUBT)을 별도 결과로 표현한다.
 * 타임아웃은 실패가 아니라 무슨 일이 일어났는지 모르는 상태이기 때문이다(docs/adr/0004).
 */
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
