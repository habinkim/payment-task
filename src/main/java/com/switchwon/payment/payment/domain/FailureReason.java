package com.switchwon.payment.payment.domain;

public enum FailureReason {

    /** 지갑 잔액이 결제 금액보다 적다. */
    INSUFFICIENT_BALANCE,

    /** 게이트웨이가 승인을 거절했다. */
    PAYMENT_DECLINED,

    /**
     * 외부 또는 내부 장애다.
     * 재시도 가능 여부는 이 값만으로 판정할 수 없어 Payment 가 별도 필드로 함께 기록한다.
     */
    SYSTEM_ERROR
}
