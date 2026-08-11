package com.switchwon.payment.external;

public enum ExternalApprovalResult {
    APPROVED,

    DECLINED,

    FAILED_RETRIABLE,

    FAILED_PERMANENT,

    IN_DOUBT
}
