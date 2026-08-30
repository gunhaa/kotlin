# Spring WebFlux에서 Kotlin Coroutine 사용하기

코루틴 자체 개념(launch, async/await, withContext, 예외 처리)은
[../../coroutine/basics.md](../../coroutine/basics.md) 참고. 이 문서는 그 개념을
Spring WebFlux 위에서 어떻게 쓰는지만 다룬다. WebFlux는 원래 논블로킹
스택이므로 코루틴과 궁합이 가장 좋다.

## 필요한 의존성

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")
}
```

`kotlinx-coroutines-reactor`(1.4.0 이상)가 클래스패스에 있어야 Spring이
`suspend fun` / `Flow` 핸들러를 Reactor의 `Mono` / `Flux`와 자동으로
상호 변환해준다. 이 변환은 Spring Framework가 컨트롤러 진입/반환 지점에서
내부적으로 처리하므로, 컨트롤러 코드에서 직접 `mono {}` 같은 브리지 함수를
호출할 필요는 없다 (`WebClient` 호출 결과를 받을 때는 예외 — 아래 참고).

## 반환 타입 대응표

| Java(Reactive) 스타일  | Kotlin(Coroutines) 스타일        |
|-------------------------|-----------------------------------|
| `Mono<Void>`            | `suspend fun handler()`           |
| `Mono<T>`               | `suspend fun handler(): T`        |
| `Flux<T>`               | `fun handler(): Flow<T>`          |
| `Mono<T>` (즉시 필요)   | `fun handler(value: T)`           |
| `Mono<T>` (지연 필요)   | `fun handler(supplier: suspend () -> T)` |

## 컨트롤러 기본형

Java + Reactor로 WebFlux 컨트롤러를 작성하면 이런 모습이다
(리포지토리가 `ReactiveCrudRepository`처럼 `Mono`/`Flux`를 반환한다고 가정).

```java
@RestController
class UserController {

    private final UserRepository userRepository;

    UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/{id}")
    Mono<User> getUser(@PathVariable String id) {
        return userRepository.findById(id);
    }

    @GetMapping("/users")
    Flux<User> getUsers() {
        return userRepository.findAll();
    }
}
```

Kotlin에서는 (리포지토리가 `CoroutineCrudRepository`처럼 코루틴을 지원한다고 가정)
`suspend fun` 하나가 `Mono<T>` 핸들러 하나, `Flow<T>` 반환이 `Flux<T>` 핸들러에
대응한다.

```kotlin
@RestController
class UserController(private val userRepository: UserRepository) {

    @GetMapping("/users/{id}")
    suspend fun getUser(@PathVariable id: String): User =
        userRepository.findById(id)

    @GetMapping("/users")
    fun getUsers(): Flow<User> = userRepository.findAll()
}
```

## WebClient로 여러 호출을 병렬로 묶기

Java + Reactor에서 두 개의 외부 호출을 병렬로 조합하려면 `Mono.zip`을 쓴다.

```java
@GetMapping("/users/{id}/summary")
Mono<Summary> getSummary(@PathVariable String id) {
    Mono<Profile> profile = client.get().uri("/profile/" + id)
            .retrieve().bodyToMono(Profile.class);
    Mono<List<Order>> orders = client.get().uri("/orders/" + id)
            .retrieve().bodyToMono(new ParameterizedTypeReference<List<Order>>() {});
    return Mono.zip(profile, orders)
            .map(t -> new Summary(t.getT1(), t.getT2()));
}
```

`WebClient`는 리액티브 타입(`Mono`/`Flux`)을 반환하므로, 코루틴에서 쓰려면
`kotlinx-coroutines-reactor`가 제공하는 확장 함수 `awaitBody()`,
`awaitBodyOrNull()`, `awaitExchange()` 등으로 직접 변환해야 한다. 여러 호출을
동시에 보내려면 [../../coroutine/basics.md](../../coroutine/basics.md)의
async/await 패턴을 그대로 사용한다.

```kotlin
@GetMapping("/users/{id}/summary")
suspend fun getSummary(@PathVariable id: String): Summary = coroutineScope {
    val profile = async {
        client.get().uri("/profile/$id")
            .retrieve().awaitBody<Profile>()
    }
    val orders = async {
        client.get().uri("/orders/$id")
            .retrieve().awaitBody<List<Order>>()
    }
    Summary(profile.await(), orders.await())
}
```

**핵심 차이**: Java/Reactor는 `map`/`flatMap`/`zip` 같은 연산자 체이닝으로
비동기 흐름을 "선언"하는 스타일이고, Kotlin 코루틴은 `suspend fun` 안에서
순서대로 값을 꺼내 쓰는 "명령형" 스타일이다. 실행 방식(논블로킹, 이벤트 루프)은
동일하고 겉모습만 다르다.

## 함수형 엔드포인트: `RouterFunction` / `coRouter { }`

애노테이션 대신 함수형 스타일을 쓴다면, Java는 `RouterFunctions.route()`를 쓴다.

```java
@Configuration
class RouterConfig {
    @Bean
    RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                .GET("/users/{id}", handler::getUser)
                .GET("/users", handler::getUsers)
                .build();
    }
}

class UserHandler {
    private final UserRepository userRepository;

    UserHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    Mono<ServerResponse> getUser(ServerRequest request) {
        return ServerResponse.ok()
                .body(userRepository.findById(request.pathVariable("id")), User.class);
    }

    Mono<ServerResponse> getUsers(ServerRequest request) {
        return ServerResponse.ok().body(userRepository.findAll(), User.class);
    }
}
```

Kotlin은 같은 것을 `coRouter { }` DSL로 표현한다.

```kotlin
@Configuration
class RouterConfig {
    @Bean
    fun userRoutes(handler: UserHandler) = coRouter {
        GET("/users/{id}", handler::getUser)
        GET("/users", handler::getUsers)
    }
}

class UserHandler(private val userRepository: UserRepository) {
    suspend fun getUser(request: ServerRequest): ServerResponse {
        val user = userRepository.findById(request.pathVariable("id"))
        return ServerResponse.ok().bodyValueAndAwait(user)
    }

    suspend fun getUsers(request: ServerRequest): ServerResponse =
        ServerResponse.ok().bodyAndAwait(userRepository.findAll())
}
```

## 트랜잭션

R2DBC 등 리액티브 트랜잭션 매니저를 쓸 때, Java에서는
`TransactionalOperator.transactional(Publisher)`로 시퀀스를 트랜잭션으로 감싼다.

```java
class OrderService {
    private final TransactionalOperator operator;
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    Mono<Void> placeOrder(Order order) {
        Mono<Void> sequence = orderRepository.save(order)
                .then(inventoryRepository.decrease(order.getItemId(), order.getQuantity()));
        return operator.transactional(sequence);
    }
}
```

Kotlin은 `TransactionalOperator`의 코루틴 확장 `executeAndAwait`을 쓴다.

```kotlin
class OrderService(private val operator: TransactionalOperator) {
    suspend fun placeOrder(order: Order) = operator.executeAndAwait {
        orderRepository.save(order)
        inventoryRepository.decrease(order.itemId, order.quantity)
    }
}
```

## 참고 자료

- [Coroutines :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Functional Endpoints :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webflux-functional.html)
- [Programmatic Transaction Management :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html)
- [Going Reactive with Spring, Coroutines and Kotlin Flow](https://spring.io/blog/2019/04/12/going-reactive-with-spring-coroutines-and-kotlin-flow/)
