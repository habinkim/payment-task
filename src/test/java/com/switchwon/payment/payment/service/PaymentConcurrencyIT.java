package com.switchwon.payment.payment.service;

import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
class PaymentConcurrencyIT {

    private static final Long POOR_WALLET = 2L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WalletStore walletStore;

    private BigDecimal balance() {
        return walletStore.findById(POOR_WALLET).orElseThrow().balance();
    }

    @BeforeEach
    void restoreBalance() {
        BigDecimal current = balance();
        BigDecimal target = new BigDecimal("10.0000");
        if (current.compareTo(target) < 0) {
            walletStore.charge(POOR_WALLET, target.subtract(current));
        } else if (current.compareTo(target) > 0) {
            walletStore.deductIfEnough(POOR_WALLET, current.subtract(target));
        }
    }

    private List<Throwable> runConcurrently(int threads, List<Runnable> tasks) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Runnable task : tasks) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.run();
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
        return errors;
    }

    @Test
    @DisplayName("같은 지갑에 동시 결제가 들어와도 잔액이 음수가 되지 않는다")
    void concurrentPaymentsNeverOverdraw() throws InterruptedException {
        AtomicInteger completed = new AtomicInteger();
        List<Runnable> tasks = List.of(
                () -> countIfCompleted(completed, "CONC-A-" + System.nanoTime()),
                () -> countIfCompleted(completed, "CONC-B-" + System.nanoTime()));

        runConcurrently(2, tasks);

        assertThat(balance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(completed.get()).isEqualTo(1);
    }

    private void countIfCompleted(AtomicInteger counter, String paymentNo) {
        var payment = paymentService.pay(
                new PaymentCommand(paymentNo, POOR_WALLET, new BigDecimal("8.0000"), "USD"));
        if (payment.status() == PaymentStatus.COMPLETED) {
            counter.incrementAndGet();
        }
    }

    @Test
    @DisplayName("같은 결제번호로 동시 요청이 들어와도 한 번만 처리된다")
    void concurrentDuplicateProcessesOnce() throws InterruptedException {
        String paymentNo = "CONC-DUP-" + System.nanoTime();
        BigDecimal before = balance();
        AtomicInteger succeeded = new AtomicInteger();

        List<Runnable> tasks = List.of(
                () -> countIfCompleted(succeeded, paymentNo),
                () -> countIfCompleted(succeeded, paymentNo));

        runConcurrently(2, tasks);

        assertThat(succeeded.get()).isLessThanOrEqualTo(1);
        assertThat(balance()).isGreaterThanOrEqualTo(before.subtract(new BigDecimal("8.0000")));
    }
}
