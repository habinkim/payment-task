package com.switchwon.payment.payment.domain;

import com.switchwon.payment.common.page.PageQuery;
import com.switchwon.payment.common.page.PageResult;

import java.util.List;
import java.util.Optional;

public interface PaymentLedgerStore {

    Payment append(Payment payment);

    boolean updateState(Payment payment);

    Optional<Payment> findByMerchantPaymentNo(String merchantPaymentNo);

    PageResult<Payment> search(PaymentSearchCondition condition, PageQuery query);

    List<Payment> findOldestUnknown(int limit);
}
