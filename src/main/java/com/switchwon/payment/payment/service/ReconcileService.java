package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.external.ExternalInquiry;
import com.switchwon.payment.external.ExternalPaymentClient;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconcileService {

    private final PaymentTransactionService transaction;
    private final ExternalPaymentClient gateway;

    public Payment reconcile(String merchantPaymentNo) {
        Payment payment = transaction.findExisting(merchantPaymentNo)
                .orElseThrow(() -> new ApiException(ResponseCode.PAYMENT_NOT_FOUND));

        if (payment.status() != PaymentStatus.UNKNOWN) {
            return payment;
        }

        return transaction.confirm(payment, inquire(merchantPaymentNo));
    }

    public List<Payment> findTargets(int limit) {
        return transaction.findOldestUnknown(limit);
    }

    private ExternalInquiry inquire(String merchantPaymentNo) {
        try {
            return gateway.inquire(merchantPaymentNo);
        } catch (RuntimeException e) {
            return ExternalInquiry.stillUnknown(e.getClass().getSimpleName());
        }
    }
}
