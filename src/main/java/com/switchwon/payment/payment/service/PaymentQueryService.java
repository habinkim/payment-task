package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.common.page.PageQuery;
import com.switchwon.payment.common.page.PageResult;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentSearchCondition;
import com.switchwon.payment.payment.domain.PaymentLedgerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentLedgerStore ledgerStore;

    @Transactional(readOnly = true)
    public Payment getByPaymentNo(String paymentNo) {
        return ledgerStore.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ApiException(ResponseCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResult<Payment> search(PaymentSearchCondition condition, int page, int size) {
        return ledgerStore.search(condition, new PageQuery(page, capped(size)));
    }

    private int capped(int size) {
        if (size < 1) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
