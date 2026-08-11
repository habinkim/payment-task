package com.switchwon.payment.wallet.controller.dto;

import com.switchwon.payment.wallet.domain.WalletCharge;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "ChargeResponse", description = "지갑 충전 결과")
public record ChargeResponse(

        @Schema(description = "충전번호", example = "CHG-20260812-001")
        String chargeNo,

        @Schema(description = "충전한 지갑", example = "1")
        Long walletId,

        @Schema(description = "충전 금액", example = "500.0000")
        BigDecimal amount,

        @Schema(description = "통화", example = "USD")
        String currency,

        @Schema(description = "충전 시각", example = "2026-08-12T00:00:00Z")
        Instant createdAt
) {

    public static ChargeResponse from(WalletCharge charge) {
        return new ChargeResponse(
                charge.chargeNo(), charge.walletId(), charge.amount(), charge.currency(), charge.createdAt());
    }
}
