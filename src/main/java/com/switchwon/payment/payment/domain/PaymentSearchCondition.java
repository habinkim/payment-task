package com.switchwon.payment.payment.domain;

import java.time.Instant;

public record PaymentSearchCondition(
        PaymentStatus status,
        Long walletId,
        Instant from,
        Instant to
) {
}
