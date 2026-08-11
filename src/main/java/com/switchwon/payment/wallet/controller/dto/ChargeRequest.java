package com.switchwon.payment.wallet.controller.dto;

import com.switchwon.payment.wallet.service.ChargeCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ChargeRequest", description = "지갑 충전 요청")
public record ChargeRequest(

        @Schema(
                description = """
                        호출자가 생성하는 충전번호. 멱등 키를 겸한다.
                        같은 번호로 다시 요청하면 재충전 없이 최초 이력을 돌려준다.
                        """,
                example = "CHG-20260812-001",
                maxLength = 64,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$")
        String chargeNo,

        @Schema(
                description = "충전 금액. 소수점 넷째 자리까지 다룬다.",
                example = "500.0000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        BigDecimal amount,

        @Schema(
                description = "ISO 4217 통화 코드. 지갑 통화와 같아야 한다. 환전은 이 시스템의 범위가 아니다.",
                example = "USD",
                minLength = 3,
                maxLength = 3,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {

    public ChargeCommand toCommand(Long walletId) {
        return new ChargeCommand(chargeNo, walletId, amount, currency);
    }
}
