package com.switchwon.payment.wallet.infra;

import com.switchwon.payment.wallet.domain.WalletCharge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Entity
@Table(name = "wallet_charge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletChargeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "charge_no", nullable = false, updatable = false, length = 64)
    private String chargeNo;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private WalletChargeEntity(WalletCharge charge, Instant now) {
        this.chargeNo = charge.chargeNo();
        this.walletId = charge.walletId();
        this.amount = charge.amount();
        this.currency = charge.currency();
        this.createdAt = now;
    }

    public static WalletChargeEntity from(WalletCharge charge, Instant now) {
        return new WalletChargeEntity(charge, now);
    }

    public WalletCharge toDomain() {
        return WalletCharge.restore(chargeNo, walletId, amount, currency, createdAt);
    }
}
