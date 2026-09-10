# Level 1 — 단일 노드 KV 스토어 (Kotlin 기본기)

**배점 30점** · 대상 패키지 `com.kotlin.workbook.answer.level1`

TTL이 있는 인메모리 키-값 스토어와, 그 스토어를 조작하는 텍스트 명령 파서를 만든다.
동시성은 아직 없다. 여기서 만드는 `InMemoryStore`가 Level 3에서 클러스터의 각 노드가 된다.

## 이 레벨에서 익히는 것

| 문법·API | 어디서 쓰는가 |
|---------|-------------|
| `data class` · 기본 인자 · 명명 인자 | `StoredValue`, `Command` |
| `sealed interface` + 완전한 `when` | `Command`, `CommandResult` |
| null 안전(`?.`, `?:`, `!!` 금지) | 만료 시각, 조회 결과 |
| `Result` / `runCatching` | 명령 파싱 실패 |
| `require` / `check` | 생성자 불변식 |
| 컬렉션 확장 함수(`groupBy`, `sumOf`, `maxWithOrNull` …) | `toStats` |
| 확장 함수 | `Map<String, StoredValue>.toStats()` |
| `fun interface`(SAM 변환) | `Clock` |

## 개념 미리보기

### 값 객체

Java에서는 equals/hashCode/toString/복사 생성자를 손으로 쓰거나 record를 쓴다.
record는 간결하지만 기본값이 없어 오버로드 생성자를 따로 만들어야 한다.

```java
public record StoredValue(String value, long version, Long expiresAtMillis) {
    public StoredValue(String value, long version) {   // 만료 없는 경우를 위한 오버로드
        this(value, version, null);
    }
}
```

Kotlin은 기본 인자로 오버로드를 대신하고, `copy`까지 함께 얻는다.

```kotlin
data class StoredValue(
    val value: String,
    val version: Long,
    val expiresAtMillis: Long? = null,
)

val renewed = stored.copy(expiresAtMillis = now + 1000)
```

**핵심 차이**: Java는 "생성자 조합"을 오버로드로 표현해야 하지만, Kotlin은 기본 인자와
명명 인자로 표현한다. 인자가 늘어날수록 조합 폭발이 사라진다.

### 닫힌 타입 분기

Java에서 명령 종류를 표현하려면 인터페이스 + `instanceof` 사슬을 쓰고, 새 명령을 추가해도
컴파일러는 아무 말도 하지 않는다(Java 21의 sealed + 패턴 매칭이 이를 개선했지만,
`default`를 쓰는 순간 다시 조용해진다).

```java
if (command instanceof Put put) { ... }
else if (command instanceof Get get) { ... }
else { throw new IllegalStateException("unknown command"); }
```

Kotlin의 `sealed` 계층은 `when`을 식(expression)으로 쓸 때 완전성을 강제한다.

```kotlin
val result: CommandResult = when (command) {
    is Command.Put -> ...
    is Command.Get -> ...
    is Command.Delete -> ...
    Command.Keys -> ...
    // else 가지가 없다. 새 Command를 추가하면 여기가 컴파일 에러가 된다.
}
```

**핵심 차이**: "빠뜨린 경우"를 런타임 예외가 아니라 컴파일 에러로 만든다.
그래서 이 워크북은 `else ->`를 금지한다 — 쓰는 순간 이 안전망이 꺼진다.

### 실패를 값으로

Java는 예외를 던지거나 `Optional`을 돌려주지만, `Optional`에는 실패 이유가 없다.

```java
public Command parse(String line) {
    if (line.isBlank()) throw new IllegalArgumentException("empty command");
    ...
}
```

Kotlin은 성공/실패를 한 타입에 담는 `Result`를 표준으로 제공한다.

```kotlin
fun parse(line: String): Result<Command> = runCatching {
    require(line.isNotBlank()) { "empty command" }
    ...
}
```

**핵심 차이**: 호출부가 `try/catch` 없이 `getOrNull()`, `fold`, `map`으로 실패를 다룰 수
있고, "이 함수는 실패할 수 있다"가 타입에 드러난다.

### 시간 주입

Java에서도 테스트 가능성을 위해 `java.time.Clock`을 주입한다.

```java
class InMemoryStore {
    private final Clock clock;
    InMemoryStore(Clock clock) { this.clock = clock; }
}
// 테스트: new InMemoryStore(Clock.fixed(instant, ZoneOffset.UTC))
```

Kotlin에서는 추상 메서드가 하나뿐인 인터페이스를 `fun interface`로 선언해 람다로 바로
구현할 수 있다.

```kotlin
fun interface Clock {
    fun nowMillis(): Long
}

val fixed = Clock { 1_000L }              // SAM 변환
```

**핵심 차이**: 시간 주입이라는 설계는 같지만, 가짜 구현을 만드는 비용이 람다 한 줄로
줄어든다. 테스트에서 `Thread.sleep`을 쓸 이유가 사라진다.

## 구현 요구사항

### `StoredValue.kt`

- **L1-01** 생성자에서 `version >= 1`을 검증한다. 위반이면 `IllegalArgumentException`.
- **L1-02** `isExpired(nowMillis)` — `expiresAtMillis`가 null이면 항상 false.
  만료 시각과 **정확히 같은 순간은 아직 살아 있다**(`now > expiresAt`일 때만 만료).
- **L1-03** `remainingTtlMillis(nowMillis)` — 만료 시각이 없으면 null, 이미 만료됐으면 0,
  아니면 남은 밀리초.

### `CommandParser.kt`

- **L1-04** 다음 문법을 파싱한다. 명령어는 대소문자를 구분하지 않고, 토큰 사이 공백은
  여러 칸이어도 된다.

  | 입력 | 결과 |
  |------|------|
  | `PUT user:1 alice` | `Command.Put("user:1", "alice", null)` |
  | `put user:1 alice 5000` | `Command.Put("user:1", "alice", 5000)` |
  | `GET user:1` | `Command.Get("user:1")` |
  | `DEL user:1` | `Command.Delete("user:1")` |
  | `KEYS` | `Command.Keys` |

- **L1-05** 잘못된 입력은 **예외를 던지지 않고** `Result.failure(IllegalArgumentException)`으로
  돌려준다. 빈 문자열, 모르는 명령어, 인자 개수 부족/초과, TTL이 숫자가 아니거나 0 이하인 경우가
  모두 여기에 해당한다.

### `InMemoryStore.kt`

- **L1-06** `put`은 새 version을 돌려준다. 같은 키에 처음 쓰면 1, 그다음은 2, 3 … 으로
  증가한다. 키가 다르면 서로 영향을 주지 않는다. `size`는 만료되지 않은 항목 수다.
- **L1-07** TTL이 지난 항목은 `get`에서 null이고, **그 조회 시점에 내부 맵에서도 제거된다**
  (lazy expiration). `ttlMillis`가 0 이하면 `IllegalArgumentException`.
- **L1-08** `delete`는 실제로 지웠으면 true, 원래 없었거나 이미 만료된 키면 false.
- **L1-09** `keys()`는 만료되지 않은 키를 **사전순**으로 돌려준다. `snapshot()`은 만료되지
  않은 항목 전체를 담은 읽기 전용 맵이다.
- **L1-10** `execute(command)` 매핑:

  | 명령 | 결과 |
  |------|------|
  | `Put` | `CommandResult.Value(저장한 값, 새 version)` |
  | `Get` (존재) | `CommandResult.Value(값, version)` |
  | `Get` (없음/만료) | `CommandResult.Empty` |
  | `Delete` (성공) | `CommandResult.Value(지워진 값, 그 version)` |
  | `Delete` (실패) | `CommandResult.Empty` |
  | `Keys` | `CommandResult.KeyList(키 목록)` |
  | 인자 오류로 예외 발생 | `CommandResult.Failure(예외 메시지)` |

  `when`에 `else ->`를 쓰지 않는다.

### `StoreStats.kt`

- **L1-11** `Map<String, StoredValue>.toStats()`는 `totalKeys`, `totalValueLength`,
  `keysByNamespace`를 계산한다. 네임스페이스는 첫 `:` 앞부분이고, `:`가 없으면 `"_"`다.
  각 네임스페이스의 키 목록은 사전순.
- **L1-12** `hottestKey`는 version이 가장 큰 키다. 동률이면 사전순으로 앞선 키,
  비어 있으면 null.
- **L1-S2** 위 두 항목을 `for` 루프 없이 컬렉션 연산으로 구현한다.

## 작성해야 할 테스트

`src/test/kotlin/com/kotlin/workbook/answer/level1/Level1Test.kt`에 쓴다.
이름은 `L1-XX`로 시작한다.

| ID | 테스트가 증명해야 하는 것 | 힌트 |
|----|------------------------|------|
| L1-01 | version 0과 음수로 `StoredValue`를 만들 수 없다 | `assertFailsWith<IllegalArgumentException>` |
| L1-02 | 만료 전 / 만료 시각 정각 / 만료 후 세 지점 모두 검증 | 경계값을 반드시 포함한다 |
| L1-03 | 무기한이면 null, 만료 후면 0, 그 사이면 남은 시간 | |
| L1-04 | 네 가지 명령의 정상 파싱 + 소문자 입력 + TTL 유무 | `getOrThrow()`로 꺼내 비교 |
| L1-05 | 잘못된 입력 4종 이상이 `isFailure`이고 예외가 `IllegalArgumentException` | 함수 호출이 **예외를 던지지 않는지**도 확인 |
| L1-06 | 같은 키에 3번 쓰면 1,2,3 / 다른 키는 각자 1부터 / `size` 반영 | |
| L1-07 | 가짜 시계를 TTL 직전·정각·직후로 밀며 `get` 결과 확인. 만료된 뒤 `size`도 줄어야 한다 | `FakeClock.advance(...)` — `Thread.sleep` 금지 |
| L1-08 | 있는 키 → true, 없는 키 → false, 만료된 키 → false | |
| L1-09 | 무작위 순서로 넣어도 사전순으로 나오고, 만료 키는 빠진다 | |
| L1-10 | 여섯 가지 매핑을 모두 검증 | `Failure` 경로도 하나 만든다 |
| L1-11 | 네임스페이스가 섞인 데이터로 집계 3종 검증 | `:` 없는 키를 반드시 섞는다 |
| L1-12 | version 동률 상황에서 사전순 우선, 빈 맵이면 null | |

## 체크리스트

구현을 끝냈다면 아래를 스스로 확인한다. `./gradlew grade -Plevel=1`이 30/30이면 통과다.

- [ ] `!!` 연산자를 한 번도 쓰지 않았다
- [ ] `when`에 `else ->`가 없다
- [ ] `InMemoryStore`가 `System.currentTimeMillis()`를 직접 부르지 않는다
- [ ] 테스트에 `Thread.sleep`이 없다
- [ ] `toStats`에 `for` 루프가 없다
- [ ] 파싱 실패가 예외가 아니라 `Result.failure`로 나온다
- [ ] 경계값(만료 시각 정각, 빈 맵, 동률)을 테스트가 다룬다

## 관련 문서

- [README.md](./README.md)
- [level2.md](./level2.md)
- [../stdlib/conventions.md](../stdlib/conventions.md)
- [../stdlib/collections.md](../stdlib/collections.md)

## 참고 자료

- [Data classes](https://kotlinlang.org/docs/data-classes.html) — `copy`, `equals`/`hashCode` 자동 생성
- [Sealed classes and interfaces](https://kotlinlang.org/docs/sealed-classes.html) — `when`의 완전성 검사
- [Null safety](https://kotlinlang.org/docs/null-safety.html) — `?.`, `?:`, `!!`의 의미
- [`Result`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/) / [`runCatching`](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html)
- [Functions: default and named arguments](https://kotlinlang.org/docs/functions.html#default-arguments)
- [Fun interfaces (SAM conversions)](https://kotlinlang.org/docs/fun-interfaces.html)
- [Collection aggregate operations](https://kotlinlang.org/docs/collection-aggregate.html) / [Grouping](https://kotlinlang.org/docs/collection-grouping.html)
