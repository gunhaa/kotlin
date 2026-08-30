# Spring WebFlux + R2DBC 구조에서 Kotlin 쓰기

WebFlux 애플리케이션의 데이터 계층(R2DBC)을 Kotlin으로 작성할 때의 구조와
주의사항을 정리한다.

MVC + JPA에서 "쓰지 말라"고 했던 것들 — `data class` 엔티티, `val` 프로퍼티 —
이 여기서는 정반대로 **정석**이 된다. 프록시와 지연 로딩에 의존하는 ORM이
아니라, 조회 결과를 그대로 객체에 매핑하는 단순한 오브젝트 매퍼이기 때문이다.

## 1. 빌드 설정

Java + Reactor 프로젝트는 스타터만 추가하면 된다.

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    runtimeOnly 'org.postgresql:r2dbc-postgresql'
}
```

Kotlin은 코루틴 브리지 의존성이 추가로 필요하다. 반대로 **JPA에서 필수였던
`kotlin("plugin.jpa")`와 엔티티용 `allOpen` 설정은 필요 없다** — R2DBC 엔티티는
프록시 대상이 아니고 no-arg 생성자도 요구하지 않는다.

```kotlin
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"    // 빈 프록시용. 여전히 필요하다
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
}
```

Spring Data의 코루틴 리포지토리는 `kotlinx-coroutines-core` /
`-reactive` / `-reactor` 세 가지를 요구한다 (1.7.0 이상).

## 2. 엔티티 — `data class` + `val`이 정석

Java에서는 매핑 애노테이션을 붙인 가변 클래스를 만든다.

```java
@Table("orders")
public class Order {

    @Id
    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private Instant createdAt;

    // 생성자 / getter / setter 생략
}
```

Kotlin에서는 불변 `data class`가 그대로 엔티티가 된다.

```kotlin
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("orders")
data class Order(
    @Id val id: Long? = null,                       // null이면 "새 엔티티"
    val orderNumber: String,
    val status: OrderStatus = OrderStatus.CREATED,
    val createdAt: Instant = Instant.now(),
)
```

**핵심 차이**: Spring Data는 Kotlin `data class`의 **주 생성자**로 인스턴스를
만들고, 프로퍼티를 채울 때는 `copy()`를 사용한다. 그래서 `val`만으로 이루어진
완전 불변 엔티티가 아무 문제 없이 동작한다. 결과가 `null`이거나 컬럼이 없으면
**기본 인자 값**이 적용되는 것도 그대로 지원된다.

### JPA 엔티티와의 대비

| 항목 | JPA (MVC) | R2DBC (WebFlux) |
|------|-----------|-----------------|
| `data class` | ✗ (`equals`/`copy`/`toString` 문제) | ✓ 권장 |
| `val` 프로퍼티 | ✗ (명세상 영속 필드는 non-final) | ✓ 권장 |
| `kotlin("plugin.jpa")` (no-arg) | 필요 | 불필요 |
| 엔티티 `allOpen` | 필요 (지연 로딩 프록시) | 불필요 |
| 연관관계 매핑 (`@OneToMany` 등) | 지원 | 없음 — 직접 조회/조립 |
| 더티 체킹 | 있음 (변경만 하면 flush) | 없음 — `save()`를 직접 호출 |
| 1차 캐시 / 영속성 컨텍스트 | 있음 | 없음 |
| 지연 로딩 | 있음 | 없음 (조회하면 즉시 완전한 객체) |
| 스키마 자동 생성 | `ddl-auto` | 없음 — `schema.sql` 등으로 직접 |

Spring Data Relational(JDBC/R2DBC)의 설계 원칙 자체가 "엔티티를 로드하면 SQL이
실행되고 그걸로 끝 — 지연 로딩도, 캐시도, 더티 트래킹도, 세션도 없다"이다.

### 주의사항 2-1. `save()`의 **반환값**을 써야 한다

불변 엔티티는 Spring Data가 `copy()`로 **새 인스턴스**를 만들어 생성된 식별자를
채운다. 원본 인스턴스의 `id`는 여전히 `null`이다.

```kotlin
val order = Order(orderNumber = "A-1001")
orderRepository.save(order)
println(order.id)                       // null — 원본은 그대로다

val saved = orderRepository.save(order)  // 반환값을 받아야 한다
println(saved.id)                       // 1
```

### 주의사항 2-2. `id`가 `null`이면 INSERT, 아니면 UPDATE

Spring Data는 "새 엔티티인가"를 식별자 상태로 판단한다. 자동 증가 컬럼이면
식별자가 설정되지 않았을 때(래퍼 타입은 `null`, 원시 타입은 `0`) 새 엔티티로 본다.
`Long?`을 쓰면 이 판정이 자연스럽게 맞아떨어진다. 반대로 **DB에서 발급하지 않는
식별자(UUID 등)를 직접 채워 넣는 경우, 항상 UPDATE로 취급되어 INSERT가 나가지
않는다** — 이때는 `Persistable<ID>`를 구현해 `isNew`를 직접 정의한다.

### 주의사항 2-3. 변경은 반드시 명시적으로

JPA에 익숙하면 트랜잭션 안에서 프로퍼티만 바꿔도 UPDATE가 나갈 것으로 기대하기
쉽다. R2DBC에는 더티 체킹이 없다. 불변 엔티티에서는 `copy()`로 새 값을 만들고
`save()`를 호출하는 것이 기본 흐름이다.

```kotlin
val paid = order.copy(status = OrderStatus.PAID)
orderRepository.save(paid)
```

## 3. 리포지토리 — `CoroutineCrudRepository`

Java에서는 `ReactiveCrudRepository`를 상속해 `Mono`/`Flux`를 다룬다.

```java
public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {
    Mono<Order> findByOrderNumber(String orderNumber);
    Flux<Order> findByStatus(OrderStatus status);
}
```

Kotlin에서는 `CoroutineCrudRepository`를 상속하면 `suspend fun`과 `Flow`로 쓸 수
있다.

```kotlin
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface OrderRepository : CoroutineCrudRepository<Order, Long> {
    suspend fun findByOrderNumber(orderNumber: String): Order?
    fun findByStatus(status: OrderStatus): Flow<Order>
    suspend fun findAllByStatus(status: OrderStatus): List<Order>
}
```

**핵심 차이**: `Mono<T>` → `suspend fun ...: T`, `Flux<T>` → `fun ...: Flow<T>`.
기반 인터페이스가 이미 코루틴 시그니처라, JPA 쪽에서 필요했던 `findByIdOrNull`
확장이 여기서는 필요 없다 — `CoroutineCrudRepository.findById`가 처음부터
`suspend fun findById(id: ID): T?`로 선언되어 있다.

### 주의사항 3-1. `CoroutineCrudRepository`를 상속해야 인식된다

공식 문서 표현 그대로, **코루틴 리포지토리는 `CoroutineCrudRepository`를 상속할
때만 발견된다**. `ReactiveCrudRepository`를 상속한 채 `suspend fun`만 선언하면
동작하지 않는다.

### 주의사항 3-2. `Flow`는 cold 스트림이다

`Flow<Order>`를 두 번 `collect`하면 쿼리가 **두 번** 실행된다. 결과를 여러 번
쓸 거라면 `toList()`로 한 번만 수집해 둔다.

## 4. 서비스와 트랜잭션 — 여기서는 `suspend` + `@Transactional`이 동작한다

MVC + JPA에서는 `suspend fun`에 `@Transactional`을 붙이면 안 된다고 했다.
JPA의 트랜잭션이 스레드에 묶여 있기 때문이다. R2DBC의 트랜잭션 매니저
(`R2dbcTransactionManager`)는 `ReactiveTransactionManager`라서, 코루틴 트랜잭션
지원이 그 위에서 동작한다.

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional
    public Mono<Order> place(String orderNumber, String sku) {
        return orderRepository.save(new Order(null, orderNumber))
                .flatMap(saved -> inventoryRepository.decrease(sku).thenReturn(saved));
    }
}
```

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val inventoryRepository: InventoryRepository,
) {
    @Transactional
    suspend fun place(orderNumber: String, sku: String): Order {
        val saved = orderRepository.save(Order(orderNumber = orderNumber))
        inventoryRepository.decrease(sku)
        return saved
    }
}
```

프로그래밍 방식이 필요하면 `TransactionalOperator`의 코루틴 확장을 쓴다.

```kotlin
import org.springframework.transaction.reactive.executeAndAwait

class OrderService(private val operator: TransactionalOperator) {
    suspend fun place(order: Order) = operator.executeAndAwait {
        orderRepository.save(order)
        inventoryRepository.decrease(order.sku)
    }
}
```

**핵심 차이**: `flatMap`으로 이어 붙이던 시퀀스가 순차적인 문장으로 펴진다.
트랜잭션 컨텍스트는 스레드가 아니라 `CoroutineContext`/Reactor Context를 타고
전파되므로 중간에 스레드가 바뀌어도 유지된다.

### 주의사항 4-1. 취소(cancel) 시그널은 롤백이다

Spring Framework 5.3부터 **구독 취소 시그널은 롤백을 유발한다**. 코루틴 쪽에서
`withTimeout`으로 잘라내거나 클라이언트가 연결을 끊으면 진행 중이던 트랜잭션이
롤백된다. 스트림 응답 중간에 취소가 발생할 수 있다는 점을 염두에 둔다.

### 주의사항 4-2. `ThreadLocal` 기반 코드는 동작하지 않는다

`MDC`, `SecurityContextHolder`(기본 전략), `TransactionSynchronizationManager` 등
스레드에 묶인 값들은 코루틴/리액티브 파이프라인에서 유지되지 않는다. 로깅 추적이
필요하면 Spring이 제공하는 `PropagationContextElement`를 코루틴 컨텍스트에 넣는다.

## 5. WebFlux 애플리케이션에서 하면 안 되는 것

| 하지 말 것 | 이유 / 대안 |
|-----------|------------|
| JPA·JDBC 등 블로킹 드라이버 사용 | 이벤트 루프 스레드를 점유해 전체 처리량이 무너진다. 불가피하면 `withContext(Dispatchers.IO)`로 격리하되, 그럴 바엔 MVC를 쓰는 편이 낫다 |
| 이벤트 루프에서 `runBlocking` | 같은 이유. 코루틴 진입점은 `suspend fun` 핸들러 자체다 |
| `Thread.sleep` / 블로킹 I/O | `delay()`, 논블로킹 클라이언트로 대체 |
| `ThreadLocal`(MDC 등) 의존 | `CoroutineContext` / Reactor Context로 전달 |
| `Flow`를 여러 번 collect | cold 스트림이라 쿼리가 반복 실행된다 — `toList()`로 한 번만 |
| 무한/대용량 스트림을 JSON 배열로 반환 | SSE(`text/event-stream`)나 NDJSON(`application/x-ndjson`)으로 |

## 주의사항 요약

| 계층 | 주의사항 |
|------|---------|
| 빌드 | `kotlinx-coroutines-reactor`가 없으면 `suspend`/`Flow` 브리지가 동작하지 않는다 |
| 엔티티 | `data class` + `val` 권장. `save()`의 **반환값**을 써야 생성된 `id`를 얻는다 |
| 엔티티 | 더티 체킹이 없다 — `copy()` 후 `save()` 명시 호출 |
| 엔티티 | 식별자를 직접 채우면 항상 UPDATE로 취급된다 → `Persistable` 구현 |
| 리포지토리 | 코루틴 리포지토리는 `CoroutineCrudRepository` 상속이 조건 |
| 서비스 | `@Transactional suspend fun`은 여기서는 동작한다 (리액티브 트랜잭션 매니저) |
| 서비스 | 취소 시그널 = 롤백 |
| 전역 | 블로킹 호출 금지, `ThreadLocal` 금지 |

## 관련 문서

- [coroutines.md](coroutines.md)
- [../mvc/jpa.md](../mvc/jpa.md)
- [../testing.md](../testing.md)

## 참고 자료

- [Kotlin Support :: Spring Data Relational](https://docs.spring.io/spring-data/relational/reference/kotlin.html) — 코루틴, 오브젝트 매핑, null 안전성
- [Coroutines :: Spring Data Relational](https://docs.spring.io/spring-data/relational/reference/kotlin/coroutines.html) — `CoroutineCrudRepository`, 필요한 kotlinx 의존성, "코루틴 리포지토리는 `CoroutineCrudRepository`를 상속할 때만 발견된다"
- [Object Mapping :: Spring Data Commons](https://docs.spring.io/spring-data/commons/reference/object-mapping.html) — data class 주 생성자 사용, `copy()`를 통한 프로퍼티 채움, 기본 인자 처리
- [Persisting Entities :: Spring Data R2DBC](https://docs.spring.io/spring-data/relational/reference/r2dbc/entity-persistence.html) — `save()`의 insert/update 판정, 자동 증가 식별자
- [Why Spring Data JDBC? :: Spring Data Relational](https://docs.spring.io/spring-data/relational/reference/jdbc/why.html) — 지연 로딩·캐시·더티 트래킹·세션이 없다는 설계 원칙
- [`CoroutineCrudRepository.kt`](https://github.com/spring-projects/spring-data-commons/blob/main/src/main/kotlin/org/springframework/data/repository/kotlin/CoroutineCrudRepository.kt) — `suspend fun findById(id: ID): T?` 등 시그니처
- [Coroutines :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html) — `executeAndAwait`, `Flow.transactional`, `PropagationContextElement`
- [Programmatic Transaction Management :: Spring Framework](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html) — 리액티브 트랜잭션, 5.3부터 취소 시그널이 롤백
