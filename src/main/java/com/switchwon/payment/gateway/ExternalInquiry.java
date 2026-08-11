package com.switchwon.payment.gateway;

import java.util.Objects;

public record ExternalInquiry(
        ExternalInquiryResult result,
        String externalTransactionId,
        String externalResponseCode
) {

    public ExternalInquiry {
        Objects.requireNonNull(result, "result");
    }

    public static ExternalInquiry approved(String externalTransactionId, String externalResponseCode) {
        return new ExternalInquiry(ExternalInquiryResult.APPROVED, externalTransactionId, externalResponseCode);
    }

    public static ExternalInquiry declined(String externalTransactionId, String externalResponseCode) {
        return new ExternalInquiry(ExternalInquiryResult.DECLINED, externalTransactionId, externalResponseCode);
    }

    public static ExternalInquiry notFound(String externalResponseCode) {
        return new ExternalInquiry(ExternalInquiryResult.NOT_FOUND, null, externalResponseCode);
    }

    public static ExternalInquiry stillUnknown(String externalResponseCode) {
        return new ExternalInquiry(ExternalInquiryResult.STILL_UNKNOWN, null, externalResponseCode);
    }
}
