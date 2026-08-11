package com.switchwon.payment.payment.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.controller.dto.PaymentResponse;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "결제 요청과 처리 결과")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "결제 요청",
            description = """
                    결제 원장을 먼저 기록하고, 잔액을 미리 확인한 뒤, 게이트웨이 승인을 거쳐 지갑에서 차감한다.

                    잔액이 부족하면 게이트웨이를 호출하지 않고 즉시 실패시킨다.
                    승인이 난 뒤에 차감이 실패하면 외부에서는 결제됐는데 내부에서는 실패한 상태가 되기 때문이다.

                    같은 결제번호로 다시 요청하면 재차감 없이 최초 결과를 돌려준다.
                    아직 처리 중인 건에 대해서는 409로 응답한다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "승인과 차감이 모두 성공했다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "게이트웨이 응답을 받지 못해 결과를 알 수 없다. 잔액은 차감하지 않았으며 조회로 확정해야 한다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 형식이 올바르지 않거나 지갑 통화와 다르다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "지갑을 찾을 수 없다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "같은 결제번호가 아직 처리 중이다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422", description = "잔액이 부족하거나 게이트웨이가 승인을 거절했다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "게이트웨이 오류 또는 내부 장애. retriable 로 재시도 가능 여부를 알 수 있다")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@Valid @RequestBody PaymentRequest request) {
        Payment payment = paymentService.pay(request.toCommand());
        ResponseCode code = resolveCode(payment);

        return ResponseEntity.status(code.status())
                .body(ApiResponse.of(code, PaymentResponse.from(payment)));
    }

    private ResponseCode resolveCode(Payment payment) {
        return switch (payment.status()) {
            case COMPLETED -> ResponseCode.OK;
            case UNKNOWN -> ResponseCode.PAYMENT_IN_DOUBT;
            case FAILED -> failureCode(payment.failureReason());
            case PENDING -> ResponseCode.PAYMENT_IN_DOUBT;
        };
    }

    private ResponseCode failureCode(FailureReason reason) {
        if (reason == null) {
            return ResponseCode.SYSTEM_ERROR;
        }
        return switch (reason) {
            case INSUFFICIENT_BALANCE -> ResponseCode.INSUFFICIENT_BALANCE;
            case PAYMENT_DECLINED -> ResponseCode.PAYMENT_DECLINED;
            case SYSTEM_ERROR -> ResponseCode.SYSTEM_ERROR;
        };
    }
}
