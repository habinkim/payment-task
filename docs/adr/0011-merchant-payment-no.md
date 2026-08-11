# 0011. 결제번호와 외부 연동 타입에 방향을 드러낸다

상태: Accepted

[0002](0002-payment-no-as-idempotency-key.md)를 대체한다. 0002의 결정(3rd party가 발급하고 멱등 키를 겸한다)은 그대로 유효하며, **이름만 바꾼다.**

## 맥락

이름이 역할을 거꾸로 가르치고 있었다.

명세 개요는 "가상의 3rd party **에게** 결제 API를 **제공**"이라고 못박는다. 시퀀스 다이어그램도 `Client → Backend` 화살표가 결제 요청 하나뿐이고 그 앞에 번호를 발급받는 왕복이 없다. **우리가 게이트웨이(PG)이고** 3rd party는 우리를 호출하는 가맹점이다.

그런데 코드는 이렇게 읽혔다.

```java
private final PaymentGatewayClient gateway;   // 우리가 게이트웨이인데 필드명이 gateway
```

실제로 이 이름 때문에 "결제 요청할 때 어떻게 `paymentNo`를 알고 Gateway API를 호출하지?"라는 오해가 생겼다. 우리가 PG를 호출하는 가맹점이라고 읽힌 것이다. 이름이 구조를 잘못 가르치면 코드를 읽는 사람이 매번 되짚어야 한다.

`paymentNo`도 같은 문제였다. 3rd party가 발급해 보내는 값인데 이름만 보면 누가 만든 번호인지 알 수 없다. 서버가 채번했다고 읽어도 문법상 어긋나지 않는다.

## 결정

**두 방향을 이름에 박아넣는다.**

```
3rd party(가맹점) ──merchantPaymentNo──> [우리 = PG] ──> External Payment API
                     받는 값                                  externalTransactionId
                                                                받는 값
```

### `paymentNo` → `merchantPaymentNo`

발급 주체를 이름에 담는다. `merchant`는 결제 도메인의 표준 용어이고 `client`보다 역할이 분명하다.

0002가 세운 원칙과 충돌하지 않는다.

> "이름을 `idempotencyKey`가 아니라 `paymentNo`로 정한 것은 의도적이다. 필드가 하는 일이 아니라 필드가 무엇인지를 이름에 담는다."

`merchantPaymentNo`는 여전히 "무엇인지"다. 발급 주체를 더했을 뿐 구현 메커니즘을 박아넣지 않았다.

DB 컬럼도 `payment.merchant_payment_no`, 제약도 `uk_merchant_payment_no`로 맞춘다. H2 인메모리라 매 기동 시 스키마가 새로 만들어지므로 V4 마이그레이션 대신 V1을 직접 고쳤다. 배포된 DB가 없어 체크섬을 보존할 이유가 없고, V4를 만들면 존재한 적 없는 컬럼을 리네임하는 이력만 남는다.

### `gateway` → `external`

패키지와 타입, 설정 키를 함께 옮긴다.

| 현재 | 변경 |
|---|---|
| `com.switchwon.payment.gateway` | `com.switchwon.payment.external` |
| `PaymentGatewayClient` | `ExternalPaymentClient` |
| `GatewayApproval` / `GatewayResult` | `ExternalApproval` / `ExternalApprovalResult` |
| `GatewayInquiry` / `InquiryResult` | `ExternalInquiry` / `ExternalInquiryResult` |
| `payment.gateway.*` (yaml) | `payment.external.*` |

`external`은 새 용어가 아니다. `externalTransactionId`, `external_transaction_id`가 이미 쓰이고 있어 **기존 어휘를 일관되게 확장**하는 것이다.

`GatewayResult`를 `ExternalResult`가 아니라 `ExternalApprovalResult`로 한 이유는 두 enum의 값이 다르기 때문이다. 승인은 `IN_DOUBT`를, 조회는 `NOT_FOUND`·`STILL_UNKNOWN`을 갖는다. `ExternalResult`는 조회에도 쓰이는 것처럼 읽혀 오히려 정보가 준다.

### `chargeNo`는 바꾸지 않는다

충전의 `chargeNo`는 그대로 둔다. 결제는 3rd party가 호출한다고 명세에 명시돼 있지만 **충전의 호출 주체는 명세에 없다.** 근거 없는 이름을 스키마에 박지 않는다.

결과적으로 `merchant_payment_no`와 `charge_no`가 비대칭이 된다. 충전 호출 주체가 확정되면 그때 맞춘다.

## 결과

역할이 이름에서 드러난다. `ExternalPaymentClient`를 보면 "우리가 부르는 바깥"임이, `merchantPaymentNo`를 보면 "가맹점이 준 번호"임이 읽힌다.

API 계약이 바뀌었다. 요청·응답 JSON 키와 URL 경로 변수가 전부 `merchantPaymentNo`다. 과제 원문(`docs/requirement/`)에 필드명 명세가 없어 충돌은 없다.

**설정 키가 조용한 실패 지점이었다.** yaml 키는 클래스명과 독립이라 자동으로 바뀌지 않는데, 틀려도 컴파일은 통과한다. `@ConditionalOnProperty(matchIfMissing = true)` 때문에 Mock 구현은 계속 뜨고 장애 주입만 비활성화된다.

실제로 확인했다. yaml 키만 옛 이름으로 되돌렸더니 **215건이 전부 통과했다.** 기존 테스트가 이 설정을 전혀 검증하지 않았다는 뜻이다. `ExternalPropertyBindingIT`를 추가해 메웠고, 이제 같은 결함이 1건을 실패시킨다.

다중 연동 시 복합 키는 `(merchantId, merchantPaymentNo)`가 된다. `merchantId`는 가맹점 식별자, `merchantPaymentNo`는 가맹점이 부여한 번호로 서로 다른 개념이다.

## 검토한 대안

- **`clientPaymentNo`.** "호출자가 준 번호"만 말하므로 가정이 적다. 그러나 결제 도메인에서 `merchant`가 표준 용어라 색이 약하다.

- **`externalTransactionId`도 `clientTransactionId`로.** 처음 제안됐으나 방향이 반대다. 이 값은 우리가 **외부 결제사에서 받아오는** 것이라 `client`를 붙이면 3rd party가 준 값으로 읽힌다. `merchantPaymentNo`와 정확히 반대 방향인데 이름이 뒤바뀐다.

- **타입명만 바꾸고 패키지·설정은 유지.** 변경 범위가 작지만 `import com.switchwon.payment.gateway.ExternalPaymentClient`가 되어 반쪽짜리다.

- **`chargeNo`도 `merchantChargeNo`로.** 대칭은 맞으나 충전 호출 주체가 가맹점이라는 근거가 명세에 없다.

- **V4 마이그레이션으로 컬럼 리네임.** 배포된 DB가 있다면 정석이다. H2 인메모리라 해당하지 않는다.
