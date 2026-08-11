package com.switchwon.payment.wallet.service;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WalletChargeConcurrencyIT {

    private static final Long RICH_WALLET = 1L;
    private static final BigDecimal ONE = new BigDecimal("1.0000");

    @Autowired
    private WalletChargeService chargeService;

    @Autowired
    private WalletStore walletStore;

    private BigDecimal balance() {
        return walletStore.findById(RICH_WALLET).orElseThrow().balance();
    }

    private void runConcurrently(int threads, List<Runnable> tasks) throws InterruptedException {
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
    }

    private void chargeQuietly(String chargeNo) {
        try {
            chargeService.charge(new ChargeCommand(chargeNo, RICH_WALLET, ONE, "USD"));
        } catch (RuntimeException ignored) {
            // 동시 중복은 예외로 끝날 수 있다. 잔액이 한 번만 늘었는지가 검증 대상이다.
        }
    }

    @Test
    @DisplayName("서로 다른 번호로 동시에 충전하면 전부 반영된다")
    void concurrentChargesAllApply() throws InterruptedException {
        int threads = 10;
        BigDecimal before = balance();
        String prefix = "CONC-CHG-" + System.nanoTime() + "-";

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String chargeNo = prefix + i;
            tasks.add(() -> chargeQuietly(chargeNo));
        }

        runConcurrently(threads, tasks);

        assertThat(balance()).isEqualByComparingTo(before.add(ONE.multiply(new BigDecimal(threads))));
    }

    @Test
    @DisplayName("같은 번호로 동시에 충전해도 잔액은 한 번만 늘어난다")
    void concurrentDuplicateChargesOnce() throws InterruptedException {
        BigDecimal before = balance();
        String chargeNo = "CONC-DUP-CHG-" + System.nanoTime();

        List<Runnable> tasks = List.of(
                () -> chargeQuietly(chargeNo),
                () -> chargeQuietly(chargeNo));

        runConcurrently(2, tasks);

        assertThat(balance()).isEqualByComparingTo(before.add(ONE));
    }
}
