package com.switchwon.payment.gateway;

import java.math.BigDecimal;
import java.util.Objects;

public record ExternalApprovalRequest(String paymentNo, BigDecimal amount, String currency) {

    public ExternalApprovalRequest {
        Objects.requireNonNull(paymentNo, "paymentNo");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
}
