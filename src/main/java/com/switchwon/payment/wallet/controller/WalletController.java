package com.switchwon.payment.wallet.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.wallet.controller.dto.ChargeRequest;
import com.switchwon.payment.wallet.controller.dto.ChargeResponse;
import com.switchwon.payment.wallet.controller.dto.WalletResponse;
import com.switchwon.payment.wallet.service.WalletChargeService;
import com.switchwon.payment.wallet.service.WalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지갑", description = "지갑 잔액 조회와 충전")
@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletQueryService walletQueryService;
    private final WalletChargeService walletChargeService;

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

    @Operation(
            summary = "지갑 충전",
            description = """
                    지갑에 잔액을 더한다. 결제(차감)와 짝을 이루는 반대 방향 기능이다.

                    충전은 외부 게이트웨이를 거치지 않는다. 이력 기록과 잔액 증가가 한 트랜잭션에서 함께 끝나므로
                    결제와 달리 결과 미상 상태가 존재하지 않는다.

                    같은 충전번호로 다시 요청하면 재충전 없이 최초 이력을 그대로 돌려준다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "충전 성공. 같은 번호의 재요청도 최초 이력으로 200을 반환한다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 형식이 올바르지 않거나 지갑 통화와 다르다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "지갑을 찾을 수 없다")
    })
    @PostMapping("/api/v1/wallets/{walletId}/charge")
    public ApiResponse<ChargeResponse> charge(@PathVariable Long walletId,
                                              @Valid @RequestBody ChargeRequest request) {
        return ApiResponse.success(
                ChargeResponse.from(walletChargeService.charge(request.toCommand(walletId))));
    }
}
