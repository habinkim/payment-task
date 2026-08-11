package com.switchwon.payment.payment.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.payment.controller.dto.PaymentResponse;
import com.switchwon.payment.payment.service.ReconcileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "운영", description = "운영자가 직접 실행하는 정합성 확인")
@RestController
@RequiredArgsConstructor
public class AdminPaymentController {

    private final ReconcileService reconcileService;

    @Operation(
            summary = "정합성 확인",
            description = """
                    결과 미상으로 남은 결제를 게이트웨이에 다시 물어 상태를 확정한다.
                    승인으로 확인되면 그때 지갑에서 차감하고, 거절이거나 게이트웨이에 기록이 없으면 실패로 닫는다.

                    게이트웨이가 여전히 답을 주지 못하면 아무것도 바꾸지 않고 다음 시도를 기다린다.
                    결과 미상이 아닌 결제에 호출하면 현재 상태를 그대로 반환한다.

                    확인 대상은 `GET /api/v1/payments?status=UNKNOWN` 으로 찾을 수 있다.
                    스케줄러가 같은 로직을 주기적으로 실행하므로 보통은 직접 부를 일이 없다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "확인을 수행했다. 결과는 본문의 status 로 판단한다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않는 결제번호")
    })
    @PostMapping("/api/v1/admin/payments/{paymentNo}/reconcile")
    public ApiResponse<PaymentResponse> reconcile(@PathVariable String paymentNo) {
        return ApiResponse.success(PaymentResponse.from(reconcileService.reconcile(paymentNo)));
    }
}
