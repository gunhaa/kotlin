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
import kotlin.time.Duration.Companion.milliseconds

fun main() = runBlocking {
    languageLevelCoroutineExample()
    launchExample()
    asyncAwaitExample()
    asyncBlockingExample()
    withContextExample()
    exceptionHandlingExample()
}

/**
 * kotlinx.coroutines를 전혀 쓰지 않는 코루틴.
 *
 * 표준 라이브러리의 sequence는 `block: suspend SequenceScope<T>.() -> Unit`을 받고,
 * yield는 suspend 함수다. 즉 "중단·재개"라는 언어 기능만으로 동작하며
 * launch/async/delay/Dispatchers 같은 라이브러리 요소는 하나도 등장하지 않는다.
 *
 * SequenceScope는 @RestrictsSuspension으로 제한된 스코프라, 이 블록 안에 delay를 넣으면
 * "Restricted suspending functions can invoke member or extension suspending functions
 * only on their restricted coroutine scope."로 컴파일이 실패한다 — 컴파일러가 언어 층과
 * 라이브러리 층의 경계를 실제로 강제한다.
 *
 * Java 비교:
 * - Java에는 대응하는 언어 기능이 없다. 지연 계산 무한 수열은 Stream.iterate 같은
 *   라이브러리 API로 표현한다:
 *     Stream.iterate(new int[]{0, 1}, t -> new int[]{t[1], t[0] + t[1]})
 *         .limit(10).map(t -> t[0]).toList();
 */
fun languageLevelCoroutineExample() {
    println("== 언어/표준 라이브러리만으로 만드는 코루틴 예제 ==")
    println("피보나치 10개: ${fibonacci().take(10).toList()}")
    println("(kotlinx.coroutines 없이 동작 — yield에서 코루틴이 중단된다)")
}

fun fibonacci(): Sequence<Int> = sequence {
    var terms = 0 to 1
    while (true) { // 무한 루프지만, 요청받은 개수만큼만 계산된다
        yield(terms.first)
        terms = terms.second to (terms.first + terms.second)
    }
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
        delay(100.milliseconds)
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
 * async의 시그니처는 `block: suspend CoroutineScope.() -> T`다.
 * 블록 자체가 suspend 람다일 뿐, 그 안에서 호출하는 함수가 suspend여야 한다는 제약은 없다
 * (평범한 블로킹 함수도, suspend 호출이 하나도 없는 순수 계산도 들어갈 수 있다).
 *
 * 대신 실제로 병렬이 되는지는 "디스패처 위에서 스레드를 양보하는가"로 갈린다.
 * runBlocking의 기본 ContinuationInterceptor는 호출 스레드에서 도는 이벤트 루프라
 * 스레드가 하나뿐이므로, 블로킹 호출을 넣으면 async를 두 번 써도 직렬화된다.
 *
 * Java 비교:
 * - CompletableFuture.supplyAsync(() -> blockingFetch("A", 300))
 *   Java도 넘기는 것은 평범한 Supplier다. 다만 기본 실행자가 여러 스레드를 가진
 *   ForkJoinPool.commonPool이라, 블로킹 코드를 넣어도 어쩌다 병렬이 된다.
 * - 코루틴은 부모의 컨텍스트를 상속하므로 이 "어쩌다 병렬"이 성립하지 않는다.
 */
suspend fun asyncBlockingExample() = coroutineScope {
    println("\n== async 블록 안의 코드와 디스패처 예제 ==")

    val blockingOnEventLoop = measureTimeMillis {
        val a = async { blockingFetch("A", 300) }
        val b = async { blockingFetch("B", 300) }
        a.await() + b.await()
    }
    println("블로킹 함수 + runBlocking 기본 디스패처: ${blockingOnEventLoop}ms (직렬)")

    val blockingOnIo = measureTimeMillis {
        val a = async(Dispatchers.IO) { blockingFetch("C", 300) }
        val b = async(Dispatchers.IO) { blockingFetch("D", 300) }
        a.await() + b.await()
    }
    println("블로킹 함수 + Dispatchers.IO: ${blockingOnIo}ms (병렬)")

    val suspendOnEventLoop = measureTimeMillis {
        val a = async { fetchValue("E", 300) }
        val b = async { fetchValue("F", 300) }
        a.await() + b.await()
    }
    println("suspend 함수 + runBlocking 기본 디스패처: ${suspendOnEventLoop}ms (병렬)")

    // suspend 호출이 하나도 없는 순수 계산도 async 블록에 그대로 넣을 수 있다
    val pureComputation = async { (1..1_000_000).sum() }
    println("suspend 호출 없는 계산도 가능: ${pureComputation.await()}")
}

fun blockingFetch(name: String, sleepMs: Long): Int {
    Thread.sleep(sleepMs) // 스레드를 붙잡는 블로킹 호출
    println("$name 조회 완료 (thread=${Thread.currentThread().name})")
    return sleepMs.toInt()
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
