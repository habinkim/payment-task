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
| S3 | 영속 계층 (엔티티, 리포지토리, 저장소 인터페이스와 구현) | ☑ |
| S4 | 게이트웨이 연동 (모의 구현, 장애 주입) | ☑ |
| S5 | 결제 처리 서비스 | ☑ |
| S6 | 지갑 충전과 차감 | ☑ |
| S7 | 조회 API (단건, 목록) | ☑ |
| S8 | 정합성 확인 (`UNKNOWN` 확정) | ☑ |
| S9 | 아키텍처 테스트와 시나리오 검증 | ☑ |
| S10 | README와 `.http` 파일 | ☑ |

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
| `merchantPaymentNo` | 영숫자와 하이픈, 최대 64자 |
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

- `merchantPaymentNo` 필수, 형식 검증
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
| `merchant_payment_no VARCHAR(64)` UNIQUE | 멱등 키. 인덱스 크기 제한 |
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

### V3 — `wallet_charge` ☑

충전 이력. 상세는 `src/main/resources/db/migration/V3__create_wallet_charge.sql` 참조.

| 결정 | 이유 |
|---|---|
| `charge_no VARCHAR(64)` UNIQUE | 멱등 키. `merchant_payment_no`와 같은 원리이며 동시 요청의 직렬화 지점 |
| **`status` 컬럼 없음** | 외부 승인이 없어 중간 상태가 존재하지 않는다. 성공하면 행이 있고 실패하면 롤백된다 |
| `CHECK (amount > 0)` | 0원·음수 충전 차단. 도메인 검증과 이중 방어 |
| `idx_wallet_charge_wallet_id` | 지갑별 이력 조회 대비 |

`payment`와 달리 갱신 컬럼(`updated_at`)이 없다. 충전 이력은 한 번 기록되면 바뀌지 않는다.

결제는 `merchant_payment_no`인데 충전은 `charge_no`로 비대칭이다. 결제는 3rd party(가맹점)가 호출한다고 명세에 명시돼 있지만 **충전의 호출 주체는 명세에 없다.** 근거 없는 이름을 스키마에 박지 않는다([ADR 0011](adr/0011-merchant-payment-no.md)). 호출 주체가 확정되면 맞춘다.

---

## 5. API

### 5.1 결제 요청 ☑

```
POST /api/v1/payments
```

**요청**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `merchantPaymentNo` | string | 예 | 3rd party가 생성. 멱등 키 |
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
    "merchantPaymentNo": "PAY-20260811-001",
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
| 동일 `merchantPaymentNo` 재요청 | 최초 결과 그대로 | 최초와 동일 | 최초와 동일 |

멱등 재요청은 새로 처리하지 않고 저장된 결과를 반환한다. 최초 요청이 아직 처리 중이면 `DUPLICATE_PAYMENT_NO`(409)를 준다.

### 5.2 결제 단건 조회 ☑

```
GET /api/v1/payments/{merchantPaymentNo}
```

서버 PK가 아니라 `merchantPaymentNo`로 조회한다. 타임아웃으로 응답을 받지 못한 클라이언트도 호출할 수 있어야 하기 때문이다.

**응답 `returnObject`**

| 필드 | 설명 |
|---|---|
| `merchantPaymentNo` | |
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

### 5.3 지갑 조회 ☑

```
GET /api/v1/wallets/{walletId}
```

`walletId`, `currency`, `balance`, `updatedAt`을 반환한다. 없으면 `WALLET_NOT_FOUND`(404).

### 5.4 결제 목록 조회 ☑

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

### 5.5 정합성 확인 ☑

```
POST /api/v1/admin/payments/{merchantPaymentNo}/reconcile
```

`UNKNOWN` 건을 게이트웨이에 재조회해 상태를 확정한다. 운영자가 특정 건을 지목해 실행한다.

`UNKNOWN`이 아닌 건에 호출하면 현재 상태를 그대로 반환하고 아무것도 바꾸지 않는다.

스케줄러도 같은 로직을 주기적으로 실행한다. 주기는 `payment.reconcile.fixed-delay`로 설정한다.

### 5.6 지갑 충전 ☑

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

**이력은 별도 테이블(`wallet_charge`)에 남긴다.** `payment` 테이블 14개 컬럼 중 6개가 게이트웨이 전용(`status`, `failure_reason`, `retriable`, `external_transaction_id`, `external_response_code`, `requested_at`/`responded_at`)이라 충전이 쓰면 전부 NULL로 남는다. 도메인도 마찬가지다 — `Payment`의 `markRequested()`·`recordExternalApproval()`·`markUnknown()`은 충전에서 호출할 수 없다.

`wallet_charge`에는 **`status` 컬럼을 두지 않는다.** 결제에 `PENDING`이 필요했던 이유는 "요청은 보냈는데 결과를 모른다"가 존재하기 때문이다([ADR 0004](adr/0004-timeout-is-indoubt.md)). 충전은 외부 호출이 없어 이력 기록과 잔액 증가가 한 트랜잭션에서 끝나므로 중간 상태가 존재할 수 없다.

**같은 `chargeNo` 재요청은 200과 최초 이력을 반환한다.** 결제처럼 409를 쓰지 않는 이유도 같다 — 처리 중이라는 상태가 없으므로 성공했거나 행이 없거나 둘 뿐이다. 방어는 선조회와 `uk_wallet_charge_no` UNIQUE 두 단계이며, 동시 요청의 실제 직렬화 지점은 UNIQUE 제약이다.

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

`merchantPaymentNo`에 UNIQUE 제약을 걸고, 위반을 잡아 기존 건을 조회해 반환한다. 단순히 409를 내면 클라이언트가 결과를 알 수 없다.

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
| 로그 출력 | `dev`는 콘솔과 SQL 디버그, `prod`는 콘솔과 파일(비동기 롤링). 기본 프로파일은 `dev` |
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
| 4 | 서버 오류 5xx | Mock |
| 5 | 잘못된 요청 4xx | Mock |
| 6 | 타임아웃 | Mock |
| 7 | 연결 실패 | Mock |
| 8 | 지연 응답 | Mock |
| 9 | 중복 요청 | Mock |

아홉 가지 모두 모의 구현이 접두어로 재현한다. 실제 게이트웨이가 없어 HTTP 계층을 검증할 대상이 없으므로 WireMock을 쓰지 않는다(ADR 0009).

접두어로 지정하지 않은 요청에도 실패를 섞으려면 장애 주입을 켠다. 정상 결제를 반복하다 우연히 실패를 만나는 상황을 재현하기 위한 것이며, 스케줄러가 실제로 쌓인 `UNKNOWN`을 처리하는 모습을 볼 수 있다.

### 8.3 동시성 테스트 ☑

- 잔액 100인 지갑에 80짜리 결제 2건 동시 요청 → 1건만 성공, 잔액 음수 아님
- 동일 `merchantPaymentNo` 2건 동시 요청 → 1건만 처리, 나머지는 최초 결과 반환

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
| `MockExternalPaymentClient` | `payment.gateway.mode=mock` (기본) | 시나리오 판정. 앱의 정상 동작 경로 |
| `HttpExternalPaymentClient` | — | **만들지 않는다.** 연동할 실제 게이트웨이가 없다(ADR 0009) |

전환은 스프링 프로파일이 아니라 설정 키로 한다. 프로파일은 실행 환경 전체를 가르므로 게이트웨이 구현이라는 좁은 관심사를 묶기에 적합하지 않다(ADR 0007).

### 9.2 시나리오 트리거

`merchantPaymentNo` 접두어로 판정한다. 해석은 Mock 안에만 둔다.

| 접두어 | 결과 |
|---|---|
| `TIMEOUT-` | `IN_DOUBT` |
| `ERR500-` | `FAILED`, 재시도 가능 |
| `ERR400-` | `FAILED`, 재시도 불가 |
| `DECLINE-` | `DECLINED` |
| `SLOW-` | 지연 후 `APPROVED` |
| 없음 | `APPROVED` |

### 9.3 장애 주입

```yaml
payment:
  gateway:
    chaos:
      enabled: false
      timeout-rate: 0.0
      failure-rate: 0.0
```

기본값은 전부 꺼짐이다. **접두어 지정이 확률보다 우선하므로** `TIMEOUT-`을 붙인 요청은 장애 주입 여부와 무관하게 항상 타임아웃이 된다. 의도한 시나리오가 확률에 휘둘리면 통합 테스트가 불안정해진다.

난수는 생성자로 주입해 테스트에서 시드를 고정한다.

HTTP 타임아웃 설정(`connect-timeout`, `read-timeout`)은 향할 대상이 없어 제거했다.

---

## 10. 산출물

| 항목 | 상태 |
|---|---|
| `README.md` — 실행 방법, API 목록, 설계 요약, 흐름 다이어그램 | ☑ |
| `docs/adr/` — 설계 판단 12건 | ☑ |
| `docs/requirement/` — 과제 원문 | ☑ |
| `http/*.http` — 결제·지갑·운영·시나리오 4종 | ☑ |
| API 문서 UI (Scalar) | ☑ `/scalar` |

---

## 11. 미확정 항목

| # | 항목 | 영향 |
|---|---|---|
| 1 | 충전 이력 저장 방식 (별도 테이블 vs 유형 컬럼) | §4 V3, §5.6 |
| 2 | 인증과 인가 수준 | 명세가 재량 위임. 최소 구현 예정 |
| 3 | 결제 응답에 지갑 잔액을 포함할지 | 포함 시 차감 후 추가 조회 필요 |
| 4 | 다통화 지갑 지원 범위 | 현재는 지갑 1개당 통화 1개 가정 |

---


### 수평 확장 관련 (2026-08-12 검토)

[ADR 0006](adr/0006-single-server-no-middleware.md)에 전제와 한계를 정리했다. 아직 손대지 않은 항목만 여기 남긴다.

현재 정합성은 **UNIQUE 2개 + 조건부 UPDATE 2개 + CHECK 3개**가 떠받친다. 락은 0건이다. 이 구조가 유효한 조건(단일 행·고정 금액·단방향 상태 머신·한 행 안의 불변식)은 [ADR 0012](adr/0012-conditional-update-for-state-transition.md)에 정리했다 — **부분 취소, 지갑 간 이체, 일일 한도 중 하나라도 생기면 락을 다시 검토해야 한다.**

| # | 항목 | 성격 | 판단 |
|---|---|---|---|
| A | `WalletChargeService`의 `DataIntegrityViolationException`을 같은 트랜잭션에서 잡는다 | 트랜잭션이 이미 rollback-only라 커밋 시 `UnexpectedRollbackException`. 금전 정합성은 UNIQUE가 지키나 **멱등 재요청이 200 대신 500**이 된다 | **H2에선 재현되지 않아 수정을 증명할 수 없다.** MySQL 테스트(항목 B)와 묶어야 의미가 있다 |
| B | 동시성 테스트를 MySQL에서 | H2는 READ COMMITTED, InnoDB는 REPEATABLE READ | Testcontainers 도입. 새 의존성이라 별도 판단 |
| C | 스케줄러 다중 인스턴스 중복 실행 | 정합성은 [ADR 0012](adr/0012-conditional-update-for-state-transition.md)가 지킨다. 남는 건 게이트웨이 호출 낭비 | ShedLock. **효율성 문제**라 우선순위 낮음 |
| D | 같은 `merchantPaymentNo`에 다른 `amount`가 오면 기존 건 반환 | Stripe·Toss는 파라미터 불일치 에러를 낸다 | 요청 파라미터 해시 비교. API 계약 변경이라 별도 사이클 |
| E | 차감에만 원장 테이블이 없다 | 결제 생성·충전에는 원장 + UNIQUE가 있는데 차감은 `wallet.balance` 갱신뿐 | 근본 해법이나 스키마 변경 범위가 크다. [ADR 0012](adr/0012-conditional-update-for-state-transition.md)의 CAS로 먼저 막았으나 **비대칭을 메운 것이 아니라 우회한 것**이다 |
| F | `prod` 프로파일도 H2 인메모리 | 다중 인스턴스 시 DB가 인스턴스 수만큼 갈라진다 | 과제 명세가 H2를 요구. README에 단일 인스턴스 전제 명시로 갈음 |

## 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-08-12 | S10 완료. README에 문제 정의·정책·흐름(Mermaid 3종)·실행 방법·확장 고려사항을 담고, `http/` 에 시나리오별 요청 예시 4종 추가. `SLOW-` 접두어 설명이 구현과 달라 정정 |
| 2026-08-12 | 결제 상태 전이를 조건부 UPDATE로 전환(ADR 0012). 수평 확장 검토 중 이중 차감을 재현했고, `WHERE status not in (COMPLETED, FAILED)` 조건으로 확정이 한 번만 반영되도록 함. 경합 패배 시 `PAYMENT_ALREADY_SETTLED`(409) |
| 2026-08-12 | 역할을 드러내는 이름으로 정정(ADR 0011). 우리가 PG이므로 외부 연동 타입을 `Gateway*` → `External*`, 패키지·설정 키를 `gateway` → `external`로 옮기고, 3rd party가 발급하는 결제번호를 `paymentNo` → `merchantPaymentNo`로 변경. API 요청·응답 키와 DB 컬럼 포함 |
| 2026-08-12 | S6 지갑 충전 완료. `POST /api/v1/wallets/{walletId}/charge` 추가하고 이력을 `wallet_charge`(V3)에 별도 저장. `chargeNo` UNIQUE로 멱등을 보장하며 중복 요청은 409가 아니라 200과 최초 이력을 반환 |
| 2026-08-11 | 저장소를 도메인 인터페이스와 인프라 구현으로 분리(ADR 0010). 인프라가 서비스를 역참조하던 순환을 제거하고, 페이징을 `PageQuery`·`PageResult` 도메인 타입으로 바꿔 스프링 데이터를 인프라 안으로 가둠. 의존 방향을 강제하는 아키텍처 규칙 4종 추가 |
| 2026-08-11 | 최초 작성. 명세 요구사항 추적표 도입, 지갑 충전(§5.6) 누락 발견하여 추가 |
| 2026-08-11 | `HttpExternalPaymentClient`와 WireMock 검증을 범위에서 제외(ADR 0009). 연동할 실제 게이트웨이가 없어 HTTP 타임아웃 설정이 향할 대상이 없다. 대신 확률 기반 장애 주입을 둔다 |
| 2026-08-11 | S8 정합성 확인 완료. 게이트웨이 재조회로 결과 미상 건을 확정하고, 스케줄러가 주기적으로 처리한다. 스케줄러는 기본 비활성이며 `prod`에서만 켠다 |
| 2026-08-11 | S7 조회 API 완료. 단건·지갑·목록 조회를 추가하고 `Payment` 도메인에 `createdAt`을 더해 정렬 기준을 응답에 노출. 조회는 결제 상태와 무관하게 200을 반환한다 |
| 2026-08-11 | 로깅을 프로파일별로 분리. `<root>`가 `springProfile` 안에만 있어 프로파일 없이 실행하면 로그가 전혀 출력되지 않던 문제를 수정 |
| 2026-08-11 | API 문서 UI를 Swagger UI에서 Scalar로 교체. OpenAPI 애노테이션으로 시나리오 접두어와 시드 지갑을 문서에 담아 평가자가 문서에서 바로 눌러볼 수 있게 함. 텔레메트리는 끔 |
| 2026-08-11 | S5 결제 처리 서비스 완료. 트랜잭션 경계를 오케스트레이터와 전용 서비스로 분리(ADR 0001). `ExternalApprovalResult`를 재시도 가능 여부로 세분화. 동시성 테스트에서 `WalletStore` 쓰기 메서드에 트랜잭션이 없어 트랜잭션 밖 호출이 실패하는 결함을 발견해 수정 |
| 2026-08-11 | S4 모의 게이트웨이 완료. 전환 수단을 스프링 프로파일에서 `payment.gateway.mode` 설정 키로 변경. 로깅 설정을 추가하고 `-PshowSql` 로 테스트에서 SQL을 볼 수 있게 함 |
| 2026-08-11 | S3 영속 계층 완료. 도메인과 JPA 엔티티를 분리하고(ADR 0008) 잔액 증감을 조건부 UPDATE로 처리. `currency`를 `CHAR`에서 `VARCHAR`로 교정 — `CHAR`는 짧은 값에 공백을 채워 비교가 어긋난다 |
| 2026-08-11 | S2 도메인 모델 완료. 도메인 클래스를 `payment/domain`, `wallet/domain` 하위 패키지로 배치. 검증은 계층별로 분리하여 도메인은 생성자 불변식, 요청 DTO는 Bean Validation을 쓴다 |
