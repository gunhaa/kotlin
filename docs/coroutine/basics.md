# Kotlin Coroutine 기초

Spring과 무관한, 순수 Kotlin 언어/라이브러리 차원의 코루틴 개념만 다룬다.

실행 가능한 예제: `src/main/kotlin/com/kotlin/coroutine/Main.kt`
(`./gradlew run`으로 실행하면 아래 6개 예제가 순서대로 출력된다.)

## 1. 언어의 코루틴과 라이브러리의 코루틴

Java에서 동시성과 관련된 것은 **전부 라이브러리**다. 언어 문법에는 비동기를
표현하는 수단이 없고, `java.lang`이나 `java.util.concurrent`의 타입을 호출하는
것으로 표현한다.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);        // java.util.concurrent
CompletableFuture<Integer> f = CompletableFuture.supplyAsync(...);  // java.util.concurrent
Thread t = Thread.ofVirtual().start(() -> { ... });                 // java.lang
```

Kotlin은 여기가 두 층으로 나뉜다. **"중단·재개할 수 있는 함수"라는 개념 자체는
언어(컴파일러)가** 담당하고, **그 코루틴을 실제로 띄우고 스케줄링하는 것은 별도
라이브러리가** 담당한다.

```kotlin
suspend fun fetchValue(name: String, delayMs: Long): Int { ... } // suspend는 언어 키워드 — 의존성 없음

val one = async { fetchValue("A", 1000) }                        // async/Deferred는 kotlinx.coroutines
```

**핵심 차이**: Java는 동시성이 100% 라이브러리지만, Kotlin은 중단 규약만 언어가
정의하고 나머지를 라이브러리에 위임하는 2층 구조다. 그래서 `suspend`를 쓰는 데는
아무 의존성도 필요 없지만, `launch`/`async`/`delay`를 쓰려면
`kotlinx.coroutines` 의존성이 필요하다.

### 어느 층에서 온 것인가

| 층 | 제공 주체 | 대표 요소 | 필요한 의존성 |
| --- | --- | --- | --- |
| 언어 | Kotlin 컴파일러 | `suspend` 제어자, suspend 람다(`suspend () -> T`) | 없음 |
| 표준 라이브러리 `kotlin.coroutines` | kotlin-stdlib | `Continuation`, `CoroutineContext`, `ContinuationInterceptor`, `EmptyCoroutineContext`, `suspendCoroutine`, `createCoroutine`, `startCoroutine`, `coroutineContext`, `RestrictsSuspension` | 없음 |
| 코루틴 라이브러리 `kotlinx.coroutines` | 별도 아티팩트 | `launch`, `async`/`await`, `delay`, `runBlocking`, `coroutineScope`, `supervisorScope`, `withContext`, `Dispatchers`, `Job`, `Deferred`, `CoroutineScope`, `CoroutineExceptionHandler`, `Flow` | `org.jetbrains.kotlinx:kotlinx-coroutines-core` |

공식 문서도 같은 선을 긋는다 — 표준 라이브러리 `kotlin.coroutines` 패키지의
설명은 "Basic primitives for creating and suspending coroutines: `Continuation`,
`CoroutineContext` interfaces, coroutine creation and suspension top-level
functions"에 그치고, 그 위의 것은 "Most coroutine features are provided by the
`kotlinx.coroutines` library, which includes tools for launching coroutines,
handling concurrency, working with asynchronous streams, and more"라고 설명한다.

아래 2~5번에서 다루는 `launch`, `async`, `withContext`,
`CoroutineExceptionHandler`는 **전부 세 번째 층**이다. `build.gradle.kts`에 다음
한 줄이 있어야 컴파일된다.

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
```

### 바이트코드로 확인하는 경계

`suspend`가 언어 층이라는 것은 컴파일 결과에서 바로 보인다. `javap`로 열어보면
`suspend` 함수에만 파라미터가 하나 더 붙어 있다.

```
public static final java.lang.Object fetchValue(java.lang.String, long, kotlin.coroutines.Continuation<? super java.lang.Integer>);
public static final int blockingFetch(java.lang.String, long);
```

`kotlinx.coroutines.Continuation`이 아니라 **`kotlin.coroutines.Continuation`**,
즉 표준 라이브러리 타입인 점이 핵심이다. 컴파일러가 하는 일은 "함수를 중단 가능한
형태로 바꾸고 `Continuation`을 넘기는 것"까지이고, 그 `Continuation`을 언제 어느
스레드에서 재개할지는 라이브러리의 몫이다.

### 라이브러리 없이 쓰는 코루틴

`kotlinx.coroutines`가 한 줄도 등장하지 않는 코루틴도 있다. 표준 라이브러리의
`sequence`가 그 예다 — 시그니처가
`fun <T> sequence(block: suspend SequenceScope<T>.() -> Unit): Sequence<T>`이고,
`yield`는 suspend 함수다.

```kotlin
fun fibonacci(): Sequence<Int> = sequence {
    var terms = 0 to 1
    while (true) {                 // 무한 루프지만
        yield(terms.first)         // yield에서 중단되므로
        terms = terms.second to (terms.first + terms.second)
    }
}

fibonacci().take(10).toList() // [0, 1, 1, 2, 3, 5, 8, 13, 21, 34] — 요청한 10개만 계산된다
```

그리고 이 경계는 **컴파일러가 실제로 강제한다**. `SequenceScope`는
`@RestrictsSuspension`이 걸린 제한된 스코프라, 여기에 라이브러리 층의 `delay`를
넣으면 컴파일이 실패한다.

```kotlin
sequence {
    yield(1)
    delay(100) // 컴파일 에러
}
// e: Restricted suspending functions can invoke member or extension
//    suspending functions only on their restricted coroutine scope.
```

### 실무에서는 어차피 같이 쓴다

맞다 — 언어와 표준 라이브러리 층만으로는 코루틴을 "띄울" 방법이 사실상 없으므로,
비동기 작업을 한다면 `kotlinx.coroutines`를 항상 같이 쓰게 된다. 그래도 경계를
알아둘 실익은 있다.

- **API 설계**: 공개 함수에 `suspend`만 노출하면 호출자에게 `kotlinx.coroutines`
  의존성을 강요하지 않는다. `Deferred`나 `Flow`를 반환 타입에 쓰는 순간부터
  라이브러리가 API의 일부가 된다.
- **에러 메시지 읽기**: 층에 따라 메시지가 다르다. 언어 층의 규칙 위반은
  `Suspend function 'suspend fun fetchValue(...)' can only be called from a
  coroutine or another suspend function.`처럼 "suspend"를 말하고, 스코프나
  디스패처 이야기가 나오면 라이브러리 층의 문제다.
- **런타임 교체**: Reactor 같은 다른 비동기 런타임과 붙일 때, 갈아끼울 수 있는
  층이 세 번째 층뿐이라는 것이 보인다.

## 2. launch — 결과값 없는 코루틴 실행

Java(`ExecutorService`)에서는 작업을 제출해도 자동으로 기다려주지 않는다.
기다리려면 반환된 `Future`를 직접 `get()` 하거나 executor를 명시적으로
종료(`shutdown()` + `awaitTermination()`)해야 한다.

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
executor.submit(() -> {
    Thread.sleep(100);
    System.out.println("100ms 뒤 실행됨");
});
System.out.println("즉시 출력됨"); // executor.submit()은 완료를 기다려주지 않는다
```

Kotlin의 `launch`는 `CoroutineScope`의 확장 함수로, 결과값이 필요 없는 코루틴을
시작한다. `coroutineScope { }` 블록(부모)은 그 안에서 `launch`된 모든 자식
코루틴이 끝날 때까지 자동으로 대기한다 — 이를 **구조화된 동시성**이라고 한다.

```kotlin
suspend fun launchExample() = coroutineScope {
    launch {
        delay(100.milliseconds) // kotlin.time.Duration.Companion.milliseconds
        println("launch: 100ms 뒤 실행됨")
    }
    println("launch: 즉시 출력됨 (코루틴은 대기 중)")
} // 이 지점에서 launch된 코루틴이 끝날 때까지 자동으로 기다린다
```

**핵심 차이**: Java의 `executor.submit()`은 결과를 기다리는 책임이 호출자에게
있지만, Kotlin의 `launch`는 부모 스코프가 자동으로 자식의 완료를 보장한다.

## 3. async / await — 병렬 실행

Kotlin 코루틴은 **기본적으로 순차 실행**된다는 점이 Java의 일반 메서드 호출과
동일하다. Java에서 두 작업을 병렬로 돌리려면 `CompletableFuture`로 명시적으로
분리해야 한다.

```java
long start = System.currentTimeMillis();
CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> fetchValue("A", 1000));
CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> fetchValue("B", 1000));
int sum = a.get() + b.get(); // 두 Future의 완료를 기다림
System.out.println("Completed in " + (System.currentTimeMillis() - start) + " ms"); // ~1000ms
```

Kotlin에서는 `async`로 감싼 두 작업이 동시에 시작되고, `await()`로 각각의
결과를 받는다.

```kotlin
suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { fetchValue("A", 1000) }
    val two = async { fetchValue("B", 1000) }
    one.await() + two.await()
} // ~1000ms (순차로 하면 ~2000ms)
```

**핵심 차이**: `CompletableFuture.get()`은 호출한 스레드를 블로킹하는 반면,
`await()`는 스레드를 점유하지 않고 코루틴만 suspend된다 — 그 스레드는
대기하는 동안 다른 코루틴 작업에 재사용될 수 있다.

### async 블록에 suspend 함수만 올 수 있는가

`async`의 공식 시그니처는 다음과 같다.

```kotlin
fun <T> CoroutineScope.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T>
```

`block`이 `suspend` 람다인 것이지, **그 안에서 호출하는 함수가 `suspend`여야
한다는 제약은 없다.** suspend 람다 안에서는 일반 함수도 그대로 호출할 수 있다.
Java의 `supplyAsync`가 평범한 `Supplier`를 받는 것과 같다.

Java:

```java
// supplyAsync에 넘기는 것은 그냥 평범한 메서드 참조/람다다
CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> blockingFetch("A", 300));
CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> blockingFetch("B", 300));
int sum = a.get() + b.get(); // ~300ms — commonPool의 서로 다른 스레드에서 돌기 때문
```

Kotlin:

```kotlin
val x = async { blockingFetch("A", 300) } // 평범한 블로킹 함수 — 컴파일된다
val y = async { (1..1_000_000).sum() }    // suspend 호출이 하나도 없어도 된다
```

**핵심 차이**: 없다 — 양쪽 다 "비동기 전용 함수"라는 개념이 없고, 아무 코드나
넣을 수 있다. 대신 Kotlin에서는 **어떤 디스패처 위에서 도느냐**에 따라 결과가
달라진다. Java의 `supplyAsync`는 기본값이 여러 스레드를 가진
`ForkJoinPool.commonPool`이라 블로킹 코드를 넣어도 어쩌다 병렬이 되지만,
코루틴은 컨텍스트를 상속하므로 부모가 단일 스레드면 블로킹 코드가 직렬화된다.

같은 300ms 작업 두 개를 조합을 바꿔가며 측정한 결과다
(`runBlocking` 안에서 실행, 순차라면 600ms).

| 블록 안의 코드 | 디스패처 | 소요 시간 | 이유 |
| --- | --- | --- | --- |
| `Thread.sleep` (블로킹) | `runBlocking` 기본 | 614ms | 이벤트 루프가 호출 스레드 하나에서 도는데, 그 스레드를 붙잡아버림 |
| `Thread.sleep` (블로킹) | `Dispatchers.IO` | 315ms | 워커 스레드가 여러 개라 각각 다른 스레드를 붙잡음 |
| `delay` (suspend) | `runBlocking` 기본 | 309ms | 스레드가 하나여도 `delay`가 스레드를 반납하므로 겹쳐서 진행 |

`runBlocking`의 기본 `ContinuationInterceptor`는 "호출한 스레드에서
continuation을 처리하는 이벤트 루프"다. 그래서 첫 행은 `async`를 두 번 썼는데도
병렬이 되지 않는다. 즉 병렬성을 만드는 것은 `async`라는 키워드가 아니라
**"코루틴이 스레드를 점유하지 않고 양보하는가"** 이고, 블로킹 호출을 넣어야
한다면 `Dispatchers.IO`로 옮겨서 그 성질을 되찾아야 한다.

## 4. withContext — 다른 디스패처로 전환

Java에서 "이 블로킹 작업은 IO 전용 스레드풀에서 실행해달라"는 의도는
`Executor`를 명시적으로 지정하는 것으로 표현한다.

```java
CompletableFuture<String> future =
    CompletableFuture.supplyAsync(this::blockingIoCall, ioExecutor);
String result = future.get();
```

Kotlin의 `withContext`는 코루틴을 일시 중단하고 새 컨텍스트(디스패처)로
전환해 블록을 실행한 뒤, 끝나면 원래 컨텍스트로 복귀한다.

```kotlin
val result = withContext(Dispatchers.IO) {
    blockingIoCall()
}
// withContext 블록이 끝나면 원래 디스패처로 자동 복귀
```

`Dispatchers.Default`는 CPU 연산에 최적화된 공유 스레드풀이고,
`Dispatchers.IO`는 블로킹 I/O 호출에 최적화된 스레드풀이다 — 블로킹 호출을
코루틴 안에서 해야 한다면 `Dispatchers.IO`로 전환해 다른 코루틴의 실행을
막지 않도록 한다.

**핵심 차이**: Java는 `Executor` 인스턴스를 직접 만들어 관리해야 하지만,
Kotlin은 `Dispatchers.IO`/`Dispatchers.Default` 같은 미리 정의된 디스패처를
쓰는 것이 관용구다.

## 5. 예외 처리 — CoroutineExceptionHandler + supervisorScope

Java의 `CompletableFuture`는 애초에 각 Future가 독립적이라, 하나가 실패해도
다른 Future에 영향을 주지 않는다. 실패 처리는 각 Future에 개별적으로 건다.

```java
CompletableFuture.runAsync(() -> { throw new RuntimeException("의도적으로 발생시킨 예외"); })
    .exceptionally(ex -> {
        System.out.println("예외 처리됨: " + ex.getMessage());
        return null;
    });
```

Kotlin 코루틴은 구조화된 동시성 때문에 기본값이 반대다 — 형제 코루틴이 있으면
하나의 예외가 부모까지 전파되어 나머지도 함께 취소된다. `CoroutineExceptionHandler`는
**루트(root) 코루틴**에서만 동작하는데, 일반 `coroutineScope`의 자식으로
`launch(handler)`를 걸면 예외가 handler보다 먼저 부모로 전파되어 무시된다.
`supervisorScope`는 내부의 각 자식 코루틴을 마치 루트 코루틴처럼 취급하므로,
이 안에서는 `launch(handler)`의 handler가 정상적으로 동작한다.

```kotlin
suspend fun exceptionHandlingExample() = supervisorScope {
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("예외 처리됨: ${throwable.message}")
    }
    launch(handler) {
        throw RuntimeException("의도적으로 발생시킨 예외")
    }.join()
    println("supervisorScope는 예외 발생 후에도 계속 진행된다")
}
```

**핵심 차이**: Java의 `Future`/`exceptionally`는 실패 격리가 기본값이지만,
Kotlin 코루틴은 "형제와 함께 취소"가 기본값이라 격리하려면 `supervisorScope`를
명시적으로 선언해야 한다.

## 관련 문서

- [../spring/webflux/coroutines.md](../spring/webflux/coroutines.md) — WebFlux에서 코루틴 사용
- [../spring/mvc/coroutines.md](../spring/mvc/coroutines.md) — MVC에서 코루틴 사용

## 참고 자료

- [Coroutines overview](https://kotlinlang.org/docs/coroutines-overview.html) — 언어/표준 라이브러리와 `kotlinx.coroutines`의 역할 구분
- [`kotlin.coroutines` 패키지](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines/) — 표준 라이브러리가 제공하는 `Continuation`, `CoroutineContext`, `RestrictsSuspension`
- [`sequence`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/sequence.html) / [`SequenceScope`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.sequences/-sequence-scope/) — 라이브러리 없이 동작하는 코루틴
- [Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html) — `launch`, `runBlocking`, 구조화된 동시성
- [Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html) — `async`/`await`, 순차 vs 병렬 실행
- [Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html) — `withContext`, `Dispatchers`
- [`async`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/async.html) — `block: suspend CoroutineScope.() -> T` 시그니처
- [`runBlocking`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/run-blocking.html) — 호출 스레드에서 도는 기본 이벤트 루프
- [Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html) — `CoroutineExceptionHandler`, `supervisorScope`
