# 구현 스펙

> 이 문서는 **무엇을 만드는가**를 정의한다. 사이클별 목표는 [PRD](prd/README.md), 설계 판단의 근거는 [ADR](adr/README.md)에 있다.
> 구현이 진행되면서 항목이 **추가되고 확정된다.** 결정이 바뀌면 해당 항목을 고치고 변경 이력에 남긴다.

| 항목 | 값 |
|---|---|
| 문서 버전 | v0.1 |
| 최종 갱신 | 2026-08-11 |
| 상태 표기 | ☐ 미착수 · ◐ 진행중 · ☑ 완료 |

---

## 0. 진행 현황

| # | 범위 | 상태 |
|---|---|---|
| S1 | 빌드와 공통 계층 | ☑ |
| S2 | 도메인 모델 (`Payment`, `Wallet`) | ☑ |
| S3 | 영속 계층 (엔티티, 리포지토리, 캡슐) | ☑ |
| S4 | 게이트웨이 연동 (`Mock`, `Http`) | ◐ |
| S5 | 결제 처리 서비스 | ☑ |
| S6 | 지갑 충전과 차감 | ☐ |
| S7 | 조회 API (단건, 목록) | ☐ |
| S8 | 정합성 확인 (`UNKNOWN` 확정) | ☐ |
| S9 | 아키텍처 테스트와 시나리오 검증 | ☐ |
| S10 | README와 `.http` 파일 | ☐ |

---

## 1. 요구사항 추적

명세의 시스템 요구사항 3개가 어느 스펙 항목으로 구현되는지 대응시킨다. 빠진 요구사항이 없음을 확인하는 장치다.

| 명세 요구사항 | 대응 스펙 |
|---|---|
| 1. 결제 이력 — 원장, 상태, 사유 저장 | §3 도메인, §4 스키마, §5.1 결제 요청 |
| 1. 결제 이력 — 운영 모니터링 정보 제공 | §5.4 목록 조회, §5.5 정합성 확인, §7 관측성 |
| 2. 고객 지갑 — 실시간 금액 확인과 갱신 | §3.2 `Wallet`, §5.1 결제 처리 |
| 2. 고객 지갑 — 금액 정합성 보장 | §6.2 원자적 차감, §8.3 동시성 테스트 |
| 2. 고객 지갑 — **충전과 차감 가능** | §5.6 지갑 충전, §5.1 결제(차감) |
| 3. 외부 결제 API 연동 | §5.1 결제 처리, §9 게이트웨이 |

> **누락 발견 기록** — 초기 분석의 기능 요구사항에는 지갑 **충전**이 빠져 있었다. 명세 2번의 "고객 지갑은 결제 API 연동을 통해 충전/차감이 가능해야 합니다"를 근거로 §5.6을 추가한다.

---

## 2. 공통 규약

### 2.1 응답 형식

성공과 실패 모두 같은 래퍼를 쓴다. 명세가 제시한 형식을 그대로 따른다.

```json
{
  "code": "OK",
  "message": "정상적으로 처리되었습니다.",
  "returnObject": { }
}
```

실패 시 `returnObject`는 `null`이다.

### 2.2 응답 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| `OK` | 200 | 정상 처리 |
| `PAYMENT_IN_DOUBT` | 202 | 결과 미상. 조회로 확정 필요 |
| `INVALID_REQUEST` | 400 | 요청 파라미터 오류 |
| `PAYMENT_NOT_FOUND` | 404 | 결제 없음 |
| `WALLET_NOT_FOUND` | 404 | 지갑 없음 |
| `DUPLICATE_PAYMENT_NO` | 409 | 처리 중인 결제번호 재요청 |
| `INSUFFICIENT_BALANCE` | 422 | 잔액 부족 |
| `PAYMENT_DECLINED` | 422 | 게이트웨이 승인 거절 |
| `SYSTEM_ERROR` | 500 | 외부 또는 내부 장애 |

### 2.3 공통 헤더

| 헤더 | 방향 | 설명 |
|---|---|---|
| `X-Request-Id` | 요청(선택) / 응답(항상) | 없으면 서버가 생성한다. CS 문의 추적용 |

### 2.4 값 규약

| 대상 | 규약 |
|---|---|
| 금액 | 문자열 또는 숫자로 받되 `BigDecimal`로 다룬다. 소수점 4자리까지 |
| 통화 | ISO 4217 세 글자 대문자 (`USD`, `JPY`, `KRW`) |
| `paymentNo` | 영숫자와 하이픈, 최대 64자 |
| 시각 | ISO-8601 UTC (`2026-08-11T00:00:00Z`) |

---

## 3. 도메인 모델

### 3.1 `Payment`

**상태**

| 값 | 의미 | 종료 상태 |
|---|---|---|
| `PENDING` | 원장 선기록, 외부 호출 전후 | 아니오 |
| `COMPLETED` | 승인과 차감 성공 | 예 |
| `FAILED` | 실패. 사유 필수 | 예 |
| `UNKNOWN` | 결과 미상. 확정 필요 | 아니오 |

**허용 전이**

```
PENDING  → COMPLETED | FAILED | UNKNOWN
UNKNOWN  → COMPLETED | FAILED
COMPLETED, FAILED → (전이 불가)
```

**실패 사유**

| 값 | 재시도 | 발생 조건 |
|---|---|---|
| `INSUFFICIENT_BALANCE` | 불가 | 잔액 부족 |
| `PAYMENT_DECLINED` | 불가 | 게이트웨이 거절 |
| `SYSTEM_ERROR` | 조건부 | 외부 또는 내부 장애. `retriable` 필드로 구분 |

**메서드**

| 시그니처 | 동작 |
|---|---|
| `complete(String externalTransactionId, String externalResponseCode)` | `COMPLETED`로 전이 |
| `fail(FailureReason reason, boolean retriable, String externalResponseCode)` | `FAILED`로 전이 |
| `markUnknown(String externalResponseCode)` | `UNKNOWN`으로 전이 |
| `isTerminal()` | 종료 상태 여부 |

**불변식** — 생성자에서 강제한다.

검증은 계층별로 나눈다. 요청 DTO는 Bean Validation 애노테이션(`@NotBlank`, `@Positive`)으로 컨트롤러 진입 시점에 걸러 `INVALID_REQUEST`를 반환하고, 도메인 객체는 생성자에서 직접 검증한다. 애노테이션은 `@Valid`가 붙은 경로에서만 평가되므로 `new Payment(...)`로 직접 생성할 때는 동작하지 않는다. 도메인 불변식은 생성 경로와 무관하게 성립해야 하므로 생성자가 책임진다.

`SelfValidating` 같은 패턴으로 도메인에서도 애노테이션을 평가할 수 있으나, 그러면 도메인이 `jakarta.validation`에 의존하게 되어 §8.4의 아키텍처 규칙과 충돌한다.

- `paymentNo` 필수, 형식 검증
- `walletId` 필수
- `amount` 필수, `> 0`
- `currency` 필수, 세 글자
- 종료 상태에서 다시 전이하면 예외

### 3.2 `Wallet`

| 시그니처 | 동작 |
|---|---|
| `canAfford(BigDecimal amount)` | 잔액 충분 여부 |
| `charge(BigDecimal amount)` | 충전 |
| `withdraw(BigDecimal amount)` | 차감. 부족하면 예외 |

**불변식**

- `currency` 필수
- `balance` 음수 불가
- 충전과 차감 금액은 `> 0`

---

## 4. 스키마

Flyway가 소유한다. JPA는 `validate`만 한다.

### V1 — `wallet`, `payment`

작성 완료. 상세는 `src/main/resources/db/migration/V1__create_wallet_and_payment.sql` 참조.

| 결정 | 이유 |
|---|---|
| `payment_no VARCHAR(64)` UNIQUE | 멱등 키. 인덱스 크기 제한 |
| `amount DECIMAL(19,4)` | 금액 정밀도. `DOUBLE` 금지 |
| `currency VARCHAR(3)` | ISO 4217. `CHAR`는 짧은 값에 공백을 채워 비교가 어긋난다 |
| `status VARCHAR(16)` | enum 문자열. 가독성 우선 |
| `idx_payment_status_created_at` | 상태별 목록 조회와 `UNKNOWN` 스캔 |

### V2 — 초기 데이터 ☑

평가자가 바로 결제를 호출할 수 있도록 지갑 몇 건을 넣는다. 인메모리라 재기동 시 사라지므로 시드가 필요하다.

| 지갑 ID | 통화 | 초기 잔액 | 용도 |
|---|---|---|---|
| 1 | USD | 1000.0000 | 정상 결제 |
| 2 | USD | 10.0000 | 잔액 부족 시나리오 |
| 3 | JPY | 50000.0000 | 다통화 확인 |

---

## 5. API

### 5.1 결제 요청 ☑

```
POST /api/v1/payments
```

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `paymentNo` | string | 예 | 3rd party가 생성. 멱등 키 |
| `walletId` | number | 예 | 대상 지갑 |
| `amount` | number | 예 | `> 0` |
| `currency` | string | 예 | 지갑 통화와 일치해야 한다 |

**처리 순서**

```
① 원장 PENDING 저장 (TX1, 커밋)
② 잔액 사전검증 — 부족하면 즉시 FAILED, 외부 호출 안 함
③ 게이트웨이 승인 요청 (트랜잭션 밖)
④ 잔액 원자적 차감 + 상태 확정 (TX2, 커밋)
```

**응답 (성공)**

```json
{
  "code": "OK",
  "message": "정상적으로 처리되었습니다.",
  "returnObject": {
    "paymentNo": "PAY-20260811-001",
    "status": "COMPLETED",
    "amount": 100.0000,
    "currency": "USD",
    "walletBalance": 900.0000,
    "externalTransactionId": "TXN-8f2a",
    "completedAt": "2026-08-11T00:00:00Z"
  }
}
```

**결과별 응답**

| 상황 | code | HTTP | status |
|---|---|---|---|
| 승인과 차감 성공 | `OK` | 200 | `COMPLETED` |
| 잔액 부족 | `INSUFFICIENT_BALANCE` | 422 | `FAILED` |
| 게이트웨이 거절 | `PAYMENT_DECLINED` | 422 | `FAILED` |
| 외부 또는 내부 장애 | `SYSTEM_ERROR` | 500 | `FAILED` |
| 타임아웃 | `PAYMENT_IN_DOUBT` | 202 | `UNKNOWN` |
| 동일 `paymentNo` 재요청 | 최초 결과 그대로 | 최초와 동일 | 최초와 동일 |

멱등 재요청은 새로 처리하지 않고 저장된 결과를 반환한다. 최초 요청이 아직 처리 중이면 `DUPLICATE_PAYMENT_NO`(409)를 준다.

### 5.2 결제 단건 조회 ☐

```
GET /api/v1/payments/{paymentNo}
```

서버 PK가 아니라 `paymentNo`로 조회한다. 타임아웃으로 응답을 받지 못한 클라이언트도 호출할 수 있어야 하기 때문이다.

**응답 `returnObject`**

| 필드 | 설명 |
|---|---|
| `paymentNo` | |
| `walletId` | |
| `amount`, `currency` | |
| `status` | |
| `failureReason` | 실패 시에만 |
| `retriable` | 실패 시에만. CS 안내 기준 |
| `externalTransactionId` | 게이트웨이 대조용 |
| `externalResponseCode` | 게이트웨이 대조용 |
| `requestedAt`, `respondedAt` | 외부 호출 소요 추적 |
| `createdAt`, `updatedAt` | |

없으면 `PAYMENT_NOT_FOUND`(404).

### 5.3 지갑 조회 ☐

```
GET /api/v1/wallets/{walletId}
```

`walletId`, `currency`, `balance`, `updatedAt`을 반환한다. 없으면 `WALLET_NOT_FOUND`(404).

### 5.4 결제 목록 조회 ☐

```
GET /api/v1/payments?status=&walletId=&from=&to=&page=0&size=20
```

운영 모니터링용이다. 명세의 "운영상의 모니터링이 가능한 정보를 제공"을 충족한다.

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `status` | 아니오 | 상태 필터 |
| `walletId` | 아니오 | 지갑 필터 |
| `from`, `to` | 아니오 | 생성 시각 범위 |
| `page`, `size` | 아니오 | 기본 0, 20. `size` 최대 100 |

정렬은 `createdAt` 내림차순 고정이다.

**응답 `returnObject`**

```json
{
  "content": [ ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "hasNext": true
}
```

### 5.5 정합성 확인 ☐

```
POST /api/v1/admin/payments/{paymentNo}/reconcile
```

`UNKNOWN` 건을 게이트웨이에 재조회해 상태를 확정한다. 운영자가 특정 건을 지목해 실행한다.

`UNKNOWN`이 아닌 건에 호출하면 현재 상태를 그대로 반환하고 아무것도 바꾸지 않는다.

스케줄러도 같은 로직을 주기적으로 실행한다. 주기는 `payment.reconcile.fixed-delay`로 설정한다.

### 5.6 지갑 충전 ☐

```
POST /api/v1/wallets/{walletId}/charge
```

명세 2번의 "고객 지갑은 결제 API 연동을 통해 충전/차감이 가능해야 합니다"를 충족한다. 차감은 결제(§5.1)가, 충전은 이 API가 담당한다.

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `chargeNo` | string | 예 | 멱등 키. 결제와 같은 이유로 필요 |
| `amount` | number | 예 | `> 0` |
| `currency` | string | 예 | 지갑 통화와 일치 |

충전은 외부 게이트웨이를 거치지 않는다. 이 과제의 외부 연동 대상은 결제 승인이고, 충전 수단(계좌이체·카드) 연동은 범위 밖이다.

> **미확정** — 충전 이력을 별도 테이블로 남길지, `payment` 테이블에 유형 컬럼을 두어 함께 다룰지 정하지 않았다. 결정 시 §4에 V3을 추가한다.

---

## 6. 처리 규칙

### 6.1 트랜잭션 경계

```
[TX1] 원장 PENDING 저장 → 커밋
[트랜잭션 밖] 게이트웨이 호출
[TX2] 잔액 차감 + 상태 확정 → 커밋
```

외부 호출을 트랜잭션 안에 넣지 않는다. 커넥션 점유 시간이 외부 응답 시간에 종속되면 커넥션 풀이 고갈된다.

### 6.2 잔액 차감

```sql
UPDATE wallet
   SET balance = balance - :amount,
       updated_at = :now
 WHERE id = :walletId
   AND balance >= :amount
```

영향 행이 0이면 잔액 부족이다. 조회 후 검증 후 차감하는 3단계를 쓰지 않는다.

### 6.3 멱등 처리

`paymentNo`에 UNIQUE 제약을 걸고, 위반을 잡아 기존 건을 조회해 반환한다. 단순히 409를 내면 클라이언트가 결과를 알 수 없다.

### 6.4 실패 분류

| 게이트웨이 결과 | 상태 | 사유 | `retriable` |
|---|---|---|---|
| `APPROVED` + 차감 성공 | `COMPLETED` | — | — |
| `APPROVED` + 잔액 부족 | `FAILED` | `INSUFFICIENT_BALANCE` | false |
| `DECLINED` | `FAILED` | `PAYMENT_DECLINED` | false |
| `FAILED` (4xx) | `FAILED` | `SYSTEM_ERROR` | false |
| `FAILED` (5xx, 연결 실패) | `FAILED` | `SYSTEM_ERROR` | true |
| `IN_DOUBT` (타임아웃) | `UNKNOWN` | `SYSTEM_ERROR` | true |

`APPROVED`인데 차감이 실패한 경우는 외부 승인이 이미 난 상태다. `externalTransactionId`를 원장에 남기고 별도 카운터로 집계한다. 보상 취소는 v1에서 구현하지 않는다.

---

## 7. 관측성

| 항목 | 내용 |
|---|---|
| 상관관계 ID | `X-Request-Id`를 MDC에 넣고 응답 헤더로 반환 |
| 결제 결과 카운터 | `payment.result{status, reason}` |
| 승인 후 차감 실패 카운터 | `payment.orphan.total` — 미아 거래 후보 |
| 게이트웨이 호출 시간 | `gateway.approve.duration` |
| `UNKNOWN` 잔량 | `payment.unknown.count` |

허용하는 실패마다 카운터를 둔다. 조용히 넘어가면 의도한 것인지 모르고 놓친 것인지 구분되지 않는다.

---

## 8. 테스트

### 8.1 단위 테스트 ◐

인프라 없이 돈다.

- `Payment` 상태 전이와 불변식
- `Wallet` 충전, 차감, 잔액 판정
- 실패 분류 로직
- `ScenarioResolver` 접두어 해석

### 8.2 시나리오 검증 ☐

아래 9건을 전부 통합 테스트로 검증한다.

| # | 시나리오 | 검증 주체 |
|---|---|---|
| 1 | 정상 승인 | Mock |
| 2 | 승인 거절 | Mock |
| 3 | 잔액 부족 | Mock |
| 4 | 서버 오류 5xx | Mock + WireMock |
| 5 | 잘못된 요청 4xx | Mock + WireMock |
| 6 | 타임아웃 | Mock + **WireMock 필수** |
| 7 | 연결 실패 | **WireMock 필수** |
| 8 | 지연 응답 | **WireMock 필수** |
| 9 | 중복 요청 | Mock |

Mock은 결과를 만들고 WireMock은 설정을 검증한다. #6~#8은 HTTP 계층 설정이 실제로 동작하는지 확인해야 하므로 WireMock이 필요하다.

### 8.3 동시성 테스트 ☑

- 잔액 100인 지갑에 80짜리 결제 2건 동시 요청 → 1건만 성공, 잔액 음수 아님
- 동일 `paymentNo` 2건 동시 요청 → 1건만 처리, 나머지는 최초 결과 반환

### 8.4 아키텍처 테스트 ☑

| 규칙 | 이유 |
|---|---|
| Service가 `Repository`에 직접 의존 금지 | 캡슐 경유 |
| Service, Controller가 HTTP 클라이언트에 직접 의존 금지 | 게이트웨이 경계 |
| 도메인 객체가 Spring, JPA 타입에 의존 금지 | 도메인 순수성 |
| 도메인 객체가 `double` 사용 금지 | 금액 정밀도 |
| `payment` 패키지가 `gateway` 구현체에 의존 금지 | 인터페이스만 참조 |
| 필드 주입 금지 | 테스트 가능성 |

테스트 이름은 요구사항과 1:1로 대응시킨다. 평가자가 이름만 읽어도 무엇이 검증됐는지 알 수 있어야 한다.

---

## 9. 게이트웨이

### 9.1 구현체

| 구현 | 활성 조건 | 역할 |
|---|---|---|
| `MockPaymentGatewayClient` | `payment.gateway.mode=mock` (기본) | 시나리오 판정. 앱의 정상 동작 경로 |
| `HttpPaymentGatewayClient` | `payment.gateway.mode=real` | 실제 연동. 통합 테스트 대상 |

전환은 스프링 프로파일이 아니라 설정 키로 한다. 프로파일은 실행 환경 전체를 가르므로 게이트웨이 구현이라는 좁은 관심사를 묶기에 적합하지 않다(ADR 0007).

### 9.2 시나리오 트리거

`paymentNo` 접두어로 판정한다. 해석은 Mock 안에만 둔다.

| 접두어 | 결과 |
|---|---|
| `TIMEOUT-` | `IN_DOUBT` |
| `ERR500-` | `FAILED`, 재시도 가능 |
| `ERR400-` | `FAILED`, 재시도 불가 |
| `DECLINE-` | `DECLINED` |
| `SLOW-` | 지연 후 `APPROVED` |
| 없음 | `APPROVED` |

### 9.3 타임아웃

| 설정 | 값 |
|---|---|
| connect | 1s |
| read | 3s |

`real` 프로파일에서만 적용된다. 기본 프로파일은 인프로세스라 이 설정이 경로에 놓이지 않는다.

---

## 10. 산출물

| 항목 | 상태 |
|---|---|
| `README.md` — 실행 방법, API 목록, 설계 요약 | ☐ |
| `docs/adr/` — 설계 판단 7건 | ☑ |
| `docs/requirement/` — 과제 원문 | ☑ |
| `http/*.http` — 정상 경로와 오류 시나리오 전부 | ☐ |
| Swagger UI | ☑ (springdoc 설정 완료) |

---

## 11. 미확정 항목

| # | 항목 | 영향 |
|---|---|---|
| 1 | 충전 이력 저장 방식 (별도 테이블 vs 유형 컬럼) | §4 V3, §5.6 |
| 2 | 인증과 인가 수준 | 명세가 재량 위임. 최소 구현 예정 |
| 3 | 결제 응답에 지갑 잔액을 포함할지 | 포함 시 차감 후 추가 조회 필요 |
| 4 | 다통화 지갑 지원 범위 | 현재는 지갑 1개당 통화 1개 가정 |

---

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-08-11 | 최초 작성. 명세 요구사항 추적표 도입, 지갑 충전(§5.6) 누락 발견하여 추가 |
| 2026-08-11 | S5 결제 처리 서비스 완료. 트랜잭션 경계를 오케스트레이터와 전용 서비스로 분리(ADR 0001). `GatewayResult`를 재시도 가능 여부로 세분화. 동시성 테스트에서 `WalletStore` 쓰기 메서드에 트랜잭션이 없어 트랜잭션 밖 호출이 실패하는 결함을 발견해 수정 |
| 2026-08-11 | S4 모의 게이트웨이 완료. 전환 수단을 스프링 프로파일에서 `payment.gateway.mode` 설정 키로 변경. 로깅 설정을 추가하고 `-PshowSql` 로 테스트에서 SQL을 볼 수 있게 함 |
| 2026-08-11 | S3 영속 계층 완료. 도메인과 JPA 엔티티를 분리하고(ADR 0008) 잔액 증감을 조건부 UPDATE로 처리. `currency`를 `CHAR`에서 `VARCHAR`로 교정 — `CHAR`는 짧은 값에 공백을 채워 비교가 어긋난다 |
| 2026-08-11 | S2 도메인 모델 완료. 도메인 클래스를 `payment/domain`, `wallet/domain` 하위 패키지로 배치. 검증은 계층별로 분리하여 도메인은 생성자 불변식, 요청 DTO는 Bean Validation을 쓴다 |
