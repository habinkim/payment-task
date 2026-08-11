package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.external.ExternalApproval;
import com.switchwon.payment.external.ExternalInquiry;
import com.switchwon.payment.external.ExternalInquiryResult;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentLedgerStore;
import com.switchwon.payment.wallet.domain.Wallet;
import com.switchwon.payment.wallet.domain.WalletStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentLedgerStore ledgerStore;
    private final WalletStore walletStore;
    private final PaymentMetrics metrics;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<Payment> findExisting(String merchantPaymentNo) {
        return ledgerStore.findByMerchantPaymentNo(merchantPaymentNo);
    }

    @Transactional(readOnly = true)
    public Wallet loadWallet(Long walletId) {
        return walletStore.findById(walletId)
                .orElseThrow(() -> new ApiException(ResponseCode.WALLET_NOT_FOUND));
    }

    @Transactional
    public Payment openPending(String merchantPaymentNo, Long walletId, BigDecimal amount, String currency) {
        try {
            return ledgerStore.append(new Payment(merchantPaymentNo, walletId, amount, currency));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ResponseCode.DUPLICATE_PAYMENT_NO);
        }
    }

    @Transactional
    public Payment rejectWithoutGateway(Payment payment, FailureReason reason) {
        payment.fail(reason, false, null, Instant.now(clock));
        ledgerStore.updateState(payment);
        metrics.recordResult(payment);
        return payment;
    }

    @Transactional
    public Payment settle(Payment payment, ExternalApproval approval) {
        Instant now = Instant.now(clock);

        switch (approval.result()) {
            case APPROVED -> settleApproved(payment, approval, now);
            case DECLINED -> payment.fail(FailureReason.PAYMENT_DECLINED, false, approval.externalResponseCode(), now);
            case FAILED_RETRIABLE -> payment.fail(FailureReason.SYSTEM_ERROR, true, approval.externalResponseCode(), now);
            case FAILED_PERMANENT -> payment.fail(FailureReason.SYSTEM_ERROR, false, approval.externalResponseCode(), now);
            case IN_DOUBT -> payment.markUnknown(approval.externalResponseCode(), now);
        }

        ledgerStore.updateState(payment);
        metrics.recordResult(payment);
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> findOldestUnknown(int limit) {
        return ledgerStore.findOldestUnknown(limit);
    }

    @Transactional
    public Payment confirm(Payment payment, ExternalInquiry inquiry) {
        if (inquiry.result() == ExternalInquiryResult.STILL_UNKNOWN) {
            metrics.recordReconcile(inquiry.result().name());
            return payment;
        }

        Instant now = Instant.now(clock);
        switch (inquiry.result()) {
            case APPROVED -> confirmApproved(payment, inquiry, now);
            case DECLINED -> payment.fail(FailureReason.PAYMENT_DECLINED, false, inquiry.externalResponseCode(), now);
            case NOT_FOUND -> payment.fail(FailureReason.SYSTEM_ERROR, false, inquiry.externalResponseCode(), now);
            case STILL_UNKNOWN -> throw new IllegalStateException("위에서 걸러졌어야 합니다");
        }

        ledgerStore.updateState(payment);
        metrics.recordResult(payment);
        metrics.recordReconcile(payment.status().name());
        return payment;
    }

    private void confirmApproved(Payment payment, ExternalInquiry inquiry, Instant now) {
        if (walletStore.deductIfEnough(payment.walletId(), payment.amount())) {
            payment.complete(inquiry.externalTransactionId(), inquiry.externalResponseCode(), now);
            return;
        }

        payment.fail(FailureReason.INSUFFICIENT_BALANCE, false, inquiry.externalResponseCode(), now);
        payment.recordExternalApproval(inquiry.externalTransactionId());
        metrics.recordOrphan(payment);
    }

    private void settleApproved(Payment payment, ExternalApproval approval, Instant now) {
        if (walletStore.deductIfEnough(payment.walletId(), payment.amount())) {
            payment.complete(approval.externalTransactionId(), approval.externalResponseCode(), now);
            return;
        }

        payment.fail(FailureReason.INSUFFICIENT_BALANCE, false, approval.externalResponseCode(), now);
        payment.recordExternalApproval(approval.externalTransactionId());
        metrics.recordOrphan(payment);
    }
}
