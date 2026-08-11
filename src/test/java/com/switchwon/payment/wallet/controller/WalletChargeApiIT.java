package com.switchwon.payment.wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchwon.payment.wallet.controller.dto.ChargeRequest;
import com.switchwon.payment.wallet.domain.WalletChargeStore;
import com.switchwon.payment.wallet.domain.WalletStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletChargeApiIT {

    private static final Long RICH_WALLET = 1L;
    private static final Long JPY_WALLET = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletStore walletStore;

    @Autowired
    private WalletChargeStore chargeStore;

    private BigDecimal balance(Long walletId) {
        return walletStore.findById(walletId).orElseThrow().balance();
    }

    private String body(String chargeNo, String amount, String currency) throws Exception {
        return objectMapper.writeValueAsString(new ChargeRequest(chargeNo, new BigDecimal(amount), currency));
    }

    private org.springframework.test.web.servlet.ResultActions charge(Long walletId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets/" + walletId + "/charge")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("충전하면 잔액이 늘고 이력이 남는다")
    void chargeIncreasesBalanceAndLeavesHistory() throws Exception {
        BigDecimal before = balance(RICH_WALLET);

        charge(RICH_WALLET, body("CHG-API-001", "500.0000", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.returnObject.chargeNo").value("CHG-API-001"))
                .andExpect(jsonPath("$.returnObject.walletId").value(RICH_WALLET))
                .andExpect(jsonPath("$.returnObject.currency").value("USD"))
                .andExpect(jsonPath("$.returnObject.createdAt").exists());

        assertThat(balance(RICH_WALLET)).isEqualByComparingTo(before.add(new BigDecimal("500.0000")));
        assertThat(chargeStore.findByChargeNo("CHG-API-001")).isPresent();
    }

    @Test
    @DisplayName("같은 충전번호로 다시 요청해도 잔액은 한 번만 늘어난다")
    void duplicateChargeIsIdempotent() throws Exception {
        BigDecimal before = balance(RICH_WALLET);
        String request = body("CHG-API-002", "100.0000", "USD");

        charge(RICH_WALLET, request).andExpect(status().isOk());
        BigDecimal afterFirst = balance(RICH_WALLET);

        charge(RICH_WALLET, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnObject.chargeNo").value("CHG-API-002"));

        assertThat(afterFirst).isEqualByComparingTo(before.add(new BigDecimal("100.0000")));
        assertThat(balance(RICH_WALLET)).isEqualByComparingTo(afterFirst);
    }

    @Test
    @DisplayName("없는 지갑에 충전하면 찾을 수 없다고 응답한다")
    void missingWalletReturnsNotFound() throws Exception {
        charge(9999L, body("CHG-API-003", "100.0000", "USD"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("지갑 통화와 다른 통화로 충전하면 거부한다")
    void currencyMismatchIsRejected() throws Exception {
        BigDecimal before = balance(JPY_WALLET);

        charge(JPY_WALLET, body("CHG-API-004", "100.0000", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(balance(JPY_WALLET)).isEqualByComparingTo(before);
        assertThat(chargeStore.findByChargeNo("CHG-API-004")).isEmpty();
    }

    @Test
    @DisplayName("0원을 충전하려 하면 거부한다")
    void zeroAmountIsRejected() throws Exception {
        charge(RICH_WALLET, body("CHG-API-005", "0", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("음수를 충전하려 하면 거부한다")
    void negativeAmountIsRejected() throws Exception {
        BigDecimal before = balance(RICH_WALLET);

        charge(RICH_WALLET, body("CHG-API-006", "-100.0000", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(balance(RICH_WALLET)).isEqualByComparingTo(before);
    }

    @Test
    @DisplayName("충전번호 형식이 올바르지 않으면 거부한다")
    void malformedChargeNoIsRejected() throws Exception {
        charge(RICH_WALLET, body("CHG_API_007", "100.0000", "USD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
