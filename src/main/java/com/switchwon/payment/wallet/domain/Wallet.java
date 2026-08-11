package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;

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
