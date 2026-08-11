package com.switchwon.payment.gateway;

public interface ExternalPaymentClient {

    ExternalApproval approve(ExternalApprovalRequest request);

    ExternalInquiry inquire(String paymentNo);
}
