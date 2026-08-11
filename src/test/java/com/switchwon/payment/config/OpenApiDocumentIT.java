package com.switchwon.payment.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentIT {

    private static final String DOC = "/v3/api-docs";
    private static final String PAYMENT_SCHEMA = "$.components.schemas.PaymentRequest.properties";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("OpenAPI 문서가 생성된다")
    void documentIsServed() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("스위치원 결제 API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    @DisplayName("문서 설명에 시나리오 접두어 규약이 담긴다")
    void descriptionExplainsScenarioPrefixes() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath("$.info.description").value(containsString("TIMEOUT-")))
                .andExpect(jsonPath("$.info.description").value(containsString("DECLINE-")))
                .andExpect(jsonPath("$.info.description").value(containsString("walletId")));
    }

    @Test
    @DisplayName("결제 엔드포인트가 문서에 포함된다")
    void paymentEndpointIsDocumented() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.summary").value("결제 요청"));
    }

    @Test
    @DisplayName("응답 코드가 상태별로 문서화된다")
    void responseCodesAreDocumented() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.200").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.202").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.409").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.422").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses.500").exists());
    }

    @Test
    @DisplayName("요청 필드에 예제 값이 담긴다")
    void requestFieldsHaveExamples() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath(PAYMENT_SCHEMA + ".paymentNo.example").value("PAY-20260811-001"))
                .andExpect(jsonPath(PAYMENT_SCHEMA + ".walletId.example").value(1))
                .andExpect(jsonPath(PAYMENT_SCHEMA + ".currency.example").value("USD"));
    }

    @Test
    @DisplayName("지갑 설명에 시드 데이터가 담겨 바로 눌러볼 수 있다")
    void walletDescriptionListsSeedData() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath(PAYMENT_SCHEMA + ".walletId.description")
                        .value(containsString("잔액 부족")));
    }

    @Test
    @DisplayName("문서에는 API 경로만 노출되고 문서 도구 자신은 빠진다")
    void documentExcludesScalarItself() throws Exception {
        mockMvc.perform(get(DOC))
                .andExpect(jsonPath("$.paths['/scalar']").doesNotExist())
                .andExpect(jsonPath("$.paths['/scalar/scalar.js']").doesNotExist());
    }

    @Test
    @DisplayName("Scalar 문서 화면이 열리고 우리 문서를 가리킨다")
    void scalarPageReferencesOurDocument() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"url\":\"/v3/api-docs\"")))
                .andExpect(content().string(not(containsString("registry.scalar.com"))))
                .andExpect(content().string(not(containsString("galaxy"))));
    }

    @Test
    @DisplayName("외부로 나가는 통신을 켜지 않는다")
    void telemetryIsDisabled() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(content().string(containsString("\"telemetry\":false")));
    }

    @Test
    @DisplayName("문서 화면이 외부 CDN에 의존하지 않는다")
    void scriptIsServedLocally() throws Exception {
        mockMvc.perform(get("/scalar"))
                .andExpect(content().string(containsString("src=\"scalar/scalar.js\"")))
                .andExpect(content().string(not(containsString("https://cdn."))));
    }

    @Test
    @DisplayName("Swagger UI는 비활성화되어 있다")
    void swaggerUiIsDisabled() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}
