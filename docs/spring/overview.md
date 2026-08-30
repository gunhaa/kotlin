# Spring에서 Kotlin Coroutine — MVC vs WebFlux 개요

각 스택별 상세 사용법은 [webflux/coroutines.md](webflux/coroutines.md),
[mvc/coroutines.md](mvc/coroutines.md) 참고. 이 문서는 두 스택을 가로지르는
Java ↔ Kotlin 대응표와 선택 기준만 정리한다.

## Java 대응표 (한눈에 보기)

| Kotlin 코루틴 | java.util.concurrent | Spring WebFlux (Reactor) |
|---------------|----------------------|--------------------------|
| `suspend fun f(): T` | `CompletableFuture<T> f()` | `Mono<T> f()` |
| `fun f(): Flow<T>` | (직접적인 대응 없음, 보통 `Iterator`/콜백) | `Flux<T> f()` |
| `launch { ... }` | `executorService.submit { ... }` (자동 대기 없음) | `Mono.fromRunnable { ... }.subscribe()` |
| `async { ... }` / `await()` | `CompletableFuture.supplyAsync { ... }` / `.get()` | `Mono.zip(...)` |
| `withContext(Dispatchers.IO) { ... }` | `supplyAsync(supplier, ioExecutor)` | `.subscribeOn(Schedulers.boundedElastic())` |
| `supervisorScope` + `CoroutineExceptionHandler` | `.exceptionally { ... }` (Future 단위로 원래 격리됨) | `.onErrorResume { ... }` |
| `coRouter { }` | (해당 없음, MVC/애노테이션이 일반적) | `RouterFunctions.route()` |

## 상황별 선택 기준

| 상황 | 권장 방식 |
|------|-----------|
| 신규 프로젝트, I/O 대부분이 non-blocking | WebFlux + suspend/Flow |
| 기존 MVC 프로젝트에 부분적으로 비동기 호출 추가 | MVC + suspend fun, 블로킹 구간만 `withContext(Dispatchers.IO)` |
| 여러 외부 API를 동시에 호출해야 함 | `coroutineScope { async { ... } }`로 병렬화 (WebFlux/MVC 공통) |
| 코루틴에서 발생한 예외를 개별적으로 잡아야 함 | `supervisorScope` + `CoroutineExceptionHandler` ([coroutine/basics.md](../coroutine/basics.md) 참고) |

## 참고 자료

- [Coroutines :: Spring Framework Reference](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Going Reactive with Spring, Coroutines and Kotlin Flow](https://spring.io/blog/2019/04/12/going-reactive-with-spring-coroutines-and-kotlin-flow/)
