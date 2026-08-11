package com.switchwon.payment.payment.controller.dto;

import com.switchwon.payment.payment.service.PaymentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "PaymentRequest", description = "결제 요청")
public record PaymentRequest(

        @Schema(
                description = """
                        3rd party가 생성하는 결제번호. 조회 키이자 멱등 키를 겸한다.
                        같은 번호로 다시 요청하면 재차감 없이 최초 결과를 돌려준다.
                        접두어로 게이트웨이 시나리오를 지정할 수 있다.
                        `DECLINE-`, `ERR500-`, `ERR400-`, `TIMEOUT-`, `SLOW-`
                        """,
                example = "PAY-20260811-001",
                maxLength = 64,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$")
        String paymentNo,

        @Schema(
                description = "대상 지갑. 1=USD 1000, 2=USD 10(잔액 부족 재현용), 3=JPY 50000(통화 불일치 재현용)",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long walletId,

        @Schema(
                description = "결제 금액. 소수점 넷째 자리까지 다룬다.",
                example = "100.0000",
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

    public PaymentCommand toCommand() {
        return new PaymentCommand(paymentNo, walletId, amount, currency);
    }
}
