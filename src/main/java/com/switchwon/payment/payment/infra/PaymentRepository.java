package com.switchwon.payment.payment.infra;

import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByMerchantPaymentNo(String merchantPaymentNo);

    boolean existsByMerchantPaymentNo(String merchantPaymentNo);

    @Query("""
            select p from PaymentEntity p
             where (:status is null or p.status = :status)
               and (:walletId is null or p.walletId = :walletId)
               and (:from is null or p.createdAt >= :from)
               and (:to is null or p.createdAt <= :to)
            """)
    Page<PaymentEntity> search(@Param("status") PaymentStatus status,
                               @Param("walletId") Long walletId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               Pageable pageable);

    List<PaymentEntity> findByStatusOrderByCreatedAtAsc(PaymentStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentEntity p
               set p.status = :status,
                   p.failureReason = :failureReason,
                   p.retriable = :retriable,
                   p.externalTransactionId = :externalTransactionId,
                   p.externalResponseCode = :externalResponseCode,
                   p.requestedAt = :requestedAt,
                   p.respondedAt = :respondedAt,
                   p.updatedAt = :now
             where p.merchantPaymentNo = :merchantPaymentNo
               and p.status not in (com.switchwon.payment.payment.domain.PaymentStatus.COMPLETED,
                                    com.switchwon.payment.payment.domain.PaymentStatus.FAILED)
            """)
    int updateStateIfNotTerminal(@Param("merchantPaymentNo") String merchantPaymentNo,
                                 @Param("status") PaymentStatus status,
                                 @Param("failureReason") FailureReason failureReason,
                                 @Param("retriable") Boolean retriable,
                                 @Param("externalTransactionId") String externalTransactionId,
                                 @Param("externalResponseCode") String externalResponseCode,
                                 @Param("requestedAt") Instant requestedAt,
                                 @Param("respondedAt") Instant respondedAt,
                                 @Param("now") Instant now);
}
