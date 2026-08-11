package com.switchwon.payment.wallet.service;

import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.error.ApiException;
import com.switchwon.payment.wallet.domain.Wallet;
import com.switchwon.payment.wallet.domain.WalletCharge;
import com.switchwon.payment.wallet.domain.WalletChargeStore;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalletChargeServiceTest {

    private static final String CHARGE_NO = "CHG-001";
    private static final Long WALLET_ID = 1L;
    private static final BigDecimal AMOUNT = new BigDecimal("500.0000");

    @Mock
    private WalletChargeStore chargeStore;

    @Mock
    private WalletStore walletStore;

    @InjectMocks
    private WalletChargeService service;

    private ChargeCommand command(String currency) {
        return new ChargeCommand(CHARGE_NO, WALLET_ID, AMOUNT, currency);
    }

    private Wallet wallet(String currency) {
        return new Wallet(WALLET_ID, currency, new BigDecimal("1000.0000"));
    }

    private WalletCharge charge() {
        return WalletCharge.restore(CHARGE_NO, WALLET_ID, AMOUNT, "USD", Instant.parse("2026-08-12T00:00:00Z"));
    }

    @Test
    @DisplayName("충전하면 이력을 남기고 잔액을 늘린다")
    void chargesAndRecordsHistory() {
        given(chargeStore.findByChargeNo(CHARGE_NO)).willReturn(Optional.empty());
        given(walletStore.findById(WALLET_ID)).willReturn(Optional.of(wallet("USD")));
        given(chargeStore.append(any())).willReturn(charge());
        given(walletStore.charge(WALLET_ID, AMOUNT)).willReturn(true);

        WalletCharge result = service.charge(command("USD"));

        assertThat(result.chargeNo()).isEqualTo(CHARGE_NO);
        verify(chargeStore).append(any());
        verify(walletStore).charge(WALLET_ID, AMOUNT);
    }

    @Test
    @DisplayName("같은 충전번호로 다시 요청하면 재충전하지 않는다")
    void duplicateChargeDoesNotChargeAgain() {
        given(chargeStore.findByChargeNo(CHARGE_NO)).willReturn(Optional.of(charge()));

        WalletCharge result = service.charge(command("USD"));

        assertThat(result.chargeNo()).isEqualTo(CHARGE_NO);
        verify(walletStore, never()).charge(anyLong(), any());
        verify(chargeStore, never()).append(any());
    }

    @Test
    @DisplayName("없는 지갑에 충전하면 찾을 수 없다고 응답한다")
    void missingWalletIsRejected() {
        given(chargeStore.findByChargeNo(CHARGE_NO)).willReturn(Optional.empty());
        given(walletStore.findById(WALLET_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.charge(command("USD")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.WALLET_NOT_FOUND);

        verify(chargeStore, never()).append(any());
        verify(walletStore, never()).charge(anyLong(), any());
    }

    @Test
    @DisplayName("지갑 통화와 다른 통화로 충전하면 거부한다")
    void currencyMismatchIsRejected() {
        given(chargeStore.findByChargeNo(CHARGE_NO)).willReturn(Optional.empty());
        given(walletStore.findById(WALLET_ID)).willReturn(Optional.of(wallet("JPY")));

        assertThatThrownBy(() -> service.charge(command("USD")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.INVALID_REQUEST);

        verify(chargeStore, never()).append(any());
        verify(walletStore, never()).charge(anyLong(), any());
    }

    @Test
    @DisplayName("이력을 남긴 뒤 잔액 증가가 실패하면 충전을 실패로 끝낸다")
    void failedBalanceUpdateIsRejected() {
        given(chargeStore.findByChargeNo(CHARGE_NO)).willReturn(Optional.empty());
        given(walletStore.findById(WALLET_ID)).willReturn(Optional.of(wallet("USD")));
        given(chargeStore.append(any())).willReturn(charge());
        given(walletStore.charge(WALLET_ID, AMOUNT)).willReturn(false);

        assertThatThrownBy(() -> service.charge(command("USD")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).responseCode())
                .isEqualTo(ResponseCode.WALLET_NOT_FOUND);
    }
}
