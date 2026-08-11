package com.switchwon.payment.payment.domain;

public enum PaymentStatus {
    PENDING,

    COMPLETED,

    FAILED,

    UNKNOWN;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
