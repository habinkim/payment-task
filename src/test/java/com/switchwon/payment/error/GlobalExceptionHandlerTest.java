package com.switchwon.payment.error;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.wallet.domain.InsufficientBalanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("도메인 불변식 위반은 잘못된 요청으로 변환된다")
    void domainInvariantBecomesInvalidRequest() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDomainInvariant(new IllegalArgumentException("amount는 0보다 커야 합니다"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ResponseCode.INVALID_REQUEST.name());
    }

    @Test
    @DisplayName("잔액 부족 예외는 422로 변환된다")
    void insufficientBalanceBecomesUnprocessable() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleInsufficientBalance(
                new InsufficientBalanceException(1L, new BigDecimal("10"), new BigDecimal("100")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().code()).isEqualTo(ResponseCode.INSUFFICIENT_BALANCE.name());
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 오류로 변환되고 상세가 노출되지 않는다")
    void unexpectedBecomesSystemError() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new RuntimeException("connection pool exhausted at line 42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ResponseCode.SYSTEM_ERROR.name());
        assertThat(response.getBody().message()).doesNotContain("connection pool");
    }

    @Test
    @DisplayName("응답 코드를 가진 예외는 그 코드와 상태로 변환된다")
    void apiExceptionUsesItsOwnCode() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleApiException(new ApiException(ResponseCode.DUPLICATE_PAYMENT_NO));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ResponseCode.DUPLICATE_PAYMENT_NO.name());
    }
}
