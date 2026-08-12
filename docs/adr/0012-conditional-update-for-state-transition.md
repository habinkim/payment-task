# 0012. 결제 상태 전이에 조건부 UPDATE를 쓴다

상태: Accepted

## 맥락

수평 확장을 가정하고 동시성을 검토하다 **이중 차감을 재현했다.** 이 결정의 유효 범위와 전제는 [ADR 0006](0006-single-server-no-middleware.md)에 함께 정리했다.

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

경로는 둘이다. **스케줄러**와 **관리자 API**가 같은 `confirm()`을 지난다. `findOldestUnknown()`은 `created_at ASC` 정렬의 평범한 SELECT라 정렬이 결정적이고, 실측 결과 두 번 호출 시 **3/3 전부 같은 건을 집었다.**

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

그는 **효율성 락**과 **정확성 락**을 가른다. 효율성 락은 실패해도 중복 작업 정도지만, 정확성 락은 실패하면 데이터 손실이다. 그리고 락은 사실 lease라, GC pause·페이지 폴트·네트워크 지연으로 클라이언트가 만료를 알아채지 못하면 안전하지 않은 쓰기를 한다. GitHub은 약 90초 패킷 지연을 겪은 적이 있다.

> You simply cannot make any assumptions about timing.

제대로 하려면 **fencing token**이 필요하고, 이는 리소스 쪽이 토큰을 검사해 거부해야 성립한다. Redlock에는 그 기능이 없다. 결론은 명확하다.

> 정확성이면 합의 시스템이나 최소한 제대로 된 트랜잭션 보장을 가진 DB를 쓰라.

이중 차감은 정확성 문제이므로 **락이 아니라 DB 제약으로 풀었다.**

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

동시성 제어 수단을 락의 종류별로 검토했다. 선택 기준은 **무엇을 잠그는가**와 **충돌이 얼마나 잦은가**다.

| 방식 | 잠그는 대상 | 유리한 상황 |
|---|---|---|
| 비관적 락 | 행 자체 (배타 락) | 충돌이 잦다. 대기가 재시도보다 싸다 |
| 낙관적 락 | 잠그지 않음 (버전 비교) | 충돌이 드물다. 재시도 비용이 낮다 |
| 분산 락 | 외부 저장소의 '이름' | 잠글 행이 없거나 프로세스 단위로 묶어야 한다 |
| **조건부 UPDATE (채택)** | **잠그지 않음 (상태 비교)** | **판정과 쓰기가 한 문장에 들어갈 때** |

- **비관적 락 (`SELECT ... FOR UPDATE`).** 조회 시점에 행에 배타 락을 걸어 다른 트랜잭션을 대기시킨다. 대기 후 진입한 쪽은 갱신된 최신 값을 읽으므로 갱신 누락이 원천적으로 없다. 정합성이 가장 확실하다.

  기각한 이유는 **락을 잡는 구간이 너무 길기 때문**이다. 이 문제를 풀려면 `findOldestUnknown()`부터 락을 걸어야 하는데, 그 사이에 **게이트웨이 호출(네트워크 왕복 수백 ms)이 끼어 있다**([ADR 0001](0001-external-call-outside-transaction.md)). 배치가 50건을 처리하는 동안 그 행들이 전부 잠긴다. 외부 호출을 트랜잭션 밖에 두기로 한 결정과 정면으로 충돌한다.

  락 순서가 꼬이면 데드락 위험도 있다. CAS는 같은 목적을 **락 없이, 쓰기 시점에만** 달성한다.

- **낙관적 락 (`@Version`).** 락을 걸지 않고 조회 시 읽은 버전을 UPDATE 조건에 넣는다. 버전이 다르면 다른 트랜잭션이 먼저 수정했다는 뜻이므로 실패한다. **채택한 CAS와 같은 계열**이다 — 둘 다 잠그지 않고 조건 불일치로 감지한다. 차이는 버전 컬럼을 두느냐, 도메인 상태를 조건으로 쓰느냐뿐이다.

  기각한 이유는 **도메인 오염**이다. `Payment`는 JPA 엔티티가 아닌 POJO라([ADR 0008](0008-separate-domain-and-entity.md)) 버전을 들고 다닐 수 없다. `restore()`로 복원할 때 버전까지 복원하면 도메인이 기술 필드를 갖게 되고, ADR 0008이 지킨 순수성이 무너진다. 컬럼 추가로 마이그레이션도 따라온다.

  그리고 **재시도 로직이 필요한데 여기선 무의미하다.** 낙관적 락은 실패 시 다시 읽고 다시 시도해야 값을 갖는데, 이 경우의 실패는 "다른 인스턴스가 이미 확정했다"는 뜻이라 재시도할 것이 없다. 스케줄러는 다음 주기에 어차피 다시 온다.

  결국 **버전 컬럼 없이 상태 자체를 버전으로 쓰는 것**이 이 도메인에 맞았다. 상태 머신이 단방향(`PENDING → {COMPLETED, FAILED, UNKNOWN}`, `UNKNOWN → {COMPLETED, FAILED}`)이라 상태가 버전 역할을 겸할 수 있다.

- **분산 락 (Redis Redlock 등).** 외부 저장소에 이름으로 락을 걸어 프로세스 경계를 넘어 배타성을 보장한다. 잠글 행이 없거나 여러 리소스를 묶어야 할 때 쓴다.

  **여기엔 잠글 행이 이미 있다.** `payment` 행 하나가 보호 대상이므로 외부 저장소를 끌어올 이유가 없다. 인프라(Redis)를 새로 도입하는 비용도 따라온다.

  더 결정적으로, [Kleppmann의 분석](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)이 **정확성 목적에는 쓰지 말라**고 한다. 락은 사실 lease이고, GC pause·페이지 폴트·네트워크 지연으로 만료를 알아채지 못한 클라이언트가 안전하지 않은 쓰기를 한다. 제대로 하려면 **fencing token**이 필요하고 이는 리소스 쪽이 토큰을 검사해 거부해야 성립하는데, Redlock에는 그 기능이 없다. antirez의 [반론](http://antirez.com/news/101)도 "효율성 락에는 충분하다"는 선에서 그친다.

  이중 차감은 정확성 문제다. 분산 락으로 풀면 **DB가 이미 제공하는 보장을 외부에서 재구현**하는 셈이 된다.

- **ShedLock으로 스케줄러 단일화.** 잡 실행 직전 공유 저장소에 락 레코드를 쓰고 성공한 인스턴스만 실행한다. 기존 `@Scheduled`를 그대로 두므로 도입 비용이 가장 낮다([The New Stack](https://thenewstack.io/rethinking-java-scheduled-tasks-in-kubernetes/)). 그러나 **관리자 API를 통한 동시 확정은 막지 못한다.** Kleppmann의 구분으로는 효율성 락이지 정확성 해법이 아니다. CAS 이후 별도로 판단하며, 도입 시 `lockAtMostFor`를 실제 작업 시간보다 길게 잡는 것이 핵심이다(짧으면 느린 잡이 중복 실행된다).

- **차감과 상태 전이의 순서 뒤집기.** 상태를 먼저 확정하면 진 쪽이 차감을 시도조차 안 한다. 그러나 `settleApproved()`가 "차감 성공 여부로 `complete`/`fail`을 가르는" 구조라 로직이 복잡해진다. 롤백이 차감을 되돌리므로 정확성은 이미 확보된다.

- **차감 원장 테이블 신설.** 충전에 `wallet_charge`가 있듯 차감에도 원장을 두면 UNIQUE로 멱등을 강제할 수 있다. 근본 해법이지만 스키마 변경 범위가 크다. CAS로 먼저 막고 필요해지면 판단한다.

- **`flushAutomatically = true` 추가.** `append()` 직후 flush 없이 `updateState()`를 부르는 테스트가 있어 필요할 것으로 예상했으나, **실측 결과 불필요했다.** `PaymentEntity`가 `GenerationType.IDENTITY`라 `save()` 시점에 INSERT가 즉시 실행된다. 근거 없는 옵션은 넣지 않았다.
