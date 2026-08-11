package com.switchwon.payment.payment.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.common.PageResponse;
import com.switchwon.payment.common.page.PageResult;
import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.controller.dto.PaymentResponse;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import com.switchwon.payment.payment.service.PaymentQueryService;
import com.switchwon.payment.payment.service.PaymentSearchCondition;
import com.switchwon.payment.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "결제", description = "결제 요청과 처리 결과")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentQueryService paymentQueryService;

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
    @PostMapping("/api/v1/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(@Valid @RequestBody PaymentRequest request) {
        Payment payment = paymentService.pay(request.toCommand());
        ResponseCode code = resolveCode(payment);

        return ResponseEntity.status(code.status())
                .body(ApiResponse.of(code, PaymentResponse.from(payment)));
    }

    @Operation(
            summary = "결제 단건 조회",
            description = """
                    결제번호로 조회한다. 서버가 발급한 식별자가 아니라 3rd party가 만든 번호를 쓰므로,
                    응답을 받지 못해 결과를 모르는 상황에서도 호출할 수 있다.

                    조회에 성공하면 결제 상태와 무관하게 200을 반환한다.
                    결제가 실패했는지는 본문의 status 와 failureReason 으로 판단한다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "존재하지 않는 결제번호")
    })
    @GetMapping("/api/v1/payments/{paymentNo}")
    public ApiResponse<PaymentResponse> get(@PathVariable String paymentNo) {
        return ApiResponse.success(PaymentResponse.from(paymentQueryService.getByPaymentNo(paymentNo)));
    }

    @Operation(
            summary = "결제 목록 조회",
            description = """
                    운영 모니터링용이다. 상태, 지갑, 생성 기간으로 걸러 본다.
                    정렬은 생성 시각 내림차순으로 고정하며, 한 번에 최대 100건까지 조회한다.

                    결과 미상으로 남은 건을 찾으려면 status=UNKNOWN 으로 조회한다.
                    """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "페이지 파라미터가 올바르지 않다")
    })
    @GetMapping("/api/v1/payments")
    public ApiResponse<PageResponse<PaymentResponse>> search(
            @Parameter(description = "결제 상태") @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "지갑 식별자") @RequestParam(required = false) Long walletId,
            @Parameter(description = "생성 시각 시작", example = "2026-08-11T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "생성 시각 종료", example = "2026-08-11T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @Parameter(description = "페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 최대 100") @RequestParam(defaultValue = "20") int size) {

        PageResult<Payment> found = paymentQueryService.search(
                new PaymentSearchCondition(status, walletId, from, to), page, size);

        return ApiResponse.success(PageResponse.of(found, PaymentResponse::from));
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
