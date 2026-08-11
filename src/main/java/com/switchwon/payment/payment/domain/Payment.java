package com.switchwon.payment.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 결제 원장.
 * 상태 전이 규칙과 불변식을 이 객체가 직접 소유한다.
 * 서비스는 트랜잭션 경계를 긋고 이 객체를 조립할 뿐, 전이 가능 여부를 판단하지 않는다.
 */
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

    /** 외부 호출 직전에 호출한다. 응답 지연을 추적하기 위한 시각이다. */
    public void markRequested(Instant requestedAt) {
        this.requestedAt = requestedAt;
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

    /**
     * 외부 승인 여부를 알 수 없는 상태로 남긴다.
     * 실패가 아니라 결과 미상이므로 잔액을 차감하지 않으며, 조회로 확정해야 한다.
     */
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
