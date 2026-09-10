# Level 3 — 코루틴 분산 시스템

**배점 40점** · 대상 패키지 `com.kotlin.workbook.answer.level3`

Level 1·2에서 만든 스토어를 여러 노드로 쪼개고, 그 사이를 코루틴으로 잇는다.
네트워크는 실제 소켓 대신 **지연·장애를 흉내 내는 시뮬레이터**다. 그래서 클러스터 전체가
테스트 하나 안에서 돌고, `runTest`의 가상 시간 덕분에 "1초 지연되는 느린 노드"를 두고도
테스트는 즉시 끝난다.

## 만드는 것

```
                      QuorumCoordinator
                   (async 팬아웃 · W/R 정족수 · 타임아웃 · 지수 백오프)
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              SimulatedNetwork (지연 / UNREACHABLE / BLACK_HOLE)
                    │         │         │
                 StoreNode  StoreNode  StoreNode      각 노드: Mutex로 보호된 상태
                    └─────────┴─────────┘              + 변경 이벤트 Flow
                              ▲
                   HeartbeatFailureDetector (UP / SUSPECT / DOWN)
```

정족수 규칙: 복제본 수 `N`, 쓰기 정족수 `W`, 읽기 정족수 `R`일 때 **`W + R > N`**이면
읽기 집합과 쓰기 집합이 반드시 한 노드 이상 겹치므로, 읽기가 최신 값을 최소 하나는 본다.
값이 여러 개 보이면 version이 가장 큰 것을 고른다(Last-Write-Wins).

## 이 레벨에서 익히는 것

| 코루틴 요소 | 어디서 쓰는가 |
|-----------|-------------|
| `Mutex.withLock` | `StoreNode`의 상태 보호 |
| `coroutineScope` + `async` 팬아웃 | `QuorumCoordinator`의 병렬 요청 |
| `withTimeout` / `withTimeoutOrNull` | 노드별 요청 타임아웃 |
| `delay` + 지수 백오프 | `retryWithBackoff` |
| `CancellationException`의 특별함 | 재시도가 취소를 삼키지 않게 하기 |
| 구조화된 동시성 · `SupervisorJob` | `Cluster.start` |
| `SharedFlow` / `StateFlow` | 변경 스트림, 노드 상태 |
| `runTest` 가상 시간 · `backgroundScope` · `currentTime` | 모든 테스트 |

## 개념 미리보기

### 병렬 팬아웃

Java에서 N개 복제본에 동시에 요청하려면 `CompletableFuture`를 모아 `allOf`로 기다린다.
"W개만 모이면 끝"을 표현하려면 카운터와 콜백을 직접 엮어야 한다.

```java
List<CompletableFuture<Ack>> futures = replicas.stream()
        .map(r -> CompletableFuture.supplyAsync(() -> send(r, msg), executor))
        .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();  // 전부 기다린다
```

Kotlin에서는 `coroutineScope` 안에서 `async`로 띄우고, 필요한 만큼만 기다린다.

```kotlin
coroutineScope {
    val deferreds = replicas.map { replica ->
        async { send(replica, message) }
    }
    // W개가 모이면 남은 것을 기다리지 않고 빠져나온다
}
```

**핵심 차이**: `coroutineScope`는 **블록을 벗어날 때 자식이 모두 끝났음을 보장**한다
(구조화된 동시성). Java에서 `executor.shutdown()`을 잊어 스레드가 남는 실수가,
Kotlin에서는 구조적으로 불가능해진다. 대신 "정족수만 채우고 나머지는 버리고 나간다"를
하려면 남은 자식을 명시적으로 취소해야 한다.

### 공유 상태 보호

Java에서는 `synchronized`나 `ReentrantLock`으로 임계 구역을 잡는다. 락을 기다리는 동안
**스레드가 블로킹**된다.

```java
synchronized (this) {
    Versioned<String> current = data.get(key);
    if (current == null || version > current.version()) data.put(key, ...);
}
```

코루틴에서는 같은 자리에 `Mutex`를 쓴다.

```kotlin
mutex.withLock {
    val current = data[key]
    if (current == null || version > current.version) data[key] = Versioned(value, version)
}
```

**핵심 차이**: `Mutex.lock()`은 **중단 함수**라 스레드를 막지 않고 코루틴만 대기시킨다.
그래서 코루틴 안에서 `synchronized`를 쓰면 안 된다 — 락을 든 채 스레드를 붙잡는다.

### 타임아웃

Java는 대기하는 쪽에서 타임아웃을 건다. 시간이 지나면 `TimeoutException`이 나지만,
**작업 자체는 계속 돈다**(직접 `cancel`해야 한다).

```java
try {
    Ack ack = future.get(100, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    future.cancel(true);        // 잊으면 그대로 남는다
}
```

Kotlin은 블록에 타임아웃을 씌우고, 시간이 지나면 그 안의 코루틴을 **취소한다**.

```kotlin
val ack = withTimeoutOrNull(100) { network.send(self, replica, message) }
// null이면 시간 초과. 안에서 돌던 요청은 이미 취소됐다.
```

**핵심 차이**: 타임아웃이 "기다리기를 포기"가 아니라 "작업을 취소"다. 정리 책임이
호출부에서 사라진다.

### 재시도와 취소

Java에서 재시도는 보통 `catch (Exception e)`로 잡고 `Thread.sleep` 후 다시 시도한다.

```java
for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    try { return call(); }
    catch (Exception e) {
        if (attempt == maxAttempts) throw e;
        Thread.sleep(base * (1L << (attempt - 1)));   // 스레드를 재운다
    }
}
```

Kotlin에서는 `delay`로 코루틴만 재운다. 그리고 **취소는 예외로 전달되므로**,
`catch (e: Throwable)`로 통째로 잡으면 취소 신호를 삼켜 버린다.

```kotlin
repeat(maxAttempts) { i ->
    try {
        return block(i + 1)
    } catch (e: CancellationException) {
        throw e                                  // 취소는 재시도 대상이 아니다
    } catch (e: Exception) {
        if (i == maxAttempts - 1) throw e
        delay(baseDelayMillis shl i)
    }
}
```

**핵심 차이**: 코루틴의 취소는 `CancellationException`으로 전파된다. 예외를 넓게 잡는
재시도 코드는 그것까지 "실패"로 착각해 다시 시도하고, 그 순간 취소가 무력화된다.
이 워크북이 L3-07을 별도 요구사항으로 둔 이유다.

### 시간에 의존하는 테스트

Java에서 "3번 재시도하며 20ms, 40ms 기다린다"를 검증하려면 실제로 60ms를 기다리거나
시계를 추상화해야 한다. 테스트는 느려지고 CI에서 간헐적으로 깨진다.

```java
long start = System.nanoTime();
service.callWithRetry();
assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() >= 60);  // 느리고 불안정
```

`runTest`는 `delay`를 실제로 기다리지 않고 건너뛰면서 가상 시간만 진행시킨다.

```kotlin
@Test
fun `L3-06 백오프만큼 가상 시간이 흐른다`() = runTest {
    retryWithBackoff(maxAttempts = 3, baseDelayMillis = 20) { error("fail") }
    assertEquals(60, currentTime)     // 20 + 40, 실제로는 즉시 끝난다
}
```

**핵심 차이**: 공식 문서 표현대로 "The calls to `delay` are automatically skipped,
preserving the relative execution order of the tasks". 시간 순서는 유지되고 대기만
사라지므로, 지연·타임아웃·백오프를 **정확한 숫자로** 단언할 수 있다.

## 구현 요구사항

### `StoreNode.kt`

- **L3-01** `NodeMessage.Write`는 값을 저장하고 `WriteAck(nodeId, key, version, applied)`를,
  `NodeMessage.Read`는 `ReadResult(nodeId, key, value)`를, `Ping`은 `Pong(nodeId)`를 돌려준다.
  `snapshot()`은 현재 상태의 복사본이다. `handledCount`는 처리한 요청 수다.
- **L3-02** 들어온 version이 기존 version **이하**면 저장하지 않고 `applied = false`.
  (같은 version 재전송은 무시된다 — 재시도가 안전해진다.)
- **L3-03** 실제로 반영된 쓰기만 `changes`로 흘러나온다. 무시된 쓰기는 이벤트가 없다.
  구독자가 없어도 노드는 멈추지 않는다.
- **L3-S1** 모든 상태 접근은 `mutex.withLock { ... }` 안에서 한다.

### `Network.kt`

- **L3-04** 정상 경로는 `편도 지연 → 노드 호출 → 편도 지연`이다. 편도 지연이 10ms면 왕복
  20ms가 가상 시간에 나타난다. 배달될 때마다 `deliveredCount`가 는다.
- **L3-05** `FaultMode.UNREACHABLE`은 지연 없이 즉시 `NodeUnreachableException`.
  `FaultMode.BLACK_HOLE`은 영원히 응답하지 않는다(`awaitCancellation()`).
  모르는 노드로 보내도 `NodeUnreachableException`.

### `Retry.kt`

- **L3-06** `block`을 최대 `maxAttempts`번 호출한다. n번째 실패 후 대기 시간은
  `baseDelayMillis * 2^(n-1)`이고 `maxDelayMillis`로 상한을 건다. 마지막까지 실패하면
  마지막 예외를 던진다. 재시도 직전마다 `onRetry(attempt, cause)`를 호출한다.
- **L3-07** `CancellationException`은 재시도하지 않고 즉시 다시 던진다.

### `Quorum.kt`

- **L3-08** `QuorumConfig`가 `replicationFactor >= 1`, `1 <= W <= N`, `1 <= R <= N`,
  `W + R > N`을 검증한다.
- **L3-09** `write`는 모든 복제본에 `Write`를 보내고 `applied` 여부와 무관하게 도착한 ack를
  센다. `W`개를 모으면 `WriteOutcome.Committed(version, acks)`를 돌려준다.
  `acks`에는 ack를 준 노드가 담긴다.
- **L3-10** 요청은 **병렬**로 나간다. 지연이 각각 30ms인 노드 3개라면 전체 소요 가상 시간은
  90ms가 아니라 60ms(왕복) 근처여야 한다.
- **L3-11** 정족수를 채우면 **남은 응답을 기다리지 않고** 반환한다. 느린 노드 하나가
  1000ms여도 `W`를 이미 채웠다면 그 시간을 기다리지 않는다. 반환 시점에 아직 도는 코루틴이
  남아 있으면 안 된다.
- **L3-12** 노드별 요청은 `requestTimeoutMillis`로 감싸고 실패 시 `retryWithBackoff`로
  `maxAttempts`까지 재시도한다. 시도 횟수는 `attemptsPerNode`에 기록한다.
  끝내 `W`를 못 채우면 `WriteOutcome.Rejected(모은 ack 수, W)`.
- **L3-13** `read`는 `R`개 응답을 모아 version이 가장 큰 값을 `ReadOutcome.Found`로 돌려준다.
  `from`에는 응답한 노드가 담긴다.
- **L3-14** 모인 응답의 값이 전부 null이면 `ReadOutcome.NotFound`.
  `R`개를 못 모으면 `ReadOutcome.Rejected`.
- **L3-S2** 팬아웃은 `async { ... }`로 한다(순차 호출 금지).

### `FailureDetector.kt`

- **L3-15** `intervalMillis`마다 모든 peer에 `Ping`을 **동시에** 보낸다. `timeoutMillis`
  안에 `Pong`이 오면 연속 실패 수를 0으로 되돌리고 `UP`.
- **L3-16** 연속 실패가 1 이상 `failureThreshold` 미만이면 `SUSPECT`,
  `failureThreshold` 이상이면 `DOWN`. 복구되면 다시 `UP`.
- **L3-17** `start(scope)`가 돌려준 `Job`은 스코프가 취소되면 함께 끝난다.
  감지 루프가 스코프 밖에서 계속 돌면 안 된다.

### `Cluster.kt`

- **L3-18** `Cluster.of(size, config, latencyMillis)`가 `node-0` … `node-{size-1}` 노드와
  네트워크·코디네이터·감지기를 조립한다. `coordinator`와 `detector`는 이 클러스터의
  노드/네트워크를 가리켜야 한다.
- **L3-19** `start(scope)`는 백그라운드 작업을 `SupervisorJob` 아래에서 띄운다.
  자식 하나가 예외로 죽어도 형제 작업과 클러스터가 살아남는다.

## 작성해야 할 테스트

`src/test/kotlin/com/kotlin/workbook/answer/level3/Level3Test.kt`, 이름은 `L3-XX`로 시작.
**모든 테스트는 `runTest` 위에서 돌고 `runBlocking`을 쓰지 않는다.**

| ID | 테스트가 증명해야 하는 것 | 힌트 |
|----|------------------------|------|
| L3-01 | 쓰고 읽으면 같은 값·version, `snapshot()` 일치 | |
| L3-02 | version 3 저장 후 version 2를 쓰면 `applied=false`, 값이 그대로 | |
| L3-03 | 반영 2회 + 무시 1회일 때 이벤트가 2개 | `backgroundScope`에서 수집하거나 Turbine `test { }` |
| L3-04 | 왕복 지연이 `currentTime`에 그대로 | `val t0 = currentTime` 후 차이를 잰다 |
| L3-05 | UNREACHABLE은 즉시 예외(`currentTime` 변화 없음), BLACK_HOLE은 `withTimeoutOrNull`이 null | |
| L3-06 | 3번 시도하고 `currentTime == base + 2*base` | `onRetry`로 호출 횟수도 센다 |
| L3-07 | `withTimeout` 안에서 재시도 중 취소되면 즉시 빠져나오고 재시도 횟수가 늘지 않는다 | 취소를 삼키면 이 테스트가 잡는다 |
| L3-08 | N=3, W=1, R=1 조합이 거부된다(1+1 > 3 아님) | |
| L3-09 | N=3, W=2에서 `Committed`, `acks.size >= 2` | |
| L3-10 | 지연 30ms 노드 3개에서 총 시간이 왕복 1회분 근처 | 순차 구현이면 3배가 나온다 |
| L3-11 | 한 노드만 1000ms로 느리게 두고 W=2일 때 `currentTime < 1000` | 반환 후 `advanceUntilIdle()`로 남은 코루틴이 없는지도 본다 |
| L3-12 | 노드 2개를 UNREACHABLE로 만들고 W=2면 `Rejected`, `attemptsPerNode`에 재시도 흔적 | |
| L3-13 | 노드마다 다른 version을 심어두고 최신이 선택되는지 | 노드에 직접 `handle(Write(...))`로 심는다 |
| L3-14 | 없는 키 읽기 → `NotFound` | |
| L3-15 | `advanceTimeBy(interval * 3)` 후에도 전부 `UP` | `backgroundScope`에 띄운다 |
| L3-16 | 노드를 죽이고 시간을 밀면 `SUSPECT` → `DOWN`, 살리면 `UP` | 임계값 직전/직후를 나눠 단언한다 |
| L3-17 | `job.cancelAndJoin()` 후 `job.isActive == false`, 하트비트가 더 나가지 않는다 | `deliveredCount`가 멈추는지로 확인 |
| L3-18 | `Cluster.of(3, config)`의 노드 수·이름 | |
| L3-19 | 백그라운드 자식 하나가 예외로 죽어도 다른 자식이 계속 돈다 | `SupervisorJob` 없이 짜면 실패한다 |

## 체크리스트

`./gradlew grade -Plevel=3`이 40/40이면 통과다.

- [ ] 코루틴 코드 어디에도 `synchronized`, `Thread.sleep`, `.get()` 블로킹 호출이 없다
- [ ] 재시도 코드가 `CancellationException`을 다시 던진다
- [ ] `write`가 반환된 뒤 남아 있는 코루틴이 없다(`advanceUntilIdle()`로 확인)
- [ ] 장애 감지기가 `backgroundScope`에서 돌고 테스트가 끝날 때 저절로 정리된다
- [ ] 테스트에 `runBlocking`과 실제 대기가 없다
- [ ] 팬아웃이 병렬임을 `currentTime`으로 증명했다
- [ ] `W + R > N`을 깨는 설정이 생성 단계에서 막힌다

## 보너스 (채점 대상 아님)

여력이 있다면 실제 분산 스토어가 하는 일을 더 붙여 본다.

1. **읽기 복구(read repair)** — 읽기에서 뒤처진 노드를 발견하면 최신 값을 다시 써 준다.
   반환을 지연시키지 않도록 별도 스코프에서 처리하되, 클러스터 스코프에 붙여 누수를 막는다.
2. **힌티드 핸드오프** — 죽은 노드로 가야 할 쓰기를 `Channel`에 모아 두었다가 노드가 `UP`이
   되면 흘려보낸다.
3. **버전 벡터** — 단일 `Long` version 대신 노드별 카운터를 두고 충돌을 감지한다.
4. **`select`로 정족수 대기** — `onAwait` 절로 "먼저 도착한 W개"를 표현해 본다
   (`select`는 실험적 API다).

## 관련 문서

- [level2.md](./level2.md)
- [../coroutine/basics.md](../coroutine/basics.md)

## 참고 자료

- [Coroutines basics](https://kotlinlang.org/docs/coroutines-basics.html) — 구조화된 동시성, `coroutineScope`
- [Composing suspending functions](https://kotlinlang.org/docs/composing-suspending-functions.html) — `async`/`await` 병렬 실행
- [Cancellation and timeouts](https://kotlinlang.org/docs/cancellation-and-timeouts.html) — `withTimeout`/`withTimeoutOrNull`, 취소의 협조적 성격
- [Coroutine exceptions handling](https://kotlinlang.org/docs/exception-handling.html) — `SupervisorJob`, `supervisorScope`
- [Shared mutable state and concurrency](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html) — `Mutex.withLock`은 중단하며 스레드를 막지 않는다
- [Channels](https://kotlinlang.org/docs/channels.html) — `send`/`receive`, 버퍼, 팬아웃/팬인
- [Flows](https://kotlinlang.org/docs/coroutines-flow.html) — 콜드 플로우와 핫 플로우(`SharedFlow`/`StateFlow`), 취소, `catch`
- [Select expression](https://kotlinlang.org/docs/select-expression.html) — `onAwait`/`onReceive`, 실험적 API 안내
- [kotlinx-coroutines-test](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) — `runTest`, `TestCoroutineScheduler`, `currentTime`, `advanceTimeBy`, `advanceUntilIdle`, `backgroundScope`
