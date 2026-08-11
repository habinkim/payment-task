package com.switchwon.payment.external;

public interface ExternalPaymentClient {

    ExternalApproval approve(ExternalApprovalRequest request);

    ExternalInquiry inquire(String paymentNo);
}
