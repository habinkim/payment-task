package com.switchwon.payment.gateway;

public enum GatewayResult {

    /** 승인됐다. 지갑 차감으로 진행한다. */
    APPROVED,

    /** 게이트웨이가 거절했다. 재시도해도 결과가 같다. */
    DECLINED,

    /** 호출이 실패했고 승인되지 않았음이 확실하다. */
    FAILED,

    /** 응답을 받지 못해 승인 여부를 알 수 없다. 조회로 확정해야 한다. */
    IN_DOUBT
}
