package com.switchwon.payment.gateway;

public enum ExternalApprovalResult {
    APPROVED,

    DECLINED,

    FAILED_RETRIABLE,

    FAILED_PERMANENT,

    IN_DOUBT
}
