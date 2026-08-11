package com.switchwon.payment.wallet.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.wallet.domain.Wallet;
import com.switchwon.payment.wallet.domain.WalletStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final WalletStore walletStore;

    @Transactional(readOnly = true)
    public Wallet getById(Long walletId) {
        return walletStore.findById(walletId)
                .orElseThrow(() -> new ApiException(ResponseCode.WALLET_NOT_FOUND));
    }
}
