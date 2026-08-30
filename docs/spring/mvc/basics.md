# Spring MVC에서 Kotlin 쓰기

서블릿 기반 Spring MVC 애플리케이션의 뼈대 — 부트스트랩, 빈 등록, 컨트롤러,
예외 처리, 외부 HTTP 호출, 설정값 바인딩 — 을 Java에서 Kotlin으로 옮길 때
무엇이 어떻게 바뀌는지 정리한다.

## 1. 애플리케이션 진입점

Java는 `main`을 담을 클래스가 반드시 필요하고, `SpringApplication.run`에
클래스 리터럴을 넘긴다.

```java
@SpringBootApplication
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
```

Kotlin은 최상위 함수를 쓸 수 있고, `runApplication<T>()`가 reified 타입 파라미터로
클래스 리터럴을 대신한다.

```kotlin
@SpringBootApplication
class OrderApplication

fun main(args: Array<String>) {
    runApplication<OrderApplication>(*args)
}
```

`SpringApplication` 자체를 손봐야 하면 후행 람다로 설정한다.

```kotlin
fun main(args: Array<String>) {
    runApplication<OrderApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
```

**핵심 차이**: `main`을 감쌀 클래스가 없어지고, `OrderApplication::class.java`
같은 클래스 리터럴 전달이 타입 파라미터로 바뀐다 (`*args`는 스프레드 연산자).

## 2. 설정 클래스와 빈 등록

Java 설정 클래스는 `@Configuration` + `@Bean` 메서드다.

```java
@Configuration
public class AppConfig {

    @Bean
    public RestClient orderRestClient(RestClient.Builder builder) {
        return builder.baseUrl("https://api.example.com").build();
    }
}
```

Kotlin도 같은 모양이지만, 식 본문(expression body)으로 반환 타입 선언까지
생략할 수 있다.

```kotlin
@Configuration
class AppConfig {

    @Bean
    fun orderRestClient(builder: RestClient.Builder): RestClient =
        builder.baseUrl("https://api.example.com").build()
}
```

Kotlin에는 애노테이션 대신 쓸 수 있는 **빈 등록 DSL**도 있다. 등록 로직에 `if`,
`for`, 프로파일 분기 같은 일반 코드를 그대로 쓸 수 있는 것이 장점이다. Spring
Framework 7 기준으로는 `BeanRegistrarDsl`이 그 자리를 맡는다.

```kotlin
import org.springframework.beans.factory.BeanRegistrarDsl

class OrderBeanRegistrar : BeanRegistrarDsl({
    registerBean<OrderService>()
    registerBean<UserHandler>()
    registerBean {
        RestClient.builder().baseUrl(env.getRequiredProperty("api.base-url")).build()
    }
    profile("dev") {
        registerBean<FakePaymentClient>()
    }
})

@Configuration
@Import(OrderBeanRegistrar::class)
class OrderConfig
```

**핵심 차이**: `@Bean` 방식은 리플렉션 + 애노테이션 처리를 거치지만, 이 DSL은
람다로 등록된다. 컴포넌트 스캔/애노테이션을 쓰지 않는 선택지가 하나 더 생기는
셈이다.

> Spring Framework 5부터 있던 `beans { }` DSL(`BeanDefinitionDsl`)은 Framework 7에서
> **deprecated**됐다 — "Use BeanRegistrarDsl instead". 기존 코드의 `beans { }` +
> `addInitializers(...)` 조합은 위 형태로 옮긴다.

### 주의사항 2-1. `@Configuration` 클래스는 `inner`가 될 수 없다

Spring 공식 문서는 설정 클래스를 **최상위(top-level) 또는 중첩(nested) 클래스로
선언하되 `inner` 클래스로 만들지 말라**고 안내한다. Kotlin의 `inner` 클래스는
바깥 클래스 인스턴스 참조를 요구해서 Spring이 단독으로 인스턴스화할 수 없다.

```kotlin
class Outer {
    @Configuration
    inner class BadConfig      // 안 됨

    @Configuration
    class OkConfig             // 중첩 클래스는 괜찮다 (inner가 아님)
}
```

### 주의사항 2-2. `kotlin-spring` 플러그인이 없으면 `@Configuration`이 깨진다

`@Configuration`은 기본 설정(`proxyBeanMethods = true`)에서 CGLIB 프록시를
만든다. Kotlin 클래스는 `final`이라 프록시를 만들 수 없으므로
`kotlin("plugin.spring")`이 필요하다. (`@Component`, `@Transactional` 등도 동일)

## 3. 애노테이션 컨트롤러

Java 컨트롤러는 선택적 파라미터를 `required = false`로, 없을 수 있는 값을
`Optional`이나 `@Nullable`로 표현한다.

```java
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<OrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "20") int size) {
        return orderService.list(status, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }
}
```

Kotlin에서는 타입의 null 허용 여부와 기본값이 그 역할을 대신한다.

```kotlin
@RestController
@RequestMapping("/orders")
class OrderController(private val orderService: OrderService) {

    @GetMapping
    fun list(
        @RequestParam status: OrderStatus?,          // nullable = required = false
        @RequestParam size: Int = 20,                // 기본값
    ): List<OrderResponse> = orderService.list(status, size)

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<OrderResponse> =
        ResponseEntity.ok(orderService.get(id))
}
```

**핵심 차이**: Spring은 Kotlin 타입의 nullability를 그대로 "필수 여부"로 읽는다.
`@RequestParam`, `@RequestHeader`, `@Autowired`, `@Bean` 파라미터에 공통으로
적용된다 — `required = false`를 쓸 일이 사라진다.

## 4. 함수형 라우팅 (WebMvc.fn)

함수형 라우팅은 WebFlux 전용이 아니다. Spring MVC에도 `WebMvc.fn`이 있고,
Kotlin에는 그 위에 얹은 `router { }` DSL이 있다.

```java
@Configuration
public class RouterConfiguration {

    @Bean
    public RouterFunction<ServerResponse> mainRouter(UserHandler handler) {
        return RouterFunctions.route()
                .GET("/api/users", accept(APPLICATION_JSON), handler::findAll)
                .GET("/api/users/{id}", accept(APPLICATION_JSON), handler::findOne)
                .build();
    }
}
```

```kotlin
import org.springframework.web.servlet.function.router   // MVC용 (WebFlux는 reactive.function.server)

@Configuration
class RouterConfiguration {

    @Bean
    fun mainRouter(handler: UserHandler) = router {
        "/api".nest {
            accept(APPLICATION_JSON).nest {
                GET("/users", handler::findAll)
                GET("/users/{id}", handler::findOne)
            }
        }
        resources("/**", ClassPathResource("static/"))
    }
}
```

**핵심 차이**: 경로 접두사(`"/api".nest`)와 조건(`accept(...).nest`)이 중첩 블록으로
묶여 라우팅 표가 한눈에 들어온다. DSL이 그냥 Kotlin 코드라서 `if`/`for`로 라우트를
동적으로 등록할 수도 있다.

## 5. 예외 처리 — `@RestControllerAdvice` + sealed class

Java는 예외 계층을 클래스로 만들고 핸들러를 개별 메서드로 늘어놓는다.

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleStock(InsufficientStockException e) {
        return ResponseEntity.status(CONFLICT).body(new ErrorResponse(e.getMessage()));
    }
}
```

Kotlin에서는 도메인 예외를 sealed class로 닫아두고 `when`으로 한 번에 매핑할 수
있다. 새 예외를 추가하면 `when`이 컴파일 에러를 내므로 매핑 누락이 없다.

```kotlin
sealed class OrderException(message: String) : RuntimeException(message) {
    class NotFound(val id: Long) : OrderException("주문을 찾을 수 없습니다: $id")
    class InsufficientStock(val sku: String) : OrderException("재고 부족: $sku")
}

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(OrderException::class)
    fun handle(e: OrderException): ResponseEntity<ErrorResponse> {
        val status = when (e) {                       // else 없이 망라성 검사
            is OrderException.NotFound -> HttpStatus.NOT_FOUND
            is OrderException.InsufficientStock -> HttpStatus.CONFLICT
        }
        return ResponseEntity.status(status).body(ErrorResponse(e.message!!))
    }
}
```

**핵심 차이**: 핸들러 메서드 개수가 예외 종류만큼 늘어나는 대신, 매핑이 하나의
`when` 식에 모이고 컴파일러가 빠진 케이스를 잡아준다.

> Kotlin에는 checked exception이 없다. `throws` 선언이 사라지므로 "어떤 예외가
> 올라올 수 있는가"는 시그니처가 아니라 이런 sealed 계층이나 문서로 표현한다.

## 6. 외부 HTTP 호출 — `RestClient` 확장

Java는 제네릭 타입을 넘기려면 `ParameterizedTypeReference` 익명 클래스를 만들어야
한다 (JVM 타입 소거 때문).

```java
List<User> users = restClient.get()
        .uri("/users")
        .retrieve()
        .body(new ParameterizedTypeReference<List<User>>() {});
```

Kotlin 확장 함수는 reified 타입 파라미터로 이 문제를 없앤다.

```kotlin
import org.springframework.web.client.body

val users = restClient.get()
    .uri("/users")
    .retrieve()
    .body<List<User>>()          // 반환 타입은 List<User>? (nullable)
```

**핵심 차이**: `ParameterizedTypeReference` 보일러플레이트가 사라진다. 다만
`body<T>()`의 반환 타입은 **nullable(`T?`)**이다. 본문이 반드시 있어야 하면
`requiredBody<T>()`를 쓴다. 같은 방식의 확장이 `RestTemplate`, `WebClient`,
`JdbcTemplate`에도 있다.

## 7. 설정값 바인딩 — `@ConfigurationProperties`

Java는 가변 클래스 + getter/setter로 바인딩하거나, 생성자 바인딩을 위해 record를
쓴다.

```java
@ConfigurationProperties("order.payment")
public class PaymentProperties {
    private String apiToken;
    private Duration timeout = Duration.ofSeconds(3);
    // getter / setter
}
```

Kotlin은 생성자 바인딩 + `data class` + 불변 `val` 조합이 그대로 동작한다.
기본값도 Kotlin 기본 인자로 표현한다.

```kotlin
@ConfigurationProperties("order.payment")
data class PaymentProperties(
    val apiToken: String,
    val timeout: Duration = Duration.ofSeconds(3),
    val retry: Retry = Retry(),
) {
    data class Retry(val maxAttempts: Int = 3)
}
```

**핵심 차이**: 설정 클래스가 불변이 되고, "설정이 없으면 어떤 값인지"가 기본 인자로
코드에 드러난다. non-null 프로퍼티에 값이 없으면 기동 시점에 바인딩이 실패하므로
설정 누락을 런타임 NPE가 아니라 부팅 실패로 잡는다.

## 8. 요청/응답 DTO

DTO는 `data class`가 정답이다 (엔티티와 다르다).

```kotlin
data class PlaceOrderRequest(
    @field:NotBlank val customerName: String,
    @field:Size(min = 1) val lines: List<OrderLineRequest>,
    val couponCode: String? = null,
)
```

검증 애노테이션에는 **사용 지점 타깃(`@field:`)**을 붙여야 한다. Kotlin의 주 생성자
프로퍼티는 Java 바이트코드에서 생성자 파라미터·필드·getter 여러 곳이 될 수 있어,
타깃을 지정하지 않으면 Bean Validation이 읽지 못하는 자리에 붙을 수 있다.

`jackson-module-kotlin`은 클래스패스에 있으면 Spring Boot가 자동 등록하며, 없으면
주 생성자 기반 역직렬화와 기본값 처리가 동작하지 않는다. Spring Boot 4는 Jackson 3을
쓰므로 좌표가 **`tools.jackson.module:jackson-module-kotlin`**이다 (Boot 3의
`com.fasterxml.jackson.module`에서 바뀌었다). 참고로 Spring MVC는
`kotlinx.serialization`(JSON/CBOR/ProtoBuf)도 클래스패스에 있으면 자동으로 설정한다.

## 주의사항 요약

| 항목 | 내용 |
|------|------|
| 컴파일러 플러그인 | `kotlin("plugin.spring")` 없이는 `@Configuration`/`@Component`/`@Transactional` 프록시가 만들어지지 않는다 |
| 설정 클래스 | `inner class`로 선언하면 안 된다 (top-level 또는 nested) |
| 컨트롤러 파라미터 | `required = false` 대신 nullable 타입, 기본값은 Kotlin 기본 인자로 |
| DTO 검증 | `@NotBlank`가 아니라 `@field:NotBlank` |
| JSON | `jackson-module-kotlin` 필수 (Boot가 자동 등록), 원시 타입 null 처리 주의 |
| `RestClient.body<T>()` | 반환 타입이 `T?`다. non-null이 필요하면 `requiredBody<T>()` |
| 예외 | checked exception이 없으므로 `throws`로 실패를 알릴 수 없다 — sealed class 활용 |

## 관련 문서

- [jpa.md](jpa.md)
- [coroutines.md](coroutines.md)
- [../testing.md](../testing.md)
- [../../stdlib/conventions.md](../../stdlib/conventions.md)

## 참고 자료

- [Kotlin :: Spring Boot Reference](https://docs.spring.io/spring-boot/reference/features/kotlin.html) — `runApplication`, `@ConfigurationProperties` 생성자 바인딩, Jackson 모듈
- [Web :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/web.html) — `router { }`/`coRouter { }` DSL, MockMvc DSL, kotlinx.serialization
- [Extensions :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/extensions.html) — reified 타입 파라미터 확장 (`RestTemplate`, `RestClient`, `WebClient`, `JdbcTemplate`)
- [Programmatic Bean Registration :: Spring Framework](https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html) — `BeanRegistrar` / Kotlin `BeanRegistrarDsl`, `profile { }`, `@Import` 등록
- [`BeanDefinitionDsl.kt`](https://github.com/spring-projects/spring-framework/blob/main/spring-context/src/main/kotlin/org/springframework/context/support/BeanDefinitionDsl.kt) — `beans { }`는 Framework 7에서 deprecated (`Use BeanRegistrarDsl instead`)
- [Classes and Interfaces :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/classes-interfaces.html) — 설정 클래스는 top-level/nested, `inner` 금지
- [Annotations :: Spring Framework Kotlin](https://docs.spring.io/spring-framework/reference/languages/kotlin/annotations.html) — nullability = 필수 여부, `@field:` 사용 지점 타깃
- [All-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html)
- [`RestClientExtensions.kt`](https://github.com/spring-projects/spring-framework/blob/main/spring-web/src/main/kotlin/org/springframework/web/client/RestClientExtensions.kt) — `body<T>()`, `requiredBody<T>()` 시그니처
- [`RouterFunctionDsl.kt` (WebMvc.fn)](https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/kotlin/org/springframework/web/servlet/function/RouterFunctionDsl.kt)
