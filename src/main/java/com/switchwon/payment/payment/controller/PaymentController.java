package com.switchwon.payment.payment.controller;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.payment.controller.dto.PaymentRequest;
import com.switchwon.payment.payment.controller.dto.PaymentResponse;
import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

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
