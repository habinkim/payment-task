package com.switchwon.payment.error;

import com.switchwon.payment.common.ApiResponse;
import com.switchwon.payment.common.ResponseCode;
import com.switchwon.payment.wallet.domain.InsufficientBalanceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        ResponseCode code = ex.responseCode();
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception ex) {
        ResponseCode code = ResponseCode.INVALID_REQUEST;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        ResponseCode code = ResponseCode.NOT_FOUND;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainInvariant(IllegalArgumentException ex) {
        log.warn("도메인 불변식 위반이 요청 검증을 통과했습니다", ex);
        ResponseCode code = ResponseCode.INVALID_REQUEST;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientBalance(InsufficientBalanceException ex) {
        ResponseCode code = ResponseCode.INSUFFICIENT_BALANCE;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        ResponseCode code = ResponseCode.SYSTEM_ERROR;
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code));
    }
}
