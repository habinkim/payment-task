package com.switchwon.payment.gateway;

public enum GatewayResult {
    APPROVED,

    DECLINED,

    FAILED_RETRIABLE,

    FAILED_PERMANENT,

    IN_DOUBT
}
