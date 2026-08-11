# 0010. 저장소 인터페이스를 도메인에 두고 구현을 인프라에 둔다

상태: Accepted

## 맥락

[ADR 0008](0008-separate-domain-and-entity.md)에서 `PaymentLedgerStore`를 `infra` 패키지에 두고 "서비스는 캡슐을 통해 도메인 객체를 받는다"고 정했다. 도메인 객체와 JPA 엔티티는 확실히 갈렸고, 서비스는 `PaymentEntity`를 보지 않는다.

그런데 방향이 한쪽으로만 흐르지 않았다. 측정하니 세 가지가 나왔다.

**하나. 인프라가 서비스를 역참조했다.**

```java
// PaymentLedgerStore.java:5
import com.switchwon.payment.payment.service.PaymentSearchCondition;
```

`service → infra`와 `infra → service`가 동시에 성립하는 순환이었다.

**둘. 아키텍처 테스트가 이것을 잡지 못했다.** 규칙 16개 중 계층 관련 규칙에 `"도메인은 인프라를 알지 못한다"`는 있었으나 `"인프라는 서비스를 알지 못한다"`가 없었다. 상상한 방향만 막혀 있었고 나머지는 열려 있었다.

**셋. 스프링 데이터 타입이 인프라 밖으로 샜다.**

| 위치 | 누수 |
|---|---|
| `PaymentQueryService` | `Page` 반환, `PageRequest`·`Sort` 직접 조립 |
| `PaymentController` | `Page<Payment>` 지역변수 |
| `common/PageResponse` | `of(Page<E>, ...)` 파라미터 |

서비스가 `PageRequest.of(page, size, Sort.by(...))`를 만들고 있었다. 페이징 구현을 바꾸면 비즈니스 로직이 따라 바뀐다.

세 번째가 특히 문제다. 서비스는 "어떤 조건으로 몇 건을 본다"만 알면 되는데 "스프링 데이터로 페이징한다"까지 알고 있었다.

## 결정

**저장소의 계약은 도메인이 정의하고, 기술 구현은 인프라가 제공한다.**

```
payment/
├── domain/   PaymentLedgerStore (interface)     ← 무엇이 필요한가
└── infra/    JpaPaymentLedgerStore (impl)       ← 어떻게 하는가
```

의존 방향이 한쪽으로만 흐른다.

```
Controller ──> Service ──> PaymentLedgerStore (domain interface)
                                    ▲
                        JpaPaymentLedgerStore (infra) ──┘  구현으로 연결
```

인프라가 도메인을 향하고, 도메인은 인프라를 모른다.

### 페이징도 도메인 타입으로 주고받는다

`common/page`에 프레임워크 없는 타입 둘을 둔다.

```java
public record PageQuery(int page, int size) { }
public record PageResult<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) { }
```

`PageResult`의 필드는 기존 `PageResponse`와 같으므로 **JSON 응답 계약이 바뀌지 않는다.**

정렬(`Sort.by(DESC, "createdAt")`)은 구현체 안으로 넣었다. `"createdAt"`은 영속 필드명이고 고정값이라 도메인이 알 이유가 없다.

반면 **`size` 상한(100)은 서비스에 남겼다.** 상한은 "이 API가 한 번에 허용하는 양"이라 정책이고, 정책이 바뀔 때 도메인이 따라 바뀌면 안 된다. `PageQuery`는 `size < 1` 같은 불변식만 막는다.

### 이름은 `Store`를 유지한다

기존 규칙 두 개가 이름에 걸려 있다.

```java
noClasses().that().haveSimpleName("PaymentService").or().haveSimpleName("ReconcileService")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("Store")
noClasses().that().haveSimpleNameEndingWith("Scheduler")
        .should().dependOnClassesThat().haveSimpleNameEndingWith("Store")
```

`Port`나 `Repository`로 개명하면 이 규칙들이 **실패 없이 감시 대상을 잃는다.** 오케스트레이터가 저장소를 직접 잡는 것을 막는 유일한 방어선이 조용히 사라진다. 테스트가 초록색인데 보호는 없는 상태가 가장 나쁘다.

### 규칙으로 되돌아가지 못하게 막는다

| 규칙 | 막는 것 |
|---|---|
| 인프라는 서비스를 알지 못한다 | 이번 역류의 재발 |
| 스프링 데이터 타입은 인프라 밖으로 나가지 않는다 | 페이징 기술 누수 |
| 서비스는 저장소 구현이 아니라 인터페이스에 의존한다 | 구현 결합 |
| 저장소 인터페이스는 도메인에 / 구현은 인프라에 | 위치 역전 |

네 규칙 모두 **위반을 주입해 해당 규칙만 실패하는지 확인했다.** 통과만으로는 규칙이 무언가를 지킨다는 증거가 되지 않는다.

## 결과

서비스 패키지에서 `infra`와 `org.springframework.data` import가 **0건**이 됐다. 이것이 "비즈니스 로직이 인프라를 바라보지 않는다"의 직접 증거다.

얻은 것과 잃은 것이 있다.

- 페이징 구현을 바꿔도 서비스가 안 바뀐다. 다만 지금 그럴 계획은 없다 — **당장의 이득이 아니라 방향을 고정한 것**이다.
- 클래스가 2개 늘었다(인터페이스 2개). 46파일 규모에서 미미하다.
- 저장소를 mock으로 갈아끼울 수 있게 됐지만, 현재 단위 테스트는 `PaymentTransactionService`를 mock하므로 **실제로 쓰지는 않는다.** 이 이득을 근거로 삼지 않는다.
- 테스트가 176 → 180건. 늘어난 4건은 새 규칙이다.

호출부 12곳과 테스트 9개 파일이 바뀌었으나 전부 타입 주입이라 import만 이동했다.

## 검토한 대안

- **규칙만 추가하고 구조는 그대로 둔다.** `"인프라는 서비스를 알지 못한다"` 한 줄이면 이번 역류는 막힌다. 가장 싼 해법이고 진지하게 고려했다. 기각한 이유는 **이번에 발견한 구멍만 막기 때문**이다. 규칙은 상상한 위반만 잡는데, 이번 결함 자체가 "상상하지 못한 방향이 뚫린다"는 증거였다. 인터페이스를 도메인에 두면 방향이 구조로 고정된다.

- **멀티 모듈로 분리한다**(`domain`/`storage`/`api`). 컴파일러가 의존 방향을 강제하므로 규칙을 빠뜨릴 수 없다. 기각이 아니라 **후속 단계로 미뤘다.** 지금 쪼개면 위의 순환 의존 때문에 빌드 자체가 되지 않으므로 이 작업이 선행 조건이다. 46파일에 모듈 4개는 과설계로 읽힐 위험도 있다. 코드가 더 커지고 팀·재사용·빌드시간 중 하나라도 실제 압력이 되면 그때 판단한다.

- **도메인별 수직 분할**(`payment`/`wallet`/`gateway` 각각 모듈). 측정해보니 도메인 간 의존은 `payment → wallet` 단방향 3건, 역방향 0건으로 **이미 깨끗했다.** 깨진 것은 계층 간이었으므로 수직으로 쪼개도 이번 문제가 풀리지 않는다.

- **`Pageable`을 그대로 쓰고 컨트롤러에서 받는다.** 스프링 MVC의 argument resolver를 쓰면 코드가 줄어든다. 그러나 스프링 데이터 타입이 컨트롤러까지 올라와 누수 범위가 오히려 넓어진다.

- **`size` 상한을 `PageQuery` 생성자로 옮긴다.** 검증이 한곳에 모이지만, 상한 초과 시 예외가 아니라 절삭하는 현재 동작(`size=500` → 100)과 `size=0`일 때의 400 응답을 도메인이 알아야 한다. 정책과 불변식을 섞지 않기로 했다.
