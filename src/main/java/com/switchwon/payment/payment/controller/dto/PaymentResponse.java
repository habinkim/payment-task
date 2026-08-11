package com.switchwon.payment.payment.controller.dto;

import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String paymentNo,
        Long walletId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        FailureReason failureReason,
        Boolean retriable,
        String externalTransactionId,
        String externalResponseCode,
        Instant requestedAt,
        Instant respondedAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.paymentNo(),
                payment.walletId(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.failureReason(),
                payment.retriable(),
                payment.externalTransactionId(),
                payment.externalResponseCode(),
                payment.requestedAt(),
                payment.respondedAt());
    }
}
