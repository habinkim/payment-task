package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {
    private final Long walletId;
    private final BigDecimal balance;
    private final BigDecimal requested;

    public InsufficientBalanceException(Long walletId, BigDecimal balance, BigDecimal requested) {
        super("잔액이 부족합니다: walletId=" + walletId + ", balance=" + balance + ", requested=" + requested);
        this.walletId = walletId;
        this.balance = balance;
        this.requested = requested;
    }

    public Long walletId() {
        return walletId;
    }

    public BigDecimal balance() {
        return balance;
    }

    public BigDecimal requested() {
        return requested;
    }
}
