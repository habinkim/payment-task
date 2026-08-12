package com.switchwon.payment.payment.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.external.ExternalApproval;
import com.switchwon.payment.external.ExternalInquiry;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConcurrentSettlementIT {

    private static final Long RICH_WALLET = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("3.0000");

    @Autowired
    private PaymentTransactionService transaction;

    @Autowired
    private WalletStore walletStore;

    private BigDecimal balance() {
        return walletStore.findById(RICH_WALLET).orElseThrow().balance();
    }

    private Payment unknownPayment(String prefix) {
        Payment payment = transaction.openPending(
                prefix + System.nanoTime(), RICH_WALLET, AMOUNT, "USD");
        return transaction.settle(payment, ExternalApproval.inDoubt("TIMEOUT"));
    }

    private int runConcurrently(List<Runnable> tasks) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        AtomicInteger succeeded = new AtomicInteger();
        List<Throwable> errors = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            for (Runnable task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.run();
                        succeeded.incrementAndGet();
                    } catch (Throwable t) {
                        synchronized (errors) {
                            errors.add(t);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            done.await(20, TimeUnit.SECONDS);
        }
        return succeeded.get();
    }

    @Test
    @DisplayName("두 인스턴스가 같은 결과 미상 건을 동시에 확정해도 한 번만 차감된다")
    void concurrentConfirmDeductsOnce() throws InterruptedException {
        Payment stored = unknownPayment("TIMEOUT-CAS-");
        String merchantPaymentNo = stored.merchantPaymentNo();
        BigDecimal before = balance();

        Payment copyA = transaction.findExisting(merchantPaymentNo).orElseThrow();
        Payment copyB = transaction.findExisting(merchantPaymentNo).orElseThrow();
        assertThat(copyA.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(copyB.status()).isEqualTo(PaymentStatus.UNKNOWN);

        ExternalInquiry approved = ExternalInquiry.approved("TXN-CAS", "0000");
        int succeeded = runConcurrently(List.of(
                () -> transaction.confirm(copyA, approved),
                () -> transaction.confirm(copyB, approved)));

        assertThat(succeeded).as("확정은 한 번만 성공해야 한다").isEqualTo(1);
        assertThat(before.subtract(balance()))
                .as("잔액은 결제 금액만큼만 줄어야 한다")
                .isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("잔액이 넉넉해도 동시 확정이 이중 차감되지 않는다")
    void ampleBalanceDoesNotHideDoubleDeduction() throws InterruptedException {
        BigDecimal available = balance();
        assertThat(available)
                .as("이 검증은 잔액이 결제액의 두 배를 넘어야 의미가 있다")
                .isGreaterThan(AMOUNT.multiply(BigDecimal.TWO));

        Payment stored = unknownPayment("TIMEOUT-AMPLE-");
        String merchantPaymentNo = stored.merchantPaymentNo();
        BigDecimal before = balance();

        Payment copyA = transaction.findExisting(merchantPaymentNo).orElseThrow();
        Payment copyB = transaction.findExisting(merchantPaymentNo).orElseThrow();
        ExternalInquiry approved = ExternalInquiry.approved("TXN-AMPLE", "0000");

        runConcurrently(List.of(
                () -> transaction.confirm(copyA, approved),
                () -> transaction.confirm(copyB, approved)));

        assertThat(before.subtract(balance())).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("두 인스턴스가 같은 처리 중 건을 동시에 승인해도 한 번만 차감된다")
    void concurrentSettleDeductsOnce() throws InterruptedException {
        Payment stored = transaction.openPending(
                "PAY-CAS-" + System.nanoTime(), RICH_WALLET, AMOUNT, "USD");
        String merchantPaymentNo = stored.merchantPaymentNo();
        BigDecimal before = balance();

        Payment copyA = transaction.findExisting(merchantPaymentNo).orElseThrow();
        Payment copyB = transaction.findExisting(merchantPaymentNo).orElseThrow();

        ExternalApproval approval = ExternalApproval.approved("TXN-SETTLE", "0000");
        int succeeded = runConcurrently(List.of(
                () -> transaction.settle(copyA, approval),
                () -> transaction.settle(copyB, approval)));

        assertThat(succeeded).isEqualTo(1);
        assertThat(before.subtract(balance())).isEqualByComparingTo(AMOUNT);
    }

    @Test
    @DisplayName("이미 확정된 결제를 다시 확정하려 하면 거부한다")
    void confirmingSettledPaymentIsRejected() {
        Payment stored = unknownPayment("TIMEOUT-SETTLED-");
        Payment stale = transaction.findExisting(stored.merchantPaymentNo()).orElseThrow();

        ExternalInquiry approved = ExternalInquiry.approved("TXN-FIRST", "0000");
        transaction.confirm(stored, approved);
        BigDecimal afterFirst = balance();

        assertThatThrownBy(() -> transaction.confirm(stale, ExternalInquiry.approved("TXN-SECOND", "0000")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.PAYMENT_ALREADY_SETTLED);

        assertThat(balance()).as("거부된 확정은 잔액을 건드리지 않는다").isEqualByComparingTo(afterFirst);
    }
}
