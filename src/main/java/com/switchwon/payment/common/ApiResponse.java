package com.switchwon.payment.common;

/**
 * 과제 명세가 지정한 공통 응답 규약이다.
 * 필드명 code, message, returnObject 는 명세를 그대로 따른다.
 */
public record ApiResponse<T>(String code, String message, T returnObject) {

    public static <T> ApiResponse<T> success(T returnObject) {
        return new ApiResponse<>(ResponseCode.OK.name(), ResponseCode.OK.message(), returnObject);
    }

    public static <T> ApiResponse<T> of(ResponseCode code, T returnObject) {
        return new ApiResponse<>(code.name(), code.message(), returnObject);
    }

    public static ApiResponse<Void> error(ResponseCode code) {
        return new ApiResponse<>(code.name(), code.message(), null);
    }
}
