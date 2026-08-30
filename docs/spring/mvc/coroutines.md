# Spring MVC에서 Kotlin Coroutine 사용하기

코루틴 자체 개념(launch, async/await, withContext, 예외 처리)은
[../../coroutine/basics.md](../../coroutine/basics.md) 참고. 이 문서는 그 개념을
Spring MVC(서블릿 기반, 원래 블로킹 스택) 위에서 어떻게 쓰는지만 다룬다.

Spring Framework는 5.2부터 `@Controller`의 `suspend fun` 핸들러를 지원한다.
내부적으로 핸들러 호출을 `Mono`로 감싸 비동기 서블릿 처리로 브리지하는
방식이라, 컨트롤러 메서드 자체는 별도 스레드를 점유하지 않고 코루틴으로
실행된다.

## 기본 블로킹 컨트롤러 vs suspend 컨트롤러

전통적인 Java MVC 컨트롤러는 완전히 동기·블로킹이다. JDBC 호출이 서블릿
스레드를 그대로 점유한다 — 이것이 MVC의 기본 동작이다.

```java
@RestController
class UserController {

    private final UserRepository userRepository;

    UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/users/{id}")
    User getUser(@PathVariable String id) {
        return userRepository.findById(id); // JdbcTemplate 등, 블로킹 호출
    }
}
```

Kotlin에서는 같은 자리에 `suspend fun`을 쓸 수 있다.

```kotlin
@RestController
class UserController(private val userRepository: UserRepository) {

    @GetMapping("/users/{id}")
    suspend fun getUser(@PathVariable id: String): User {
        delay(10)
        return userRepository.findById(id)
    }
}
```

## Java의 비동기 흉내: CompletableFuture / DeferredResult

Java MVC에서 서블릿 스레드를 점유하지 않는 응답을 만들려면
`CompletableFuture<T>`나 `DeferredResult<T>`를 리턴 타입으로 썼을 것이다.

```java
@GetMapping("/users/{id}/async")
CompletableFuture<User> getUserAsync(@PathVariable String id) {
    return CompletableFuture.supplyAsync(() -> userRepository.findById(id), ioExecutor);
}
```

Kotlin의 `suspend fun getUser(@PathVariable id: String): User`는 겉보기엔
`CompletableFuture`를 쓰기 전의 완전히 동기적인 Java 메서드와 똑같이 생겼지만,
실제로는 위 `CompletableFuture` 버전처럼 서블릿 스레드를 점유하지 않고
비동기로 처리된다.

**핵심 차이**: **동기 코드처럼 보이는데 비동기로 동작한다**는 게 코루틴이
Java의 `CompletableFuture`/`DeferredResult` 대비 갖는 이점이다 — 리턴 타입을
감싸는 래퍼(`CompletableFuture<T>`)도, `.thenApply`/`.exceptionally` 체이닝도
필요 없다.

## 주의할 점

- **핸들러가 non-blocking일 때만 이득**이 있다. `suspend fun` 안에서 JDBC
  같은 블로킹 호출을 그대로 하면 여전히 스레드를 블로킹하므로,
  [../../coroutine/basics.md](../../coroutine/basics.md)의 `withContext(Dispatchers.IO)`
  패턴으로 블로킹 호출을 IO 디스패처로 옮겨야 한다.

  ```kotlin
  suspend fun getUser(@PathVariable id: String): User =
      withContext(Dispatchers.IO) { jdbcTemplate.queryForObject(...) }
  ```

- MVC는 애초에 서블릿 스레드 풀 기반이라, 코루틴을 쓴다고 해서 WebFlux
  수준의 처리량 이득을 자동으로 얻는 것은 아니다. 진짜 논블로킹 I/O
  (`WebClient`, R2DBC 등)와 결합해야 의미가 있다.
- `Flow<T>`, `Deferred<T>` 반환도 지원되며 각각 `Flux`/`Mono`처럼 처리된다.

## 참고 자료

- [Coroutines :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Asynchronous Requests :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
