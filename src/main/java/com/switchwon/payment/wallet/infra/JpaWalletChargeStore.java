package com.switchwon.payment.wallet.infra;

import com.switchwon.payment.wallet.domain.WalletCharge;
import com.switchwon.payment.wallet.domain.WalletChargeStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaWalletChargeStore implements WalletChargeStore {

    private final WalletChargeRepository repository;
    private final Clock clock;

    @Override
    public WalletCharge append(WalletCharge charge) {
        return repository.save(WalletChargeEntity.from(charge, Instant.now(clock))).toDomain();
    }

    @Override
    public Optional<WalletCharge> findByChargeNo(String chargeNo) {
        return repository.findByChargeNo(chargeNo).map(WalletChargeEntity::toDomain);
    }
}
