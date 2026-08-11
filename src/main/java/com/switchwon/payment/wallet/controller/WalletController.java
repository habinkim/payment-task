package com.switchwon.payment.wallet.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.wallet.controller.dto.WalletResponse;
import com.switchwon.payment.wallet.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지갑", description = "지갑 잔액 조회")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;

    @Operation(
            summary = "지갑 조회",
            description = """
                    현재 잔액과 통화를 조회한다. 결제 전후 잔액을 확인하거나 통화를 맞출 때 쓴다.

                    미리 준비된 지갑은 셋이다.
                    1번 USD 1000, 2번 USD 10(잔액 부족 재현용), 3번 JPY 50000(통화 불일치 재현용).
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않는 지갑")
    })
    @GetMapping("/api/v1/wallets/{walletId}")
    public ApiResponse<WalletResponse> get(@PathVariable Long walletId) {
        return ApiResponse.success(WalletResponse.from(walletQueryService.getById(walletId)));
    }
}
