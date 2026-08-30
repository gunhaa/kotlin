package com.kotlin.coroutine

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

fun main() = runBlocking {
    launchExample()
    asyncAwaitExample()
    withContextExample()
    exceptionHandlingExample()
}

/**
 * launch: 결과값이 필요 없는 코루틴을 실행한다 (fire-and-forget).
 * runBlocking의 자식으로 실행되므로, 부모가 끝나기 전에 자식이 끝나길 기다린다 (구조화된 동시성).
 *
 * Java 비교:
 * - java.util.concurrent: `executorService.submit(() -> { ... })`
 *   단, 이건 진짜 fire-and-forget이라 호출부가 끝날 때 자동으로 기다려주지 않는다.
 *   기다리게 하려면 반환된 Future를 별도로 `.get()` 해야 한다.
 * - Spring WebFlux: `Mono.fromRunnable(() -> { ... }).subscribe()`
 *   subscribe()도 non-blocking이라 별도로 block() 하지 않으면 기다리지 않는다.
 * - 차이점: launch는 구조화된 동시성 덕분에 "부모가 자동으로 기다린다"는 게 보장된다.
 *   Java 진영에서는 이 보장을 받으려면 Future.get()/CompletableFuture.join()을 명시적으로
 *   호출하거나, Java 21의 StructuredTaskScope를 써야 한다.
 */
suspend fun launchExample() = coroutineScope {
    println("== launch 예제 ==")
    launch {
        delay(100)
        println("launch: 100ms 뒤 실행됨")
    }
    println("launch: 즉시 출력됨 (코루틴은 대기 중)")
}

/**
 * async/await: 결과값이 필요한 여러 작업을 동시에 실행하고 결과를 모은다.
 * 순차 실행이 아니라 병렬로 실행되므로 총 소요 시간은 가장 긴 작업 하나만큼만 걸린다.
 *
 * Java 비교:
 * - java.util.concurrent:
 *     CompletableFuture<Integer> a = CompletableFuture.supplyAsync(() -> fetchValue("A", 100));
 *     CompletableFuture<Integer> b = CompletableFuture.supplyAsync(() -> fetchValue("B", 150));
 *     int sum = a.get() + b.get(); // 또는 a.thenCombine(b, Integer::sum)
 * - Spring WebFlux: `Mono.zip(fetchMonoA(), fetchMonoB()).map(t -> t.getT1() + t.getT2())`
 * - 결정적 차이: `CompletableFuture.get()`은 호출한 스레드를 블로킹하지만,
 *   코루틴의 `await()`는 스레드를 점유하지 않고 코루틴만 suspend된다
 *   (그 스레드는 대기하는 동안 다른 코루틴 작업에 재사용될 수 있다).
 */
suspend fun asyncAwaitExample() = coroutineScope {
    println("\n== async/await 예제 ==")
    val elapsed = measureTimeMillis {
        val first = async { fetchValue("A", 100) }
        val second = async { fetchValue("B", 150) }
        println("합계: ${first.await() + second.await()}")
    }
    println("소요 시간: ${elapsed}ms (순차 실행이면 250ms 이상 걸림)")
}

suspend fun fetchValue(name: String, delayMs: Long): Int {
    delay(delayMs)
    println("$name 조회 완료")
    return delayMs.toInt()
}

/**
 * withContext: 코루틴 실행 중 다른 디스패처로 전환할 때 사용한다.
 * CPU 연산은 Dispatchers.Default, 블로킹 I/O는 Dispatchers.IO로 전환하는 식으로 활용한다.
 *
 * Java 비교:
 * - java.util.concurrent: `CompletableFuture.supplyAsync(this::blockingIoCall, ioExecutor).get()`
 *   "이 블로킹 호출은 IO 전용 스레드풀에서 실행해줘"라는 의도를
 *   supplyAsync의 두 번째 인자(Executor)로 표현하는 것과 같다.
 * - Spring WebFlux: `Mono.fromCallable(this::blockingIoCall).subscribeOn(Schedulers.boundedElastic())`
 * - Spring MVC(순수 블로킹 스택) 개발자에게: MVC 컨트롤러 메서드 안에서는 원래 서블릿
 *   스레드 자체가 블로킹을 전제로 하므로 그냥 블로킹 호출을 하면 된다. withContext(Dispatchers.IO)가
 *   필요해지는 지점은 "코루틴(WebFlux suspend 핸들러, 혹은 MVC의 suspend 핸들러) 안에서"
 *   블로킹 호출을 해야 할 때, 코루틴을 실행 중이던 메인/이벤트루프 스레드를 막지 않기 위해서다.
 */
suspend fun withContextExample() {
    println("\n== withContext 예제 ==")
    val result = withContext(Dispatchers.IO) {
        println("IO 디스패처에서 실행 중: ${Thread.currentThread().name}")
        blockingIoCall()
    }
    println("결과: $result (메인 스레드로 복귀: ${Thread.currentThread().name})")
}

fun blockingIoCall(): String {
    Thread.sleep(50) // 블로킹 I/O를 흉내낸 코드
    return "IO 결과"
}

/**
 * 코루틴에서 발생한 예외는 기본적으로 부모까지 전파되어 형제 코루틴도 함께 취소시킨다.
 * CoroutineExceptionHandler는 "루트" 코루틴에서만 동작하므로, coroutineScope의 자식으로
 * launch(handler)를 걸어도 예외가 부모로 먼저 전파되어 무시된다.
 * supervisorScope로 자식 실패를 서로 격리해야 handler가 정상적으로 동작한다.
 *
 * Java 비교:
 * - java.util.concurrent:
 *     CompletableFuture.runAsync(() -> { throw new RuntimeException("..."); })
 *         .exceptionally(ex -> { System.out.println("예외 처리됨: " + ex.getMessage()); return null; });
 *   Future 하나하나가 원래 독립적이라, 하나가 실패해도 다른 Future에 영향을 주지 않는다
 *   (Java에서는 "격리"가 기본값이다).
 * - Spring WebFlux: `Mono.error(new RuntimeException("...")).onErrorResume(ex -> Mono.empty())`
 * - 핵심 차이: 코루틴은 구조화된 동시성 때문에 기본값이 반대다 — 형제가 있으면 예외가
 *   전파되어 다 같이 취소되는 게 기본이고, Future/Mono처럼 "서로 독립적으로" 만들고 싶으면
 *   supervisorScope로 명시적으로 선언해야 한다.
 */
suspend fun exceptionHandlingExample() = supervisorScope {
    println("\n== 예외 처리 예제 ==")
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("예외 처리됨: ${throwable.message}")
    }
    launch(handler) {
        throw RuntimeException("의도적으로 발생시킨 예외")
    }.join()
    println("supervisorScope는 예외 발생 후에도 계속 진행된다")
}
