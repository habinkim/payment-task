package com.switchwon.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String DESCRIPTION = """
            가상의 3rd party 결제 게이트웨이를 연동하는 결제 시스템이다.
            결제 원장을 먼저 기록하고, 게이트웨이 승인을 거쳐, 지갑 잔액을 차감한다.

            ## 시나리오를 직접 눌러보려면

            게이트웨이는 모의 구현이며 `merchantPaymentNo` 접두어로 결과가 결정된다.
            요청 스펙을 바꾸지 않고 시나리오를 지정할 수 있도록 한 규약이다.

            | 접두어 | 결과 | HTTP |
            |---|---|---|
            | 없음 | 정상 승인 | 200 |
            | `DECLINE-` | 게이트웨이 승인 거절 | 422 |
            | `ERR500-` | 서버 오류. 재시도 가능 | 500 |
            | `ERR400-` | 잘못된 요청. 재시도 불가 | 500 |
            | `TIMEOUT-` | 결과 미상. 조회로 확정 필요 | 202 |
            | `SLOW-` | 지연 후 승인 | 200 |

            예: `TIMEOUT-20260811-001` 로 요청하면 타임아웃 경로를 재현한다.

            ## 미리 준비된 지갑

            | walletId | 통화 | 잔액 | 용도 |
            |---|---|---|---|
            | 1 | USD | 1000.0000 | 정상 결제 |
            | 2 | USD | 10.0000 | 잔액 부족 재현 |
            | 3 | JPY | 50000.0000 | 통화 불일치 재현 |

            인메모리 DB를 쓰므로 앱을 재기동하면 초기 상태로 돌아간다.

            ## 결제 실패를 다루는 방식

            실패는 재시도 가능 여부로 나뉜다. 5xx와 타임아웃은 재시도할 수 있고,
            4xx와 승인 거절, 잔액 부족은 다시 보내도 결과가 같다. 응답의 `retriable` 이 이를 알려준다.

            타임아웃은 실패가 아니라 **결과 미상**으로 다룬다. 요청이 게이트웨이에 도달해
            승인까지 났는데 응답만 유실됐을 수 있기 때문이다. 이런 건은 `UNKNOWN` 으로 남기고
            잔액을 차감하지 않으며, 조회를 통해 확정한다.
            """;

    @Bean
    public OpenAPI paymentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("스위치원 결제 API")
                .version("v1")
                .description(DESCRIPTION));
    }
}
