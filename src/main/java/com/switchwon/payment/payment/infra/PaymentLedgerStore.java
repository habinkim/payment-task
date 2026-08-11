package com.switchwon.payment.payment.infra;

import com.switchwon.payment.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentLedgerStore {
    private final PaymentRepository repository;
    private final Clock clock;

    public Payment append(Payment payment) {
        PaymentEntity saved = repository.save(PaymentEntity.from(payment, Instant.now(clock)));
        return saved.toDomain();
    }

    public void updateState(Payment payment) {
        repository.findByPaymentNo(payment.paymentNo())
                .ifPresent(entity -> entity.applyState(payment, Instant.now(clock)));
    }

    public Optional<Payment> findByPaymentNo(String paymentNo) {
        return repository.findByPaymentNo(paymentNo).map(PaymentEntity::toDomain);
    }

    public boolean exists(String paymentNo) {
        return repository.existsByPaymentNo(paymentNo);
    }
}
