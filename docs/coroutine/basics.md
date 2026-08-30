# Kotlin Coroutine 기초

Spring과 무관한, 순수 Kotlin 언어/라이브러리 차원의 코루틴 개념만 다룬다.

실행 가능한 예제: `src/main/kotlin/com/kotlin/coroutine/Main.kt`
(`./gradlew run`으로 실행하면 아래 4개 예제가 순서대로 출력된다.)

## 1. launch — 결과값 없는 코루틴 실행

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
        delay(100)
        println("launch: 100ms 뒤 실행됨")
    }
    println("launch: 즉시 출력됨 (코루틴은 대기 중)")
} // 이 지점에서 launch된 코루틴이 끝날 때까지 자동으로 기다린다
```

**핵심 차이**: Java의 `executor.submit()`은 결과를 기다리는 책임이 호출자에게
있지만, Kotlin의 `launch`는 부모 스코프가 자동으로 자식의 완료를 보장한다.

## 2. async / await — 병렬 실행

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

## 3. withContext — 다른 디스패처로 전환

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

## 4. 예외 처리 — CoroutineExceptionHandler + supervisorScope

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

- [Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html) — `launch`, `runBlocking`, 구조화된 동시성
- [Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html) — `async`/`await`, 순차 vs 병렬 실행
- [Coroutine context and dispatchers](https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html) — `withContext`, `Dispatchers`
- [Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html) — `CoroutineExceptionHandler`, `supervisorScope`
