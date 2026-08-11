package com.switchwon.payment.external;

import java.math.BigDecimal;
import java.util.Objects;

public record ExternalApprovalRequest(String merchantPaymentNo, BigDecimal amount, String currency) {

    public ExternalApprovalRequest {
        Objects.requireNonNull(merchantPaymentNo, "merchantPaymentNo");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
    }
}
