package com.switchwon.payment.payment.infra;

import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_no", nullable = false, updatable = false, length = 64)
    private String paymentNo;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    private FailureReason failureReason;

    private Boolean retriable;

    @Column(name = "external_transaction_id", length = 64)
    private String externalTransactionId;

    @Column(name = "external_response_code", length = 32)
    private String externalResponseCode;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PaymentEntity(Payment payment, Instant now) {
        this.paymentNo = payment.paymentNo();
        this.walletId = payment.walletId();
        this.amount = payment.amount();
        this.currency = payment.currency();
        this.createdAt = now;
        applyState(payment, now);
    }

    public static PaymentEntity from(Payment payment, Instant now) {
        return new PaymentEntity(payment, now);
    }

    public void applyState(Payment payment, Instant now) {
        this.status = payment.status();
        this.failureReason = payment.failureReason();
        this.retriable = payment.retriable();
        this.externalTransactionId = payment.externalTransactionId();
        this.externalResponseCode = payment.externalResponseCode();
        this.requestedAt = payment.requestedAt();
        this.respondedAt = payment.respondedAt();
        this.updatedAt = now;
    }

    public Payment toDomain() {
        return Payment.restore(
                paymentNo, walletId, amount, currency,
                status, failureReason, retriable,
                externalTransactionId, externalResponseCode,
                requestedAt, respondedAt);
    }
}
