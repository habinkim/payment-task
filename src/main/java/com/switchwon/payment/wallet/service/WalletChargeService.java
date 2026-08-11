package com.switchwon.payment.wallet.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.wallet.domain.Wallet;
import com.switchwon.payment.wallet.domain.WalletCharge;
import com.switchwon.payment.wallet.domain.WalletChargeStore;
import com.switchwon.payment.wallet.domain.WalletStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletChargeService {

    private final WalletChargeStore chargeStore;
    private final WalletStore walletStore;

    @Transactional
    public WalletCharge charge(ChargeCommand command) {
        Optional<WalletCharge> existing = chargeStore.findByChargeNo(command.chargeNo());
        if (existing.isPresent()) {
            return existing.get();
        }

        Wallet wallet = walletStore.findById(command.walletId())
                .orElseThrow(() -> new ApiException(ResponseCode.WALLET_NOT_FOUND));
        if (!wallet.supports(command.currency())) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }

        WalletCharge appended = append(command);
        if (!walletStore.charge(command.walletId(), command.amount())) {
            throw new ApiException(ResponseCode.WALLET_NOT_FOUND);
        }
        return appended;
    }

    private WalletCharge append(ChargeCommand command) {
        WalletCharge charge = new WalletCharge(
                command.chargeNo(), command.walletId(), command.amount(), command.currency());
        try {
            return chargeStore.append(charge);
        } catch (DataIntegrityViolationException e) {
            return chargeStore.findByChargeNo(command.chargeNo())
                    .orElseThrow(() -> new ApiException(ResponseCode.SYSTEM_ERROR));
        }
    }
}
