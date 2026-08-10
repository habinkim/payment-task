package com.switchwon.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시각을 빈으로 주입해 테스트에서 고정 시각으로 교체할 수 있게 한다.
 * 결제 원장의 요청 시각과 응답 시각이 검증 대상이라 결정론이 필요하다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
