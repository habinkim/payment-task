package com.switchwon.payment.wallet.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

interface WalletRepository extends JpaRepository<WalletEntity, Long> {

    @Modifying(clearAutomatically = true)
    @Query("""
            update WalletEntity w
               set w.balance = w.balance - :amount,
                   w.updatedAt = :now
             where w.id = :walletId
               and w.balance >= :amount
            """)
    int deductIfEnough(@Param("walletId") Long walletId,
                       @Param("amount") BigDecimal amount,
                       @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
            update WalletEntity w
               set w.balance = w.balance + :amount,
                   w.updatedAt = :now
             where w.id = :walletId
            """)
    int charge(@Param("walletId") Long walletId,
               @Param("amount") BigDecimal amount,
               @Param("now") Instant now);

    Optional<WalletEntity> findById(Long id);
}
