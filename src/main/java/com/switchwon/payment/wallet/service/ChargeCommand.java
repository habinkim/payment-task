package com.switchwon.payment.wallet.service;

import java.math.BigDecimal;

public record ChargeCommand(String chargeNo, Long walletId, BigDecimal amount, String currency) {
}
