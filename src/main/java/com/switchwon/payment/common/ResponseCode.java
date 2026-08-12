package com.switchwon.payment.common;

import org.springframework.http.HttpStatus;

public enum ResponseCode {

    OK(HttpStatus.OK, "정상적으로 처리되었습니다."),

    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "지갑 잔액이 부족합니다."),
    PAYMENT_DECLINED(HttpStatus.UNPROCESSABLE_ENTITY, "결제가 승인되지 않았습니다."),
    PAYMENT_IN_DOUBT(HttpStatus.ACCEPTED, "결제 결과를 확인하는 중입니다. 잠시 후 조회해 주세요."),

    DUPLICATE_PAYMENT_NO(HttpStatus.CONFLICT, "이미 처리 중인 결제번호입니다."),
    PAYMENT_ALREADY_SETTLED(HttpStatus.CONFLICT, "이미 확정된 결제입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 지갑입니다."),

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ResponseCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
