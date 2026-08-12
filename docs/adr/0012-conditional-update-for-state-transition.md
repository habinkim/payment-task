# 0012. 결제 상태 전이에 조건부 UPDATE를 쓴다

상태: Accepted

## 맥락

수평 확장을 가정하고 동시성을 검토하다 **이중 차감을 재현했다**([검토 문서](../검토_수평확장과_동시성.md)).

두 인스턴스가 같은 `UNKNOWN` 건을 동시에 확정하는 상황을 만들었다.

```
A가 본 상태 = UNKNOWN, B가 본 상태 = UNKNOWN
confirm 성공: COMPLETED      ← 둘 다 성공
confirm 성공: COMPLETED
지갑 잔액 10.0000 → 4.0000
실제 차감 = 6.0000 (정상 3.0000)
```

**예외 0건, orphan 카운터 0건.** 두 스레드 모두 정상 종료했다.

위험도가 잔액에 반비례한다는 점이 특히 나쁘다.

| 잔액 대비 결제액 | 결과 |
|---|---|
| 60% | 차감 1회 — `balance >= amount`가 두 번째를 막는다 |
| **30%** | **차감 2회, 감지 수단 없음** |

잔액이 넉넉할수록 조건이 통과해 아무도 막지 못한다. `ck_wallet_balance_non_negative` CHECK도 발동하지 않는다.

### 원인 — 설계의 비대칭

```java
walletStore.deductIfEnough(...)     // UPDATE ... WHERE balance >= ?  조건부
ledgerStore.updateState(payment)    // UPDATE ... WHERE id = ?        무조건
```

잔액 차감은 [ADR 0003](0003-atomic-balance-deduction.md)에서 조건부 UPDATE로 제대로 짰는데, **상태 전이만 무방비**였다.

두 겹의 구멍이 겹쳤다.

**하나. 도메인 방어가 메모리 사본을 본다.**

```java
private void transitionTo(PaymentStatus next) {
    if (status.isTerminal()) { throw ...; }     // this.status = 인스턴스 필드
```

`Payment`는 JPA 엔티티가 아닌 POJO다([ADR 0008](0008-separate-domain-and-entity.md)). 영속성 컨텍스트에 붙어 있지 않아 락도 버전도 없다. A가 커밋해 DB가 `COMPLETED`가 돼도 B의 객체는 `UNKNOWN` 스냅샷이라 통과한다.

**둘. 차감이 먼저, 방어가 나중이다.**

```java
if (walletStore.deductIfEnough(...)) {   // ① 차감
    payment.complete(...);                // ② 방어
}
```

②가 막지 못하면 ①은 이미 실행된 뒤다.

## 결정

**상태 전이를 조건부 UPDATE로 바꾼다.**

```sql
update PaymentEntity p
   set p.status = :status, ...
 where p.merchantPaymentNo = :merchantPaymentNo
   and p.status not in (COMPLETED, FAILED)
```

`updateState()`가 `void` 대신 **`boolean`을 반환**하고, 호출부가 결과로 판단한다.

```java
if (!ledgerStore.updateState(payment)) {
    throw new ApiException(ResponseCode.PAYMENT_ALREADY_SETTLED);
}
```

이중 차감 시나리오에 적용하면 B가 0 rows를 받는다.

| | A | B |
|---|---|---|
| 차감 | 성공 | 성공 |
| 상태 전이 | 1 row → 커밋 | **0 rows** |
| 결과 | 정상 | 예외 → **롤백, 차감도 되돌아감** |

### 조건을 "종료 상태 금지"로 둔 이유

기대 상태(`expected`)를 명시하는 방식도 가능했다. 그러나 `transitionTo()`가 `this.status`를 덮어써서 **도메인이 이전 상태를 기억하지 않는다.** 호출부에서 전이 전에 캡처해 넘겨야 하는데, 캡처를 빠뜨리면 조용히 깨진다.

`not in (COMPLETED, FAILED)`는 **도메인 규칙을 그대로 미러링한다.**

```
transitionTo():  if (status.isTerminal()) throw
SQL:             and p.status not in (COMPLETED, FAILED)
```

호출부 3곳과 도메인 `Payment`를 건드리지 않는다.

### 반환 타입을 `boolean`으로

`deductIfEnough`가 `int` → `boolean`으로 바꾸는 패턴과 같다. 두 조건부 UPDATE가 같은 형태를 갖게 되어 비대칭이 사라진다.

`boolean`은 순수 자바라 도메인 순수성 규칙([ADR 0010](0010-store-interface-in-domain.md))에 걸리지 않는다.

### 실패는 409다

`PAYMENT_ALREADY_SETTLED`를 신설했다. CAS 실패는 서버 오류가 아니라 **정상적인 경합 결과**다. `SYSTEM_ERROR`(500)로 뭉개면 클라이언트가 재시도 여부를 판단할 수 없다.

기존 `DUPLICATE_PAYMENT_NO`는 메시지가 "이미 처리 **중인**"이라 "이미 확정된"과 어긋난다.

## 결과

**락도 미들웨어도 도입하지 않았다.** Kleppmann의 [분산 락 분석](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)이 방향을 정해줬다.

> 정확성이면 합의 시스템이나 최소한 제대로 된 트랜잭션 보장을 가진 DB를 쓰라.

이제 결제 경로 전체가 같은 원리로 직렬화된다.

| 연산 | 직렬화 지점 |
|---|---|
| 결제 생성 | `uk_merchant_payment_no` UNIQUE |
| 잔액 차감 | `WHERE balance >= :amount` |
| **상태 전이** | **`WHERE status not in (terminal)`** |

부수 효과로 `updateState()`의 불필요한 SELECT 왕복이 사라졌다. 기존 구현은 엔티티를 다시 읽어 더티체킹에 맡겼는데, 도메인 객체에 이미 모든 값이 있었다.

**경로와 무관하게 막힌다.** 스케줄러든 관리자 API든 같은 `updateState`를 지나므로, 스케줄러만 단일화하는 방식(ShedLock)으로는 얻을 수 없는 보장이다.

잃은 것도 있다. 경합에서 진 쪽은 **게이트웨이를 이미 호출한 뒤**라 그 왕복이 낭비된다. 정확성은 확보되지만 효율은 아니다 — 그건 ShedLock의 몫이며 별도 판단으로 남긴다.

### 검증

네 가지 회귀 테스트를 추가했고, CAS 조건을 제거하자 **네 건 모두 실패**했다. 조건이 실제 방어선임이 증명된다.

수정 전에는 같은 시나리오에서 218건이 전부 통과했다 — **기존 테스트가 이 결함을 전혀 검증하지 않았다.**

## 검토한 대안

- **`@Version` 낙관적 락.** JPA 표준이고 Hibernate가 `WHERE version=?`를 자동으로 붙인다. 그러나 `Payment`는 엔티티가 아니라 버전을 들고 다닐 수 없고, `restore()`로 복원할 때 버전까지 복원하면 도메인이 기술 필드를 갖게 된다. ADR 0008이 지킨 순수성을 흔든다. 컬럼 추가로 마이그레이션도 따라온다.

- **비관적 락 (`SELECT ... FOR UPDATE`).** `findOldestUnknown`에 락을 걸면 배치가 락을 오래 잡아 다른 요청을 막는다. CAS는 같은 목적을 락 없이 달성한다.

- **ShedLock으로 스케줄러 단일화.** 스케줄러 중복은 막지만 **관리자 API를 통한 동시 확정은 막지 못한다.** Kleppmann의 구분으로는 효율성 락이지 정확성 해법이 아니다. CAS 이후 별도로 판단한다.

- **차감과 상태 전이의 순서 뒤집기.** 상태를 먼저 확정하면 진 쪽이 차감을 시도조차 안 한다. 그러나 `settleApproved()`가 "차감 성공 여부로 `complete`/`fail`을 가르는" 구조라 로직이 복잡해진다. 롤백이 차감을 되돌리므로 정확성은 이미 확보된다.

- **차감 원장 테이블 신설.** 충전에 `wallet_charge`가 있듯 차감에도 원장을 두면 UNIQUE로 멱등을 강제할 수 있다. 근본 해법이지만 스키마 변경 범위가 크다. CAS로 먼저 막고 필요해지면 판단한다.

- **`flushAutomatically = true` 추가.** `append()` 직후 flush 없이 `updateState()`를 부르는 테스트가 있어 필요할 것으로 예상했으나, **실측 결과 불필요했다.** `PaymentEntity`가 `GenerationType.IDENTITY`라 `save()` 시점에 INSERT가 즉시 실행된다. 근거 없는 옵션은 넣지 않았다.
