package com.switchwon.payment.gateway;

import java.util.Objects;

public record ExternalApproval(
        ExternalApprovalResult result,
        String externalTransactionId,
        String externalResponseCode
) {
    public ExternalApproval {
        Objects.requireNonNull(result, "result");
    }

    public static ExternalApproval approved(String externalTransactionId, String externalResponseCode) {
        return new ExternalApproval(ExternalApprovalResult.APPROVED, externalTransactionId, externalResponseCode);
    }

    public static ExternalApproval declined(String externalResponseCode) {
        return new ExternalApproval(ExternalApprovalResult.DECLINED, null, externalResponseCode);
    }

    public static ExternalApproval failedRetriable(String externalResponseCode) {
        return new ExternalApproval(ExternalApprovalResult.FAILED_RETRIABLE, null, externalResponseCode);
    }

    public static ExternalApproval failedPermanent(String externalResponseCode) {
        return new ExternalApproval(ExternalApprovalResult.FAILED_PERMANENT, null, externalResponseCode);
    }

    public static ExternalApproval inDoubt(String externalResponseCode) {
        return new ExternalApproval(ExternalApprovalResult.IN_DOUBT, null, externalResponseCode);
    }
}
