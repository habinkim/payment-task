package com.switchwon.payment.gateway;

public interface PaymentGatewayClient {

    GatewayApproval approve(GatewayApprovalRequest request);

    GatewayInquiry inquire(String paymentNo);
}
