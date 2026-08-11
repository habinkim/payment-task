package com.switchwon.payment.wallet.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface WalletChargeRepository extends JpaRepository<WalletChargeEntity, Long> {

    Optional<WalletChargeEntity> findByChargeNo(String chargeNo);
}
