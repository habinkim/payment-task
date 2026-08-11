package com.switchwon.payment.payment.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByPaymentNo(String paymentNo);

    boolean existsByPaymentNo(String paymentNo);
}
