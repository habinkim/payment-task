package com.switchwon.payment.gateway;

import java.math.BigDecimal;
import java.util.Objects;

public record GatewayApprovalRequest(String paymentNo, BigDecimal amount, String currency) {

    public GatewayApprovalRequest {
        Objects.requireNonNull(paymentNo, "paymentNo");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
}
