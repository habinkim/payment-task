package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;

/**
 * 도메인 예외다. 프레임워크 타입에 의존하지 않는다.
 * HTTP 응답으로의 변환은 서비스 계층에서 ApiException 으로 옮겨 담아 처리한다.
 */
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
