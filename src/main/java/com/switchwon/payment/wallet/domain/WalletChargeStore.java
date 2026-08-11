package com.switchwon.payment.wallet.domain;

import java.util.Optional;

public interface WalletChargeStore {

    WalletCharge append(WalletCharge charge);

    Optional<WalletCharge> findByChargeNo(String chargeNo);
}
