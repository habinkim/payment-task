package com.switchwon.payment.wallet.domain;

import java.math.BigDecimal;
import java.util.Optional;

public interface WalletStore {

    Optional<Wallet> findById(Long walletId);

    boolean deductIfEnough(Long walletId, BigDecimal amount);

    boolean charge(Long walletId, BigDecimal amount);
}
