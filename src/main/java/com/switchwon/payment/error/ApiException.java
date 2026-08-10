package com.switchwon.payment.error;

import com.switchwon.payment.common.ResponseCode;

public class ApiException extends RuntimeException {

    private final ResponseCode responseCode;

    public ApiException(ResponseCode responseCode) {
        super(responseCode.message());
        this.responseCode = responseCode;
    }

    public ResponseCode responseCode() {
        return responseCode;
    }
}
