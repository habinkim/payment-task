package com.switchwon.payment.gateway;

/**
 * 외부 결제 게이트웨이 연동 경계다.
 * 구현체는 세 가지다. 단위 테스트용 Mock, 자족 실행용 스텁 호출, 통합 테스트용 WireMock 대상.
 * 자세한 근거는 docs/adr/0007 를 참고한다.
 */
public interface PaymentGatewayClient {

    GatewayApproval approve(GatewayApprovalRequest request);
}
