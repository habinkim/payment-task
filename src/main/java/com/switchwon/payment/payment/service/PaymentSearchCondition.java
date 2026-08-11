package com.switchwon.payment.payment.service;

import com.switchwon.payment.payment.domain.PaymentStatus;

import java.time.Instant;

public record PaymentSearchCondition(
        PaymentStatus status,
        Long walletId,
        Instant from,
        Instant to
) {

    public static PaymentSearchCondition all() {
        return new PaymentSearchCondition(null, null, null, null);
    }
}
