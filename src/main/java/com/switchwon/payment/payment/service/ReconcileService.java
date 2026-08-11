package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.gateway.GatewayInquiry;
import com.switchwon.payment.gateway.PaymentGatewayClient;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final PaymentTransactionService transaction;
    private final PaymentGatewayClient gateway;

    public Payment reconcile(String paymentNo) {
        Payment payment = transaction.findExisting(paymentNo)
                .orElseThrow(() -> new ApiException(ResponseCode.PAYMENT_NOT_FOUND));

        if (payment.status() != PaymentStatus.UNKNOWN) {
            return payment;
        }

        return transaction.confirm(payment, inquire(paymentNo));
    }

    public List<Payment> findTargets(int limit) {
        return transaction.findOldestUnknown(limit);
    }

    private GatewayInquiry inquire(String paymentNo) {
        try {
            return gateway.inquire(paymentNo);
        } catch (RuntimeException e) {
            return GatewayInquiry.stillUnknown(e.getClass().getSimpleName());
        }
    }
}
