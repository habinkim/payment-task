package com.switchwon.payment.payment.controller.dto;

import com.switchwon.payment.payment.domain.FailureReason;
import com.switchwon.payment.payment.domain.Payment;
import com.switchwon.payment.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaymentResponse", description = "결제 처리 결과")
public record PaymentResponse(

        @Schema(description = "결제번호", example = "PAY-20260811-001")
        String merchantPaymentNo,

        @Schema(description = "대상 지갑", example = "1")
        Long walletId,

        @Schema(description = "결제 금액", example = "100.0000")
        BigDecimal amount,

        @Schema(description = "통화", example = "USD")
        String currency,

        @Schema(
                description = """
                        결제 상태.
                        `COMPLETED` 승인과 차감이 모두 끝났다.
                        `FAILED` 실패가 확정됐다. 사유가 함께 기록된다.
                        `UNKNOWN` 게이트웨이 승인 여부를 알 수 없다. 잔액을 차감하지 않았으며 조회로 확정해야 한다.
                        """,
                example = "COMPLETED")
        PaymentStatus status,

        @Schema(
                description = "실패 사유. 실패한 경우에만 채워진다.",
                example = "null")
        FailureReason failureReason,

        @Schema(
                description = """
                        다시 보내면 결과가 달라질 수 있는지. CS가 고객에게 재시도를 안내할 기준이다.
                        서버 오류와 타임아웃은 재시도할 수 있고, 잔액 부족과 승인 거절은 다시 보내도 같다.
                        """,
                example = "null")
        Boolean retriable,

        @Schema(
                description = "게이트웨이가 발급한 거래 식별자. 문의가 들어왔을 때 게이트웨이 측 기록과 대조하는 데 쓴다.",
                example = "TXN-8F2A1C3D")
        String externalTransactionId,

        @Schema(description = "게이트웨이 응답 코드", example = "0000")
        String externalResponseCode,

        @Schema(description = "게이트웨이 호출 시각", example = "2026-08-11T00:00:00Z")
        Instant requestedAt,

        @Schema(description = "게이트웨이 응답 수신 시각. 요청 시각과의 차이가 곧 소요 시간이다.", example = "2026-08-11T00:00:01Z")
        Instant respondedAt,

        @Schema(description = "원장 기록 시각. 목록 조회는 이 값의 내림차순으로 정렬된다.", example = "2026-08-11T00:00:00Z")
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.merchantPaymentNo(),
                payment.walletId(),
                payment.amount(),
                payment.currency(),
                payment.status(),
                payment.failureReason(),
                payment.retriable(),
                payment.externalTransactionId(),
                payment.externalResponseCode(),
                payment.requestedAt(),
                payment.respondedAt(),
                payment.createdAt());
    }
}
