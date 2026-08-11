package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;

/**
 * 선불 충전형 지갑.
 * 잔액 판정과 증감 규칙을 이 객체가 소유한다.
 *
 * 동시 요청 상황의 차감은 이 객체가 아니라 조건부 UPDATE 로 처리한다(docs/adr/0003).
 * 여기의 withdraw 는 단건 도메인 규칙을 표현하며, 경쟁 조건 방어는 저장소 계층의 책임이다.
 */
public class Wallet {

    private static final int CURRENCY_LENGTH = 3;

    private final Long id;
    private final String currency;
    private BigDecimal balance;

    public Wallet(Long id, String currency, BigDecimal balance) {
        if (currency == null || currency.length() != CURRENCY_LENGTH) {
            throw new IllegalArgumentException("currency는 ISO 4217 세 글자여야 합니다: " + currency);
        }
        if (balance == null || balance.signum() < 0) {
            throw new IllegalArgumentException("balance는 음수일 수 없습니다: " + balance);
        }

        this.id = id;
        this.currency = currency;
        this.balance = balance;
    }

    public boolean canAfford(BigDecimal amount) {
        requirePositive(amount);
        return balance.compareTo(amount) >= 0;
    }

    public void charge(BigDecimal amount) {
        requirePositive(amount);
        this.balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        requirePositive(amount);
        if (!canAfford(amount)) {
            throw new InsufficientBalanceException(id, balance, amount);
        }
        this.balance = balance.subtract(amount);
    }

    /** 결제 통화가 지갑 통화와 다르면 처리할 수 없다. 환전은 이 시스템의 범위가 아니다. */
    public boolean supports(String requestCurrency) {
        return currency.equals(requestCurrency);
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다: " + amount);
        }
    }

    public Long id() {
        return id;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal balance() {
        return balance;
    }
}
