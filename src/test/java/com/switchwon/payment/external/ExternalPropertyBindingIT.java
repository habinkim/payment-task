package com.switchwon.payment.external;

import com.switchwon.payment.external.infra.MockExternalPaymentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExternalPropertyBindingIT {

    @Autowired
    private Environment environment;

    @Autowired
    private ExternalChaosProperties chaosProperties;

    @Autowired
    private ExternalPaymentClient client;

    @Test
    @DisplayName("외부 연동 설정은 payment.external 아래에 있다")
    void configurationLivesUnderExternalPrefix() {
        assertThat(environment.getProperty("payment.external.mode"))
                .as("설정 키를 옮기면 이 값이 사라진다")
                .isEqualTo("mock");
        assertThat(environment.getProperty("payment.gateway.mode"))
                .as("옛 키가 남아 있으면 안 된다")
                .isNull();
    }

    @Test
    @DisplayName("장애 주입 설정이 프로퍼티에서 바인딩된다")
    void chaosPropertiesAreBound() {
        assertThat(chaosProperties).isNotNull();
        assertThat(chaosProperties.enabled()).isFalse();
    }

    @Test
    @DisplayName("모의 구현이 외부 결제 연동으로 등록된다")
    void mockClientIsRegistered() {
        assertThat(client).isInstanceOf(MockExternalPaymentClient.class);
    }
}
