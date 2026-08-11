package com.switchwon.payment.payment.controller.dto;

import com.switchwon.payment.payment.service.PaymentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9-]{1,64}$")
        String paymentNo,

        @NotNull
        Long walletId,

        @NotNull
        @Positive
        BigDecimal amount,

        @NotBlank
        @Size(min = 3, max = 3)
        String currency
) {

    public PaymentCommand toCommand() {
        return new PaymentCommand(paymentNo, walletId, amount, currency);
    }
}
