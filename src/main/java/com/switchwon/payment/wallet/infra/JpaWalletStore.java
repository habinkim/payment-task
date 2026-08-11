package com.switchwon.payment.wallet.infra;

import com.switchwon.payment.wallet.domain.Wallet;
import com.switchwon.payment.wallet.domain.WalletStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaWalletStore implements WalletStore {
    private final WalletRepository repository;
    private final Clock clock;

    @Override
    public Optional<Wallet> findById(Long walletId) {
        return repository.findById(walletId).map(WalletEntity::toDomain);
    }

    @Transactional
    @Override
    public boolean deductIfEnough(Long walletId, BigDecimal amount) {
        return repository.deductIfEnough(walletId, amount, Instant.now(clock)) == 1;
    }

    @Transactional
    @Override
    public boolean charge(Long walletId, BigDecimal amount) {
        return repository.charge(walletId, amount, Instant.now(clock)) == 1;
    }
}
