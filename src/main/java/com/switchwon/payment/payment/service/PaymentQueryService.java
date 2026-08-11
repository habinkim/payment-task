package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.infra.PaymentLedgerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_BY = "createdAt";

    private final PaymentLedgerStore ledgerStore;

    @Transactional(readOnly = true)
    public Payment getByPaymentNo(String paymentNo) {
        return ledgerStore.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new ApiException(ResponseCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<Payment> search(PaymentSearchCondition condition, int page, int size) {
        return ledgerStore.search(condition, PageRequest.of(page, capped(size), Sort.by(Sort.Direction.DESC, SORT_BY)));
    }

    private int capped(int size) {
        if (size < 1) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
