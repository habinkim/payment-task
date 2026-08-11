package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

public class WalletCharge {
    private static final Pattern CHARGE_NO = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final int CURRENCY_LENGTH = 3;

    private final String chargeNo;
    private final Long walletId;
    private final BigDecimal amount;
    private final String currency;

    private Instant createdAt;

    public WalletCharge(String chargeNo, Long walletId, BigDecimal amount, String currency) {
        if (chargeNo == null || !CHARGE_NO.matcher(chargeNo).matches()) {
            throw new IllegalArgumentException("chargeNo는 영숫자와 하이픈 64자 이내여야 합니다: " + chargeNo);
        }
        if (walletId == null) {
            throw new IllegalArgumentException("walletId는 필수입니다");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다: " + amount);
        }
        if (currency == null || currency.length() != CURRENCY_LENGTH) {
            throw new IllegalArgumentException("currency는 ISO 4217 세 글자여야 합니다: " + currency);
        }

        this.chargeNo = chargeNo;
        this.walletId = walletId;
        this.amount = amount;
        this.currency = currency;
    }

    public static WalletCharge restore(String chargeNo, Long walletId, BigDecimal amount, String currency,
                                       Instant createdAt) {
        WalletCharge charge = new WalletCharge(chargeNo, walletId, amount, currency);
        charge.createdAt = createdAt;
        return charge;
    }

    public String chargeNo() {
        return chargeNo;
    }

    public Long walletId() {
        return walletId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
