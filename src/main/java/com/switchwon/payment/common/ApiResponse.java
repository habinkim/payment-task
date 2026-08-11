package com.switchwon.payment.common;

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
