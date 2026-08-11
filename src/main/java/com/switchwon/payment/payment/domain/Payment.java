package com.switchwon.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

public class Payment {
    private static final Pattern PAYMENT_NO = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final int CURRENCY_LENGTH = 3;

    private final String paymentNo;
    private final Long walletId;
    private final BigDecimal amount;
    private final String currency;

    private PaymentStatus status;
    private FailureReason failureReason;
    private Boolean retriable;
    private String externalTransactionId;
    private String externalResponseCode;
    private Instant requestedAt;
    private Instant respondedAt;

    public Payment(String paymentNo, Long walletId, BigDecimal amount, String currency) {
        if (paymentNo == null || !PAYMENT_NO.matcher(paymentNo).matches()) {
            throw new IllegalArgumentException("paymentNo는 영숫자와 하이픈 64자 이내여야 합니다: " + paymentNo);
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

        this.paymentNo = paymentNo;
        this.walletId = walletId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment restore(String paymentNo, Long walletId, BigDecimal amount, String currency,
                                  PaymentStatus status, FailureReason failureReason, Boolean retriable,
                                  String externalTransactionId, String externalResponseCode,
                                  Instant requestedAt, Instant respondedAt) {
        Payment payment = new Payment(paymentNo, walletId, amount, currency);
        payment.status = status;
        payment.failureReason = failureReason;
        payment.retriable = retriable;
        payment.externalTransactionId = externalTransactionId;
        payment.externalResponseCode = externalResponseCode;
        payment.requestedAt = requestedAt;
        payment.respondedAt = respondedAt;
        return payment;
    }

    public void markRequested(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public void recordExternalApproval(String externalTransactionId) {
        if (status != PaymentStatus.FAILED) {
            throw new IllegalStateException(
                    "실패한 결제에만 외부 승인 사실을 남길 수 있습니다: " + status + " (paymentNo=" + paymentNo + ")");
        }
        this.externalTransactionId = externalTransactionId;
    }

    public void complete(String externalTransactionId, String externalResponseCode, Instant respondedAt) {
        transitionTo(PaymentStatus.COMPLETED);
        this.externalTransactionId = externalTransactionId;
        this.externalResponseCode = externalResponseCode;
        this.respondedAt = respondedAt;
    }

    public void fail(FailureReason reason, boolean retriable, String externalResponseCode, Instant respondedAt) {
        if (reason == null) {
            throw new IllegalArgumentException("실패에는 사유가 필요합니다");
        }
        transitionTo(PaymentStatus.FAILED);
        this.failureReason = reason;
        this.retriable = retriable;
        this.externalResponseCode = externalResponseCode;
        this.respondedAt = respondedAt;
    }

    public void markUnknown(String externalResponseCode, Instant respondedAt) {
        transitionTo(PaymentStatus.UNKNOWN);
        this.failureReason = FailureReason.SYSTEM_ERROR;
        this.retriable = true;
        this.externalResponseCode = externalResponseCode;
        this.respondedAt = respondedAt;
    }

    private void transitionTo(PaymentStatus next) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "종료 상태에서는 전이할 수 없습니다: " + status + " -> " + next + " (paymentNo=" + paymentNo + ")");
        }
        if (next == PaymentStatus.UNKNOWN && status == PaymentStatus.UNKNOWN) {
            throw new IllegalStateException("이미 결과 미상입니다: paymentNo=" + paymentNo);
        }
        this.status = next;
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public String paymentNo() {
        return paymentNo;
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

    public PaymentStatus status() {
        return status;
    }

    public FailureReason failureReason() {
        return failureReason;
    }

    public Boolean retriable() {
        return retriable;
    }

    public String externalTransactionId() {
        return externalTransactionId;
    }

    public String externalResponseCode() {
        return externalResponseCode;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant respondedAt() {
        return respondedAt;
    }
}
