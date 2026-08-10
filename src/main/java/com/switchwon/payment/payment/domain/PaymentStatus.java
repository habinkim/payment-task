package com.switchwon.payment.payment.domain;

public enum PaymentStatus {

    /** 원장 선기록. 외부 승인 결과가 반영되기 전 상태다. */
    PENDING,

    /** 승인과 차감이 모두 성공했다. */
    COMPLETED,

    /** 실패가 확정됐다. 사유가 반드시 함께 기록된다. */
    FAILED,

    /**
     * 외부 승인 여부를 알 수 없다.
     * 타임아웃처럼 요청은 나갔으나 응답을 받지 못한 경우이며, 조회로 확정해야 한다.
     */
    UNKNOWN;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
