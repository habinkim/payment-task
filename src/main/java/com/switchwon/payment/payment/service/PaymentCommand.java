package com.switchwon.payment.payment.service;

import java.math.BigDecimal;

public record PaymentCommand(String paymentNo, Long walletId, BigDecimal amount, String currency) {
}
