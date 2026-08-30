# Spring MVC + JPA 구조에서 Kotlin 쓰기

Controller → Service → Repository → Entity로 나뉜 전형적인 Spring MVC + JPA
프로젝트를 Kotlin으로 옮길 때, 각 계층의 Java 코드가 어떻게 바뀌고 어디서
문제가 생기는지 정리한다.

Kotlin의 기본값 세 가지가 JPA/Spring의 런타임 가정과 정면으로 충돌한다는 것이
이 문서 전체를 관통하는 주제다.

| Kotlin 기본값 | 충돌하는 상대 | 해결책 |
|---------------|---------------|--------|
| 클래스·멤버가 `final` | Spring AOP 프록시, Hibernate 지연 로딩 프록시 | `kotlin-spring` / `all-open` 플러그인 |
| 기본 생성자가 없음 | JPA가 요구하는 no-arg 생성자 | `kotlin-jpa` / `no-arg` 플러그인 |
| 타입이 non-null | "아직 채워지지 않은" 식별자, 조회 실패 | `Long? = null`, `findByIdOrNull` |

## 1. 빌드 설정 — 컴파일러 플러그인이 먼저다

Java Spring Boot 프로젝트는 플러그인 두 개면 끝난다.

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.1'
    id 'io.spring.dependency-management' version '1.1.7'
}
```

Kotlin은 언어 기본값을 프레임워크에 맞춰 되돌리는 컴파일러 플러그인 설정이
추가로 필요하다. 아래는 [start.spring.io](https://start.spring.io)가 Kotlin +
Spring Web + Spring Data JPA + Validation 조합으로 실제 생성해주는 내용이다.

```kotlin
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"   // all-open 래퍼
    kotlin("plugin.jpa") version "2.3.21"      // no-arg 래퍼
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {                                       // JPA 엔티티도 열어준다
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
```

각 항목이 왜 필요한지:

| 설정 | 이유 |
|------|------|
| `kotlin("plugin.spring")` | `@Component`(`@Service`/`@Repository`/`@Controller`/`@Configuration`), `@Transactional`, `@Async`, `@Cacheable`, `@SpringBootTest`가 붙은 클래스와 그 멤버를 `open`으로 만든다. Kotlin 클래스는 기본이 `final`이라 CGLIB 프록시를 만들 수 없다. |
| `kotlin("plugin.jpa")` | `@Entity`, `@Embeddable`, `@MappedSuperclass`에 **합성(synthetic) no-arg 생성자**를 생성한다. Jakarta Persistence 명세가 요구하는 생성자다. |
| `allOpen { annotation("jakarta.persistence.Entity") }` | `plugin.jpa`는 생성자만 만들어줄 뿐 클래스를 열지 않는다. 엔티티가 `final`이면 Hibernate가 지연 로딩용 프록시를 만들지 못한다. |
| `kotlin-reflect` | Spring Data가 Kotlin의 null 허용 여부를 읽으려면 필요하다 (nullability는 시그니처가 아니라 Kotlin 메타데이터에 들어 있다). |
| `jackson-module-kotlin` | Kotlin 클래스의 JSON 직렬화/역직렬화용. 클래스패스에 있으면 Spring Boot가 자동 등록한다. |
| `-Xannotation-default-target=param-property` | Kotlin 2.2부터 바뀐 애노테이션 기본 타깃 규칙(`first-only` → `param-property`)을 명시적으로 켠다. |
| `-Xjsr305=strict` | Spring이 API에 붙여둔 null 관련 애노테이션을 엄격하게 해석한다. Spring Framework 7 / Boot 4는 [JSpecify](https://jspecify.dev/)를 쓰며, Kotlin 2.1부터 `org.jspecify.annotations`는 별도 플래그 없이도 엄격하게 적용된다. |

**핵심 차이**: Java에서는 "설정 없음"이 정상 동작이지만, Kotlin에서는 이
플러그인들이 빠지면 컴파일은 되고 런타임에 실패한다 (`@Transactional`이 조용히
무시되거나, Hibernate가 엔티티를 인스턴스화하지 못한다).

## 2. 엔티티 — `data class`를 쓰지 않는다

Java 엔티티는 no-arg 생성자와 getter/setter 보일러플레이트를 달고 다닌다.

```java
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CREATED;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() {}   // JPA가 요구하는 no-arg 생성자

    public Order(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    // getter / setter 생략
}
```

Kotlin에서는 같은 엔티티를 이렇게 쓴다. `data class`가 아니라 **일반 class**이고,
프로퍼티는 `val`이 아니라 **`var`**, 식별자는 **nullable**이다.

```kotlin
@Entity
@Table(name = "orders")
class Order(

    @Column(nullable = false, unique = true)
    var orderNumber: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.CREATED,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var lines: MutableList<OrderLine> = mutableListOf()
        protected set

    fun addLine(line: OrderLine) {
        lines.add(line)          // 참조를 갈아끼우지 않고 내용만 바꾼다
        line.order = this
    }
}
```

**핵심 차이**: 보일러플레이트(생성자·getter·setter)는 사라지지만, Kotlin이
"좋은 관습"으로 밀어주는 `data class` + `val`은 엔티티에서만큼은 쓰면 안 된다.
아래 주의사항이 그 이유다.

> `cascade = [CascadeType.ALL]`처럼 애노테이션의 배열 속성은 Kotlin에서 `[]`
> 리터럴로 쓴다 (`arrayOf(...)`도 가능).

### 주의사항 2-1. `data class`를 엔티티로 쓰지 않는다

`data class`가 자동 생성하는 세 가지가 전부 엔티티와 상성이 나쁘다.

| 자동 생성 | 엔티티에서 생기는 문제 |
|-----------|------------------------|
| `equals`/`hashCode` (모든 주 생성자 프로퍼티 기준) | 아직 저장되지 않은 엔티티는 `id`가 `null`이다가 `persist` 후 값이 생긴다. 그 사이 `hashCode`가 바뀌므로 `HashSet`/`HashMap`에 넣어둔 엔티티를 다시 찾지 못한다. 지연 로딩 연관관계까지 비교 대상에 들어가면 비교만으로 추가 쿼리가 나간다. |
| `toString` (모든 프로퍼티 출력) | 로그 한 줄에 지연 로딩 연관관계가 전부 초기화된다 (트랜잭션 밖이면 `LazyInitializationException`). |
| `copy()` | 같은 `id`를 가진 **준영속(detached) 복사본**이 만들어진다. 영속성 컨텍스트가 관리하지 않는 쌍둥이 객체가 돌아다니게 된다. |

Hibernate/Jakarta Persistence가 권장하는 방식은 **비즈니스 키(자연키) 기반의
`equals`/`hashCode`를 직접 구현**하는 것이다.

```kotlin
class Order(var orderNumber: String, /* ... */) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Order) return false        // 프록시(하위 타입)도 통과시킨다
        return orderNumber == other.orderNumber  // id가 아니라 비즈니스 키로 비교
    }

    override fun hashCode(): Int = orderNumber.hashCode()

    override fun toString(): String = "Order(id=$id, orderNumber='$orderNumber')"
    // 연관관계는 출력하지 않는다
}
```

Java에서 흔히 쓰는 `getClass() != o.getClass()` 비교는 Hibernate 프록시가
엔티티의 하위 클래스라서 실패한다. Kotlin의 `is` 검사(`other !is Order`)는
하위 타입을 통과시키므로 이 문제를 피한다.

### 주의사항 2-2. 프로퍼티는 `var`, 식별자는 `Long?`

- Jakarta Persistence 명세는 "엔티티 클래스는 non-final이어야 하고, 모든 메서드와
  **영속 인스턴스 변수도 non-final**이어야 한다"고 규정한다. Kotlin의 `val`은
  `final` 필드로 컴파일되므로 영속 필드는 `var`로 선언한다.
  (`orderNumber`처럼 생성 후 절대 바뀌지 않는 자연키에 한해 `val`을 쓰고
  Hibernate의 필드 접근에 맡기는 절충안을 택하는 팀도 있지만, 명세를 그대로
  따르려면 `var`가 안전하다. 대신 외부 변경을 막고 싶으면 `protected set`을 쓴다.)
- `@GeneratedValue` 식별자는 `persist` 전까지 값이 없다. `var id: Long? = null`이
  정직한 표현이고, `lateinit`은 `Long` 같은 원시 타입 래퍼에 쓸 수 없다.
- 외부에서 `id`를 바꾸지 못하게 하려면 `protected set`을 붙인다.
- 컬렉션은 빈 컬렉션으로 초기화한다 (Hibernate 공식 가이드 권장). Hibernate가
  로딩 시 자체 컬렉션 구현으로 감싸므로 **참조를 통째로 교체하지 말고**
  `add`/`remove`로 다룬다.

### 주의사항 2-3. no-arg 생성자는 프로퍼티 초기화식을 실행하지 않는다

`kotlin-jpa` 플러그인이 만드는 합성 생성자는 **리플렉션으로만 호출 가능**하고
(Java/Kotlin 코드에서는 보이지 않는다), 기본 설정에서는 **프로퍼티 선언부의
초기화 로직을 실행하지 않는다**.

```kotlin
@Entity
class Order {
    var createdAt: Instant = Instant.now()   // Hibernate가 만든 인스턴스에서는 실행되지 않는다
}
```

DB에서 로딩된 엔티티는 Hibernate가 이후에 필드를 직접 채우므로 대개 문제가 없지만,
"객체가 만들어지는 순간 반드시 실행돼야 하는" 초기화 로직을 프로퍼티 초기화식에
두면 안 된다. 꼭 필요하면 `noArg { invokeInitializers = true }`를 켠다.

## 3. Repository — `Optional<T>` 대신 nullable 타입

Java에서는 조회 실패를 `Optional`로 표현하고 호출부에서 풀어낸다.

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByStatus(OrderStatus status);
}

Order order = orderRepository.findById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));
```

Kotlin에서는 반환 타입의 `?` 하나가 `Optional`의 역할을 대신한다. Spring Data가
Kotlin의 nullability를 그대로 읽어 동작을 바꾼다.

```kotlin
interface OrderRepository : JpaRepository<Order, Long> {
    fun findByOrderNumber(orderNumber: String): Order?   // 없으면 null
    fun findByStatus(status: OrderStatus): List<Order>
}

import org.springframework.data.repository.findByIdOrNull

val order = orderRepository.findByIdOrNull(id) ?: throw OrderNotFoundException(id)
```

**핵심 차이**: `Optional`이라는 래퍼 객체를 거치지 않고 타입 시스템이 직접
"없을 수 있음"을 표현한다. `findByIdOrNull`은 Spring Data가 제공하는 확장 함수라
`org.springframework.data.repository.findByIdOrNull`를 **직접 import** 해야 한다.

### 주의사항 3-1. non-null 반환 타입은 예외를 던진다

```kotlin
fun findByOrderNumber(orderNumber: String): Order    // ? 없음
```

이렇게 선언하면 결과가 없을 때 `null`이 아니라
**`EmptyResultDataAccessException`**이 발생한다. "없으면 null" 시맨틱을 원하면
반드시 `Order?`로 선언한다.

### 주의사항 3-2. `kotlin-reflect`가 없으면 nullability가 무시된다

Kotlin의 null 허용 정보는 메서드 시그니처가 아니라 컴파일된 메타데이터에 들어
있다. Spring Data가 이를 읽으려면 `kotlin-reflect`가 클래스패스에 있어야 한다.

## 4. 서비스 — 생성자 주입과 `@Transactional`

Java 서비스는 필드 선언 / 생성자 파라미터 / 대입문을 세 번 반복한다.

```java
@Service
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    @Transactional
    public Order pay(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        paymentClient.charge(order);
        order.setStatus(OrderStatus.PAID);   // 더티 체킹
        return order;
    }
}
```

Kotlin은 주 생성자에 `private val`을 쓰면 선언·주입·대입이 한 번에 끝난다.

```kotlin
@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
    private val paymentClient: PaymentClient,
) {
    @Transactional
    fun pay(id: Long): Order {
        val order = orderRepository.findByIdOrNull(id) ?: throw OrderNotFoundException(id)
        paymentClient.charge(order)
        order.status = OrderStatus.PAID      // 더티 체킹 (setter 호출로 컴파일된다)
        return order
    }
}
```

**핵심 차이**: 생성자 파라미터에 `val`을 붙이는 것만으로 불변 필드 + 생성자 주입이
완성된다. Spring은 Kotlin 주 생성자를 통한 인스턴스화를 지원하므로 생성자가
하나면 `@Autowired`도 필요 없다 (Java와 동일).

### 주의사항 4-1. `@Autowired lateinit var` 대신 생성자 주입

```kotlin
@Autowired
lateinit var orderRepository: OrderRepository   // 지양
```

필드 주입은 Kotlin에서 `lateinit var`가 되어 불변성도, 초기화 보장도 잃는다.
Spring 공식 문서의 예시도 있지만 생성자 주입을 기본으로 삼는다. 참고로 Spring은
Kotlin의 nullability를 주입 필수 여부로 해석한다 — `lateinit var thing: Thing`은
필수 빈, `var thing: Thing?`는 선택 빈이다.

### 주의사항 4-2. `final` 클래스에는 프록시가 붙지 않는다

`@Transactional`은 프록시로 동작한다. `kotlin-spring` 플러그인이 없으면 `@Service`
클래스가 `final`이라 CGLIB 프록시를 만들 수 없고, **트랜잭션이 걸리지 않거나 컨텍스트
로딩이 실패한다**. 프록시 기반이라는 점에서 오는 제약은 Java와 동일하다.

- 같은 클래스 안에서의 자기 호출(self-invocation)은 프록시를 거치지 않으므로
  `@Transactional`이 적용되지 않는다.
- `private` 메서드에 붙여도 소용없다 (Spring 6부터 `protected`/package-private
  메서드는 클래스 기반 프록시에서 지원된다).

### 주의사항 4-3. MVC + JPA에서 `suspend fun` + `@Transactional` 조합은 쓰지 않는다

Spring의 코루틴 트랜잭션 지원은 **리액티브 트랜잭션 관리(`TransactionalOperator`)**
위에 올라가 있다. 반면 JPA(`JpaTransactionManager`)는 스레드에 묶인(thread-bound)
트랜잭션이다. `suspend fun`은 실행 스레드가 바뀔 수 있으므로 이 둘을 섞으면
트랜잭션이 의도대로 전파되지 않는다. Spring 팀의 답변도 "코루틴 트랜잭션은
WebFlux + R2DBC와 함께 써야 하고 WebMVC + JDBC와는 아니다"였다.

MVC + JPA에서 비동기가 필요하면, 트랜잭션 경계는 일반 함수(`@Transactional fun`)에
두고 그 바깥에서만 코루틴을 쓴다.

```kotlin
@Service
class OrderFacade(
    private val orderService: OrderService,     // @Transactional은 여기에만
    private val shippingClient: ShippingClient,
) {
    suspend fun placeAndNotify(request: PlaceOrderRequest): Order {
        val order = withContext(Dispatchers.IO) { orderService.place(request) } // 블로킹 JDBC
        shippingClient.notify(order)                                            // non-blocking
        return order
    }
}
```

## 5. 컨트롤러와 DTO

Java 16+ 에서는 `record`로 요청/응답 DTO를 만들고 검증 애노테이션을 붙인다.

```java
public record PlaceOrderRequest(
        @NotBlank String customerName,
        @Size(min = 1) List<OrderLineRequest> lines,
        String couponCode
) {}

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(orderService.place(request));
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam(required = false) OrderStatus status) { ... }
}
```

Kotlin에서는 DTO에 `data class`를 쓴다 (엔티티와 달리 DTO는 `data class`가 정답이다).

```kotlin
data class PlaceOrderRequest(
    @field:NotBlank val customerName: String,
    @field:Size(min = 1) val lines: List<OrderLineRequest>,
    val couponCode: String? = null,           // 기본값으로 선택 항목 표현
)

@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {

    @PostMapping
    fun place(@Valid @RequestBody request: PlaceOrderRequest): OrderResponse =
        OrderResponse.from(orderService.place(request))

    @GetMapping
    fun list(@RequestParam status: OrderStatus?): List<OrderResponse> = ...
    //                              ^ nullable = required = false
}
```

**핵심 차이**: `@RequestParam(required = false)`를 쓸 필요가 없다. Spring이
Kotlin 타입의 nullability를 그대로 "필수 여부"로 읽는다 (`@RequestParam`,
`@Header`, `@Autowired`, `@Bean` 파라미터 등에 공통 적용).

### 주의사항 5-1. 검증 애노테이션에는 사용 지점 타깃(`@field:`)을 붙인다

Kotlin의 주 생성자 프로퍼티 하나는 Java 바이트코드에서 생성자 파라미터·필드·
getter 세 곳이 될 수 있다. 타깃을 지정하지 않으면 애노테이션이 어디에 붙을지
Kotlin 규칙(`param` → `property` → `field` 순)에 따라 결정되어, Bean Validation이
읽지 못하는 자리에 붙을 수 있다. Spring 공식 문서도 프로퍼티/주 생성자 파라미터에
검증 애노테이션을 쓸 때는 `@field:NotNull`, `@get:Size(min=5, max=15)`처럼
사용 지점 타깃을 명시하라고 안내한다.

### 주의사항 5-2. Jackson과 non-null 프로퍼티

- `jackson-module-kotlin`이 없으면 주 생성자 기반 `data class` 역직렬화와
  기본값 처리가 동작하지 않는다 (Spring Boot는 클래스패스에 있으면 자동 등록한다).
- **원시 타입 함정**: non-null 원시 타입(`Int`, `Boolean` 등) 프로퍼티에 JSON이
  명시적으로 `null`을 보내면, 예외 대신 `0`/`false` 같은 의도치 않은 기본값으로
  처리가 계속된다. 모듈 README는 `DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES`를
  켜서 막으라고 안내한다.

### 주의사항 5-3. 엔티티를 그대로 응답으로 내보내지 않는다

Java에서도 통용되는 원칙이지만 Kotlin에서는 유혹이 더 크다 — 엔티티가 이미
클래스 하나로 깔끔하게 보이기 때문이다. 엔티티를 직렬화하면 지연 로딩 연관관계가
전부 초기화되거나 `LazyInitializationException`이 난다. 변환은 확장 함수나
`companion object` 팩토리로 한 곳에 모은다.

```kotlin
data class OrderResponse(val id: Long, val orderNumber: String, val status: OrderStatus) {
    companion object {
        fun from(order: Order) = OrderResponse(
            id = requireNotNull(order.id) { "저장되지 않은 주문입니다" },
            orderNumber = order.orderNumber,
            status = order.status,
        )
    }
}
```

`id: Long?`(엔티티) → `id: Long`(응답 DTO) 변환 지점에서 "저장된 엔티티인가"를
한 번 검사하게 되는 것이 nullable 식별자의 부수 효과다.

## 6. 테스트

```kotlin
@SpringBootTest
class OrderServiceTest(
    @Autowired private val orderService: OrderService,   // 생성자 주입
    @Autowired private val orderRepository: OrderRepository,
) {
    @Test
    fun `주문을 결제하면 상태가 PAID가 된다`() { ... }
}
```

- 테스트 클래스도 생성자 파라미터에 `@Autowired`를 붙여 주입받을 수 있다.
  (`@SpringBootTest`도 `kotlin-spring` 플러그인이 여는 애노테이션 목록에 있다.)
- 백틱으로 감싼 함수 이름을 테스트 이름으로 쓸 수 있다.
- Kotlin 클래스는 `final`이라 Mockito가 그대로 목킹하지 못한다. Spring Boot 공식
  문서는 **MockK**를, `@MockitoBean`/`@MockitoSpyBean` 대응이 필요하면
  **SpringMockK**(`@MockkBean`/`@MockkSpyBean`)를 권장한다.

## 주의사항 요약

| 계층 | 하지 말 것 | 대신 할 것 |
|------|-----------|-----------|
| 빌드 | `plugin.spring`/`plugin.jpa` 없이 시작 | 두 플러그인 + `allOpen`(엔티티) + `kotlin-reflect` |
| 엔티티 | `data class`, `val` 영속 필드, `copy()` | 일반 `class`, `var`, 비즈니스 키 `equals`/`hashCode` |
| 엔티티 | `toString()`에 연관관계 포함 | `id` + 비즈니스 키만 출력 |
| 엔티티 | 컬렉션 참조 통째로 교체 | 빈 컬렉션으로 초기화 후 `add`/`remove` |
| 리포지토리 | non-null 반환 타입으로 "없으면 null" 기대 | `Order?` 선언 또는 `findByIdOrNull` |
| 서비스 | `@Autowired lateinit var` 필드 주입 | 주 생성자 `private val` 주입 |
| 서비스 | `suspend fun` + `@Transactional` (JPA) | 트랜잭션은 일반 함수에, 코루틴은 그 바깥에 |
| 컨트롤러 | `@NotBlank`만 붙이기 | `@field:NotBlank` |
| 컨트롤러 | 엔티티를 응답 본문으로 반환 | `data class` DTO로 변환 |

## 관련 문서

- [basics.md](basics.md)
- [coroutines.md](coroutines.md)
- [../testing.md](../testing.md)
- [../webflux/r2dbc.md](../webflux/r2dbc.md)
- [../../stdlib/conventions.md](../../stdlib/conventions.md)

## 참고 자료

- [Kotlin :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/features/kotlin.html) — 플러그인, null 안전성, Jackson, 테스트(MockK), `@ConfigurationProperties`
- [All-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html) — `kotlin("plugin.spring")`이 여는 애노테이션 목록
- [No-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html) — `kotlin("plugin.jpa")`, 합성 생성자와 `invokeInitializers`
- [Annotations :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/annotations.html) — nullability = 필수 여부, `@field:`/`@get:` 사용 지점 타깃
- [Classes and Interfaces :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/classes-interfaces.html) — 주 생성자를 통한 인스턴스화
- [Coroutines :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html) — 코루틴 트랜잭션(`TransactionalOperator.executeAndAwait`)
- [spring-framework#26705](https://github.com/spring-projects/spring-framework/issues/26705) — "코루틴 트랜잭션은 WebFlux + R2DBC용, WebMVC + JDBC용이 아니다"
- [Using @Transactional :: Spring Framework](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) — 프록시 모드, 자기 호출, 메서드 가시성
- [Null Handling of Repository Methods :: Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/repositories/null-handling.html) — `kotlin-reflect`, `EmptyResultDataAccessException`
- [Annotation use-site targets](https://kotlinlang.org/docs/annotations.html#annotation-use-site-targets)
- [What's new in Kotlin 2.2.0](https://kotlinlang.org/docs/whatsnew22.html) — `-Xannotation-default-target=param-property`
- [Jakarta Persistence 3.2 — 2.1 The Entity Class](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2.html) — non-final 클래스/필드, no-arg 생성자
- [Hibernate 6.6 Introduction](https://docs.hibernate.org/orm/6.6/introduction/html_single/Hibernate_Introduction.html) — 엔티티 요구사항, `equals`/`hashCode`, 컬렉션 초기화
- [jackson-module-kotlin README](https://github.com/FasterXML/jackson-module-kotlin) — `FAIL_ON_NULL_FOR_PRIMITIVES`
