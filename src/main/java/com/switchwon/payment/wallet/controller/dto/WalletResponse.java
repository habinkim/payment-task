package com.switchwon.payment.wallet.controller.dto;

import com.switchwon.payment.wallet.domain.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "WalletResponse", description = "지갑 잔액")
public record WalletResponse(

        @Schema(description = "지갑 식별자", example = "1")
        Long walletId,

        @Schema(description = "지갑 통화. 결제 요청 통화와 같아야 한다.", example = "USD")
        String currency,

        @Schema(description = "현재 잔액", example = "900.0000")
        BigDecimal balance
) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.id(), wallet.currency(), wallet.balance());
    }
}
