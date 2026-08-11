package com.switchwon.payment.gateway;

import java.util.Objects;

public record GatewayInquiry(
        InquiryResult result,
        String externalTransactionId,
        String externalResponseCode
) {

    public GatewayInquiry {
        Objects.requireNonNull(result, "result");
    }

    public static GatewayInquiry approved(String externalTransactionId, String externalResponseCode) {
        return new GatewayInquiry(InquiryResult.APPROVED, externalTransactionId, externalResponseCode);
    }

    public static GatewayInquiry declined(String externalTransactionId, String externalResponseCode) {
        return new GatewayInquiry(InquiryResult.DECLINED, externalTransactionId, externalResponseCode);
    }

    public static GatewayInquiry notFound(String externalResponseCode) {
        return new GatewayInquiry(InquiryResult.NOT_FOUND, null, externalResponseCode);
    }

    public static GatewayInquiry stillUnknown(String externalResponseCode) {
        return new GatewayInquiry(InquiryResult.STILL_UNKNOWN, null, externalResponseCode);
    }
}
