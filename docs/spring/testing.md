# Spring 애플리케이션 테스트 (Kotlin)

Java Spring 프로젝트의 테스트 코드를 Kotlin으로 옮길 때 무엇이 그대로이고
무엇이 달라지는지, 그리고 Kotlin이라서 새로 필요한 라이브러리는 무엇인지
정리한다.

가장 큰 차이 하나만 먼저 짚으면 — **Kotlin 클래스는 `final`이라 Mockito가 기본
설정으로는 목(mock)을 만들지 못한다.** 테스트 스택 선택이 여기서 갈린다.

## 1. 테스트 라이브러리 지도

| 라이브러리 | 무엇을 하는가 | Java 프로젝트에서의 대응 |
|-----------|--------------|------------------------|
| JUnit Jupiter | 테스트 실행·생명주기 (기술별 `spring-boot-starter-*-test`가 전이로 가져온다) | 동일 |
| `kotlin-test-junit5` | `assertEquals`/`assertTrue` 등 Kotlin 어서션 + JUnit5 연동 | JUnit 어서션 |
| AssertJ | `assertThat(...)` 체이닝 어서션 (위 스타터에 포함) | 동일 |
| **MockK** | Kotlin용 목킹. `final` 클래스와 `suspend` 함수를 그대로 목킹 | Mockito |
| **SpringMockK** | `@MockkBean` / `@MockkSpyBean` — 스프링 컨텍스트의 빈을 MockK 목으로 교체 | `@MockitoBean` / `@MockitoSpyBean` |
| **kotlinx-coroutines-test** | `runTest`, 가상 시간, 테스트 디스패처 | (대응 없음) |
| MockMvc + Kotlin DSL | MVC 웹 계층 테스트 (`spring-test`) | MockMvc |
| WebTestClient + Kotlin 확장 | WebFlux HTTP 테스트 (`spring-test`) | WebTestClient |
| RestTestClient + Kotlin 확장 | MVC 엔드포인트를 WebTestClient 스타일로 검증 (Spring Framework 7이 추가) | 동일 |
| Reactor `StepVerifier` | `Mono`/`Flux` 검증 (`reactor-test`) | 동일 |
| Testcontainers + `@ServiceConnection` | 실제 DB/브로커 컨테이너 | 동일 |
| Kotest (선택) | 스펙 스타일(`FunSpec` 등) 테스트 프레임워크와 `shouldBe` 어서션. JUnit 플랫폼 러너(`kotest-runner-junit5`)로 실행 | (대응 없음) |

Spring Boot 4부터 테스트 의존성은 **기술별 스타터**로 나뉜다. `spring-boot-starter-test`
하나를 선언하던 자리에 `spring-boot-starter-webmvc-test`,
`spring-boot-starter-data-jpa-test`처럼 테스트할 기술의 스타터를 선언한다
(각 스타터가 `spring-boot-starter-test`를 전이 의존성으로 가져오므로 JUnit·AssertJ·
Mockito·`spring-test`는 그대로 따라온다).

```kotlin
dependencies {
    // 테스트할 기술의 스타터를 고른다 (Boot 4)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    // WebFlux/R2DBC라면
    // testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    // testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc-test")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk-jvm:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:5.0.1")   // 5.x = Spring Framework 7 / Boot 4
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

| 스타터 | 함께 들어오는 것 |
|--------|-----------------|
| `spring-boot-starter-webmvc-test` | `spring-boot-starter-test`, `spring-boot-webmvc-test`(`@WebMvcTest`), `spring-boot-resttestclient`(`RestTestClient`/`TestRestTemplate`) |
| `spring-boot-starter-webflux-test` | `spring-boot-starter-test`, `spring-boot-webflux-test`(`@WebFluxTest`), `spring-boot-webtestclient`, `reactor-test` |
| `spring-boot-starter-data-jpa-test` | `spring-boot-starter-test`, `spring-boot-data-jpa-test`(`@DataJpaTest`), `spring-boot-jpa-test`(`TestEntityManager`), JDBC 테스트 지원 |
| `spring-boot-starter-data-r2dbc-test` | `spring-boot-starter-test`, `spring-boot-data-r2dbc-test`(`@DataR2dbcTest`), `reactor-test` |

## 2. 단위 테스트 — Mockito에서 MockK로

Java에서는 Mockito로 협력 객체를 대체한다.

```java
class OrderServiceTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final PaymentClient paymentClient = mock(PaymentClient.class);
    private final OrderService orderService = new OrderService(orderRepository, paymentClient);

    @Test
    void pays_an_order() {
        Order order = new Order("A-1001");
        given(orderRepository.findById(1L)).willReturn(Optional.of(order));

        orderService.pay(1L);

        verify(paymentClient).charge(order);
    }
}
```

Kotlin에서는 MockK를 쓴다. API 모양은 비슷하지만 `every { ... } returns ...`
람다 블록이라는 점이 다르다.

```kotlin
class OrderServiceTest {

    private val orderRepository = mockk<OrderRepository>()
    private val paymentClient = mockk<PaymentClient>(relaxed = true)
    private val orderService = OrderService(orderRepository, paymentClient)

    @Test
    fun `주문을 결제하면 결제 클라이언트를 호출한다`() {
        val order = Order(orderNumber = "A-1001")
        every { orderRepository.findByIdOrNull(1L) } returns order

        orderService.pay(1L)

        verify { paymentClient.charge(order) }
    }
}
```

**핵심 차이**: Mockito는 기본 설정에서 `final` 클래스를 목킹하지 못하는데 Kotlin은
클래스가 기본으로 `final`이다. Spring Boot 공식 문서도 **Kotlin 클래스 목킹에는
MockK를 권장**한다. 테스트 이름은 백틱으로 감싸 한글 문장으로 쓸 수 있다.

> MockK 목은 기본이 **strict**다 — 스텁하지 않은 호출은 예외가 된다.
> 필요하면 `mockk(relaxed = true)`, `@MockkBean(relaxed = true)`를 쓴다.
> (Mockito는 반대로 스텁하지 않은 호출에 `null`/기본값을 돌려준다.)

## 3. suspend 함수 테스트 — `runTest`

Java에서 비동기 코드를 테스트하려면 결과를 블로킹해서 기다린다.

```java
@Test
void fetches_summary() {
    CompletableFuture<Summary> future = service.getSummary("u1");
    Summary summary = future.join();          // 실제로 기다린다
    assertThat(summary.getName()).isEqualTo("Gunhaa");
}
```

Kotlin에서는 `runTest`가 코루틴 테스트 본문을 열어준다. `runBlocking`과 달리
**`delay`가 자동으로 건너뛰어진다**(가상 시간). 목킹은 `coEvery`/`coVerify`처럼
`co` 접두사 버전을 쓴다.

```kotlin
@Test
fun `요약 정보를 조회한다`() = runTest {
    coEvery { userClient.fetch("u1") } returns User("Gunhaa")

    val summary = service.getSummary("u1")   // 내부에 delay(1000)이 있어도 즉시 끝난다

    assertThat(summary.name).isEqualTo("Gunhaa")
    coVerify(exactly = 1) { userClient.fetch("u1") }
}
```

시간에 의존하는 코드는 스케줄러로 직접 제어한다.

```kotlin
@Test
fun `타임아웃 후 재시도한다`() = runTest {
    val job = launch { retryingWorker.run() }

    testScheduler.advanceTimeBy(30.seconds)   // 가상 시간을 30초 진행
    testScheduler.runCurrent()

    coVerify(exactly = 3) { client.call() }
    job.cancel()
}
```

**핵심 차이**: Java는 실제로 기다리거나 Awaitility 같은 폴링 라이브러리를 쓰지만,
`runTest`는 가상 시간으로 즉시 끝난다. 테스트가 60초를 넘기면 `runTest`가
취소시키므로 무한 대기도 잡힌다.

## 4. MVC 웹 계층 — `@WebMvcTest` + MockMvc Kotlin DSL

Java의 MockMvc는 static import를 잔뜩 끌어와 `perform(...).andExpect(...)`를
체이닝한다.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OrderService orderService;

    @Test
    void returns_an_order() throws Exception {
        given(orderService.get(1L)).willReturn(new OrderResponse(1L, "A-1001"));

        mockMvc.perform(get("/orders/{id}", 1L).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.orderNumber").value("A-1001"));
    }
}
```

Kotlin에는 같은 MockMvc 위에 얹은 DSL이 있다. 요청 설정은 람다 안의 프로퍼티
대입으로, 검증은 중첩 블록으로 표현한다.

```kotlin
import org.springframework.test.web.servlet.get      // 확장 함수 import 필수

@WebMvcTest(OrderController::class)
class OrderControllerTest(@Autowired private val mockMvc: MockMvc) {

    @MockkBean
    private lateinit var orderService: OrderService

    @Test
    fun `주문을 조회한다`() {
        every { orderService.get(1L) } returns OrderResponse(1L, "A-1001")

        mockMvc.get("/orders/{id}", 1L) {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.orderNumber") { value("A-1001") }
        }
    }
}
```

**핵심 차이**: `MockMvcRequestBuilders.*` / `MockMvcResultMatchers.*` static import
더미가 사라지고, 요청·검증이 각각 하나의 블록으로 묶인다. `get`, `post`, `put`,
`delete`, `multipart` 등의 확장 함수는 `org.springframework.test.web.servlet`
패키지에서 **직접 import** 해야 한다.

> 빈 교체는 Mockito를 쓰면 `@MockitoBean`, MockK를 쓰면 SpringMockK의
> `@MockkBean`이다. 목 객체는 테스트 인스턴스 생성 이후에 주입되므로
> `lateinit var`로 선언한다 (생성자 주입 대상이 아니다).

> Spring Boot 4에서는 **`@SpringBootTest`만으로는 테스트 클라이언트가 자동 구성되지
> 않는다.** MockMvc가 필요하면 `@AutoConfigureMockMvc`, `TestRestTemplate`이면
> `@AutoConfigureTestRestTemplate`, `WebTestClient`면 `@AutoConfigureWebTestClient`,
> `RestTestClient`면 `@AutoConfigureRestTestClient`를 함께 붙인다. 위 예시처럼 슬라이스
> 애노테이션(`@WebMvcTest`/`@WebFluxTest`)을 쓰는 경우에는 슬라이스가 알아서 구성한다.

### 4-1. MVC에서도 `expectBody<T>()` — `RestTestClient`

Spring Framework 7은 `RestClient`를 감싼 테스트 클라이언트 `RestTestClient`를 추가했다.
MockMvc 위에서도, 실제로 뜬 서버 대상으로도 같은 API로 검증할 수 있다.

```java
@SpringBootTest
@AutoConfigureRestTestClient
class OrderControllerTest {

    @Test
    void returns_an_order(@Autowired RestTestClient restClient) {
        restClient.get().uri("/orders/1")
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .isEqualTo(new OrderResponse(1L, "A-1001"));
    }
}
```

```kotlin
import org.springframework.test.web.servlet.client.expectBody   // MVC용 확장

@SpringBootTest
@AutoConfigureRestTestClient
class OrderControllerTest(@Autowired private val restClient: RestTestClient) {

    @Test
    fun `주문을 조회한다`() {
        restClient.get().uri("/orders/1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody<OrderResponse>()                       // reified 타입 파라미터
            .isEqualTo(OrderResponse(1L, "A-1001"))
    }
}
```

**핵심 차이**: WebFlux에서만 쓰던 `expectBody<T>()` 스타일을 MVC에서도 쓸 수 있게
됐다. 확장 함수 패키지는 WebFlux 쪽(`org.springframework.test.web.reactive.server`)과
다르다 — MVC는 `org.springframework.test.web.servlet.client`이고, 이 패키지가 제공하는
것은 `expectBody<T>()`와 `returnResult<T>()` 두 개다.

## 5. 데이터 계층 슬라이스

JPA는 `@DataJpaTest`, R2DBC는 `@DataR2dbcTest`로 데이터 계층만 띄운다.

```kotlin
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager   // Boot 4 패키지

@DataJpaTest
class OrderRepositoryTest(
    @Autowired private val orderRepository: OrderRepository,
    @Autowired private val entityManager: TestEntityManager,
) {
    @Test
    fun `주문번호로 조회한다`() {
        entityManager.persist(Order(orderNumber = "A-1001"))
        entityManager.flush()

        val found = orderRepository.findByOrderNumber("A-1001")

        assertThat(found).isNotNull
    }
}
```

R2DBC 쪽은 코루틴 리포지토리라서 테스트 본문이 `runTest`로 감싸인다.

```kotlin
@DataR2dbcTest
class OrderRepositoryTest(@Autowired private val orderRepository: OrderRepository) {

    @Test
    fun `저장하면 id가 채워진다`() = runTest {
        val saved = orderRepository.save(Order(orderNumber = "A-1001"))

        assertThat(saved.id).isNotNull       // 원본이 아니라 반환값을 검증한다
    }
}
```

## 6. WebFlux 웹 계층 — `@WebFluxTest` + `WebTestClient`

```java
@WebFluxTest(OrderController.class)
class OrderControllerTest {

    @Autowired private WebTestClient webTestClient;
    @MockitoBean private OrderService orderService;

    @Test
    void returns_an_order() {
        given(orderService.get(1L)).willReturn(Mono.just(new OrderResponse(1L, "A-1001")));

        webTestClient.get().uri("/orders/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody(OrderResponse.class)
                .isEqualTo(new OrderResponse(1L, "A-1001"));
    }
}
```

```kotlin
import org.springframework.test.web.reactive.server.expectBody

@WebFluxTest(OrderController::class)
class OrderControllerTest(@Autowired private val webTestClient: WebTestClient) {

    @MockkBean
    private lateinit var orderService: OrderService

    @Test
    fun `주문을 조회한다`() {
        coEvery { orderService.get(1L) } returns OrderResponse(1L, "A-1001")

        webTestClient.get().uri("/orders/1")
            .exchange()
            .expectStatus().isOk
            .expectBody<OrderResponse>()                       // reified 타입 파라미터
            .isEqualTo(OrderResponse(1L, "A-1001"))
    }
}
```

**핵심 차이**: `expectBody(OrderResponse.class)`/`ParameterizedTypeReference`가
`expectBody<OrderResponse>()`가 된다. 목록은 `expectBodyList<T>()`, 결과 객체가
필요하면 `returnResult<T>()`를 쓴다 (모두 직접 import).

`Flow`를 반환하는 서비스라면 테스트에서는 `toList()`로 수집해 검증하는 편이
간단하다. `Mono`/`Flux`를 직접 다뤄야 하면 `StepVerifier`를 쓴다.

## 7. 통합 테스트 — 실제 서버 + Testcontainers

```kotlin
import org.springframework.boot.resttestclient.TestRestTemplate                          // Boot 4 패키지
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate                    // Boot 4에서는 이 애노테이션이 있어야 주입된다
@Import(ContainerConfig::class)
class OrderApiIntegrationTest(@Autowired private val restTemplate: TestRestTemplate) {

    @Test
    fun `주문을 생성하고 조회한다`() { ... }
}

@TestConfiguration(proxyBeanMethods = false)
class ContainerConfig {

    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
}
```

`@ServiceConnection`을 붙이면 컨테이너의 접속 정보가 `spring.datasource.*` 등
프로퍼티를 대체하도록 자동 설정된다 (`@DynamicPropertySource`를 직접 쓸 필요가
없다). Spring Boot 문서는 JUnit 확장(`@Testcontainers` + `@Container`)보다
**빈으로 등록하는 방식을 권장**한다 — 컨테이너 생명주기가 빈 생명주기와 맞물리고
애플리케이션 컨텍스트 캐시와도 잘 어울리기 때문이다.

Kotlin에서 이 권장이 특히 반가운 이유가 있다. `@Container`를 JUnit 확장 방식으로
쓰면 **컨테이너를 테스트 전체가 공유하도록 static 필드로 선언해야 하는데**,
Kotlin에는 static이 없어 `companion object` + `@JvmStatic` 조합이 필요하다.
빈 방식은 이 문제가 아예 없다.

## 8. Kotlin 테스트에서 자주 밟는 것

### 8-1. 생성자 주입 vs `lateinit var`

```kotlin
@SpringBootTest
class OrderServiceTest(
    @Autowired private val orderService: OrderService,   // 스프링이 주입: 생성자 주입 가능
) {
    @MockkBean
    private lateinit var paymentClient: PaymentClient    // 목 주입: lateinit var
}
```

컨텍스트에서 가져오는 빈은 생성자 파라미터로 받을 수 있지만, `@MockkBean`/
`@MockitoBean`처럼 테스트 인스턴스 생성 이후에 주입되는 것은 `lateinit var`여야
한다.

### 8-2. `@BeforeAll`은 static을 요구한다

JUnit 5의 `@BeforeAll`/`@AfterAll`은 기본적으로 static 메서드여야 하는데 Kotlin에는
static이 없다. 두 가지 선택지가 있다.

```kotlin
// (1) companion object + @JvmStatic
companion object {
    @JvmStatic
    @BeforeAll
    fun setUp() { ... }
}

// (2) 테스트 인스턴스를 클래스당 하나만 만들게 하고 일반 메서드로 쓴다
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderServiceTest {
    @BeforeAll
    fun setUp() { ... }        // static이 아니어도 된다
}
```

Spring Boot 문서도 "테스트 클래스를 한 번만 인스턴스화해 재사용할 수 있어
`@BeforeAll`/`@AfterAll`을 non-static 메서드에 쓸 수 있고, 이것이 Kotlin과 잘
맞는다"고 안내한다.

### 8-3. `runBlocking` 대신 `runTest`

`runBlocking`으로도 `suspend` 함수를 호출할 수는 있지만 `delay`가 실제로 기다린다.
코루틴 테스트에는 `runTest`를 쓴다.

### 8-4. Mockito를 계속 써야 한다면

MockK로 전면 교체하지 않는다면, Mockito가 `final` 클래스를 목킹하도록 inline mock
maker를 활성화해야 한다. 또는 목킹 대상 클래스에 `open`을 붙이거나 인터페이스를
추출한다. 새 코드라면 MockK 쪽이 마찰이 적다. SpringMockK는 5.x가 Spring Framework 7
(= Spring Boot 4) 대응 버전이고, 스파이 애노테이션 이름이 `@SpykBean`에서
`@MockkSpyBean`으로 바뀌었다.

## 9. Boot 4에서 바뀐 테스트 패키지·의존성

Spring Boot 4는 테스트 지원 모듈을 기술별로 쪼갰다. 애노테이션 이름은 그대로지만
**패키지가 바뀌었으므로 import를 고쳐야 한다.**

| 대상 | Boot 3 | Boot 4 |
|------|--------|--------|
| `@WebMvcTest`, `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@WebFluxTest` | `org.springframework.boot.test.autoconfigure.web.reactive` | `org.springframework.boot.webflux.test.autoconfigure` |
| `@AutoConfigureWebTestClient` | `org.springframework.boot.test.autoconfigure.web.reactive` | `org.springframework.boot.webtestclient.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `TestEntityManager` | `org.springframework.boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.jpa.test.autoconfigure` |
| `@DataR2dbcTest` | `org.springframework.boot.test.autoconfigure.data.r2dbc` | `org.springframework.boot.data.r2dbc.test.autoconfigure` |
| `TestRestTemplate` | `org.springframework.boot.test.web.client` | `org.springframework.boot.resttestclient` |
| `@AutoConfigureTestRestTemplate` / `@AutoConfigureRestTestClient` | (없음) | `org.springframework.boot.resttestclient.autoconfigure` |
| `@ServiceConnection` | `org.springframework.boot.testcontainers.service.connection` | 동일 |
| `@MockitoBean` / `@MockitoSpyBean` | `org.springframework.test.context.bean.override.mockito` | 동일 (`spring-test`) |

의존성 쪽에서 같이 달라지는 것:

- `spring-boot-starter-test`를 직접 선언할 일이 없어졌다 — 기술별 `*-test` 스타터가
  전이로 가져온다.
- `@MockBean`/`@SpyBean`은 제거됐다. `@MockitoBean`/`@MockitoSpyBean`(또는 SpringMockK의
  `@MockkBean`/`@MockkSpyBean`)을 쓴다.
- Jackson 3으로 올라가면서 Kotlin 모듈 좌표가 `com.fasterxml.jackson.module`에서
  **`tools.jackson.module:jackson-module-kotlin`**으로 바뀌었다.
- `@SpringBootTest`는 더 이상 테스트 클라이언트를 자동 구성하지 않는다 (위 4절 참고).

## 주의사항 요약

| 상황 | 주의사항 |
|------|---------|
| 의존성 (Boot 4) | `spring-boot-starter-test` 대신 기술별 `spring-boot-starter-*-test` |
| `@SpringBootTest` (Boot 4) | 테스트 클라이언트는 `@AutoConfigureMockMvc`/`@AutoConfigureTestRestTemplate`/`@AutoConfigureWebTestClient`/`@AutoConfigureRestTestClient`로 직접 켠다 |
| import (Boot 4) | 슬라이스 애노테이션·`TestEntityManager`·`TestRestTemplate` 패키지가 모두 바뀌었다 (9절) |
| 목킹 | Kotlin 클래스는 `final` — Mockito 기본 설정으로는 목킹 불가. MockK 권장 |
| 목 주입 | `@MockkBean`(SpringMockK) / `@MockitoBean`은 `lateinit var`로 선언 |
| MockK 기본값 | strict — 스텁 안 한 호출은 예외. 필요하면 `relaxed = true` |
| suspend 테스트 | `runBlocking`이 아니라 `runTest` (delay 자동 스킵, 60초 타임아웃) |
| suspend 목킹 | `every`/`verify`가 아니라 `coEvery`/`coVerify` |
| MockMvc DSL | `org.springframework.test.web.servlet.get` 등 확장 함수 import 필요 |
| WebTestClient | `expectBody<T>()`/`expectBodyList<T>()`/`returnResult<T>()`도 import 필요 (`org.springframework.test.web.reactive.server`) |
| RestTestClient | 확장 함수 패키지가 다르다 — `org.springframework.test.web.servlet.client` |
| `@BeforeAll` | `companion object` + `@JvmStatic` 또는 `@TestInstance(PER_CLASS)` |
| Testcontainers | `@Container`는 static 필드를 요구한다 — 빈(`@Bean` + `@ServiceConnection`) 방식 권장 |
| R2DBC 저장 검증 | `save()`의 반환값을 검증해야 한다 (원본 `id`는 여전히 null) |

## 관련 문서

- [mvc/basics.md](mvc/basics.md)
- [mvc/jpa.md](mvc/jpa.md)
- [webflux/r2dbc.md](webflux/r2dbc.md)
- [../coroutine/basics.md](../coroutine/basics.md)

## 참고 자료

- [Kotlin :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/features/kotlin.html) — 테스트 절: JUnit 인스턴스 재사용과 `@BeforeAll`, MockK / SpringMockK 권장
- [Testing Spring Boot Applications :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) — `@SpringBootTest` webEnvironment, 슬라이스 테스트, `@MockitoBean`/`@MockitoSpyBean`, `@AutoConfigureMockMvc`/`@AutoConfigureTestRestTemplate`/`@AutoConfigureRestTestClient`/`@AutoConfigureWebTestClient`
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — 기술별 test 스타터, 테스트 애노테이션 패키지 이동, `@MockBean`/`@SpyBean` 제거, Jackson 3 좌표 변경
- [RestTestClient :: Spring Framework](https://docs.spring.io/spring-framework/reference/testing/resttestclient.html) — `RestClient`를 감싼 테스트 클라이언트, MockMvc/실서버 바인딩
- [`RestTestClientExtensions.kt`](https://github.com/spring-projects/spring-framework/blob/main/spring-test/src/main/kotlin/org/springframework/test/web/servlet/client/RestTestClientExtensions.kt) — `expectBody<T>()`, `returnResult<T>()`
- [Testcontainers :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — `@ServiceConnection`, 빈 방식 권장
- [JUnit 5 :: Testcontainers](https://java.testcontainers.org/test_framework_integration/junit_5/) — static 필드는 공유, 인스턴스 필드는 테스트마다 기동
- [Web :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/web.html) — MockMvc Kotlin DSL
- [WebTestClient :: Spring Framework](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html) — `expectBody<T>()`, `expectBodyList<T>()`, `returnResult<T>()` 확장
- [`MockMvcExtensions.kt`](https://github.com/spring-projects/spring-framework/blob/main/spring-test/src/main/kotlin/org/springframework/test/web/servlet/MockMvcExtensions.kt) — DSL 확장 함수 시그니처
- [MockK](https://mockk.io/) — `mockk`, `every`/`returns`, `coEvery`/`coVerify`, `relaxed`, `spyk`
- [SpringMockK](https://github.com/Ninja-Squad/springmockk) — `@MockkBean`/`@MockkSpyBean`, MockK 목은 strict가 기본
- [kotlinx-coroutines-test](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) — `runTest`, 가상 시간, `TestScope`, `advanceTimeBy`/`advanceUntilIdle`
- [Kotest](https://kotest.io/) — 스펙 스타일 프레임워크와 어서션 (선택 사항)
