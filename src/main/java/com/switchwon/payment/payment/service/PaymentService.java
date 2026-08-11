package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.gateway.ExternalApproval;
import com.switchwon.payment.gateway.ExternalApprovalRequest;
import com.switchwon.payment.gateway.ExternalPaymentClient;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.wallet.domain.Wallet;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentTransactionService transaction;
    private final ExternalPaymentClient gateway;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public Payment pay(PaymentCommand command) {
        Optional<Payment> existing = transaction.findExisting(command.paymentNo());
        if (existing.isPresent()) {
            return resolveDuplicate(existing.get());
        }

        Wallet wallet = transaction.loadWallet(command.walletId());
        if (!wallet.supports(command.currency())) {
            throw new ApiException(ResponseCode.INVALID_REQUEST);
        }

        Payment payment = transaction.openPending(
                command.paymentNo(), command.walletId(), command.amount(), command.currency());

        if (!wallet.canAfford(command.amount())) {
            return transaction.rejectWithoutGateway(payment, FailureReason.INSUFFICIENT_BALANCE);
        }

        ExternalApproval approval = approve(payment);
        return transaction.settle(payment, approval);
    }

    private Payment resolveDuplicate(Payment existing) {
        if (existing.isTerminal()) {
            return existing;
        }
        throw new ApiException(ResponseCode.DUPLICATE_PAYMENT_NO);
    }

    private ExternalApproval approve(Payment payment) {
        payment.markRequested(Instant.now(clock));

        Timer.Sample sample = metrics.startGatewayTimer();
        try {
            ExternalApproval approval = gateway.approve(new ExternalApprovalRequest(
                    payment.paymentNo(), payment.amount(), payment.currency()));
            metrics.stopGatewayTimer(sample, approval.result().name());
            return approval;
        } catch (RuntimeException e) {
            metrics.stopGatewayTimer(sample, "EXCEPTION");
            return ExternalApproval.inDoubt(e.getClass().getSimpleName());
        }
    }
}
