# Level 2 — 타입과 질의 계층 (Kotlin 심화)

**배점 30점** · 대상 패키지 `com.kotlin.workbook.answer.level2`

Level 1의 `InMemoryStore`는 모든 것이 `String`이었다. 여기서는 그 위에 **타입 안전한
식별자**, **버전이 붙은 값**, **연산자와 DSL로 쓰는 질의 계층**을 얹는다.
여기서 만드는 `Key` · `NodeId` · `Versioned`가 Level 3에서 노드 사이를 오간다.

## 이 레벨에서 익히는 것

| 문법·API | 어디서 쓰는가 |
|---------|-------------|
| 선언 지점 변성 `out` / `in` | `Versioned<out T>`, `Sink<in T>` |
| `inline` + `reified` | `CodecRegistry.encode/decode` |
| `@JvmInline value class` | `NodeId`, `Key` |
| 연산자 오버로딩 (`get`/`set`/`contains`/`plusAssign`/`compareTo`/`not`) | `TypedStore`, `KeyPredicate` |
| 중위 함수(`infix`) | `predicate and other` |
| 수신 객체 지정 람다 + `@DslMarker` | `query { ... }` |
| 위임 프로퍼티 (`by lazy`, `by map`, `Delegates.observable`, 직접 만든 위임) | `StoreConfig` |
| `Sequence` 지연 평가 | `TypedStore.scan` |
| 재귀적 sealed 계층 해석기 | `KeyPredicate.matches` |

## 개념 미리보기

### 변성 — 어디에 쓰느냐 vs 어떻게 선언하느냐

Java에서 "이 리스트에서 꺼내기만 한다"는 사실은 **사용하는 쪽**에서 와일드카드로 말한다.
같은 말을 매번 반복해야 한다.

```java
static double sum(List<? extends Number> numbers) { ... }   // 호출부마다 ? extends
List<? extends Number> ints = new ArrayList<Integer>();
```

Kotlin은 **선언하는 쪽**에서 한 번 말한다. `T`가 나오기만 하는 타입이면 `out`이다.

```kotlin
class Versioned<out T>(val value: T, val version: Long)

val stringValue: Versioned<String> = Versioned("alice", 1)
val anyValue: Versioned<Any> = stringValue      // 공변이므로 그대로 대입된다
```

**핵심 차이**: Java의 와일드카드가 "사용 지점 변성"이라면 Kotlin은 "선언 지점 변성"이다.
`out`으로 선언하면 그 타입 파라미터를 in 위치(파라미터 타입)에 쓸 수 없도록 컴파일러가
막아 주므로, 안전성은 선언 한 곳에서 보장된다.

### 타입 토큰 vs reified

Java 제네릭은 런타임에 지워지므로, 타입을 알아야 하는 API는 `Class<T>`를 인자로 받는다.

```java
<T> T decode(String raw, Class<T> type) { ... }
Integer n = decode("42", Integer.class);       // 타입을 두 번 말한다
```

Kotlin은 `inline` 함수의 타입 파라미터를 `reified`로 선언해 본문에서 실제 타입을 쓴다.

```kotlin
inline fun <reified T : Any> decode(raw: String): T = codecFor(T::class).decode(raw)

val n: Int = decode("42")                      // 타입을 한 번만 말한다
```

**핵심 차이**: 함수가 호출부에 인라인되므로 `T`의 실제 타입이 남는다. 대신 제약이 붙는다 —
`reified`는 `inline` 함수에서만 쓸 수 있고, **public inline 함수는 private/internal 선언에
접근할 수 없다.** 그래서 `TypedStore`의 `backing`/`registry`가 public이다.

### 래퍼 타입의 비용

Java에서 문자열 ID를 타입으로 감싸면 실제 객체가 하나 더 생긴다. 그래서 대개 그냥
`String`을 쓰고, 노드 ID 자리에 키를 넘기는 실수를 런타임에 발견한다.

```java
public final class NodeId {           // 힙에 객체가 하나 더 생긴다
    private final String value;
    public NodeId(String value) { this.value = value; }
}
```

Kotlin의 value class는 타입은 남기고 객체는 대부분 없앤다.

```kotlin
@JvmInline
value class NodeId(val value: String)
```

**핵심 차이**: 컴파일러가 가능한 자리에서 래퍼를 벗겨 `String`으로 다룬다. 단
"다른 타입으로 쓰일 때는 박싱된다" — 제네릭 타입 인자, 인터페이스 타입, nullable(`NodeId?`)로
쓰면 실제 객체가 만들어진다.

### 빌더 — 메서드 체이닝 vs 수신 객체 지정 람다

Java 빌더는 `return this`를 반복하고, 중첩 구조는 빌더를 또 만들어 넘긴다.

```java
Query q = new QueryBuilder()
        .limit(10)
        .prefix("user:")
        .or(new QueryBuilder().valueEquals("admin").build())
        .build();
```

Kotlin은 람다의 수신 객체를 바꿔 중첩을 문법으로 표현한다.

```kotlin
val q = query {
    limit = 10
    prefix("user:")
    any {
        valueEquals("admin")
        prefix("user:a")
    }
}
```

**핵심 차이**: 중첩이 들여쓰기로 드러난다. 대신 안쪽 람다에서 바깥 리시버의 멤버까지
호출할 수 있게 되는 위험이 생기는데, 이것을 막는 것이 `@DslMarker`다 — 마커가 붙으면
**가장 가까운 리시버의 멤버만** 암묵적으로 호출할 수 있다.

### 게으른 초기화

Java에서 스레드 안전한 지연 초기화는 이디엄을 외워야 한다.

```java
private volatile String summary;
public String getSummary() {
    String s = summary;
    if (s == null) {
        synchronized (this) {
            s = summary;
            if (s == null) summary = s = compute();   // double-checked locking
        }
    }
    return s;
}
```

Kotlin은 위임 프로퍼티로 표준화돼 있다.

```kotlin
val summary: String by lazy { compute() }
```

**핵심 차이**: "값은 첫 접근에만 계산되고 이후에는 기억된 결과를 돌려준다"는 규약이
라이브러리에 들어 있다. 같은 방식으로 `Delegates.observable`(할당 직후 콜백),
맵 위임(프로퍼티 이름을 키로 조회), 직접 만든 위임(`getValue`/`setValue`)을 쓴다.

### 지연 평가 컬렉션

Java Stream은 단계를 게으르게 이어 붙였다가 종단 연산에서 한 번에 흐른다.

```java
Optional<String> first = keys.stream()
        .filter(k -> k.startsWith("user:"))
        .findFirst();          // 필요한 만큼만 평가된다
```

Kotlin 컬렉션 연산은 기본이 **즉시 평가**(각 단계가 중간 컬렉션을 만든다)이고,
게으르게 하려면 `Sequence`로 바꾼다.

```kotlin
val first = keys.asSequence()
    .filter { it.startsWith("user:") }
    .firstOrNull()
```

**핵심 차이**: `Sequence`는 "요소 하나가 전체 단계를 통과"하고, `Iterable`은
"단계 하나가 전체 요소를 통과"한다. 그래서 `limit`이 걸린 스캔에서 `Sequence`는
필요한 만큼만 평가한다 — 이 레벨은 그것을 계측 카운터로 증명하게 한다.

## 구현 요구사항

### `Ids.kt`

- **L2-01** `NodeId`, `Key` 모두 빈 문자열/공백만 있는 값이면 `IllegalArgumentException`.
- **L2-02** `Key.namespace`는 첫 `:` 앞부분, `:`가 없으면 `"_"`. `compareTo`는 문자열 사전순.
  (`Key`는 value class이므로 backing field를 가질 수 없다 — 계산 프로퍼티로 만든다.)

### `Versioned.kt`

- **L2-03** `compareTo`는 version만 비교한다. `map`은 값을 변환하되 version을 유지한다.
  생성자에서 `version >= 1`을 검증한다.
- **L2-04** 값과 version이 같으면 `equals`가 true이고 `hashCode`도 같다.
  (`data class`가 아니므로 직접 구현한다 — 왜 `data class`로 만들 수 없는지 생각해 볼 것.)
- **L2-05** `Iterable<Versioned<T>>.latest()`는 version이 가장 큰 것을 돌려준다.
  비어 있으면 null. 동률이면 먼저 나온 것.

### `Codec.kt`

- **L2-06** `CodecRegistry.withDefaults()`는 `String`, `Int`, `Long`, `Boolean` 코덱을
  등록한 레지스트리를 만든다. `encode`/`decode`가 왕복해도 값이 같아야 한다.
- **L2-07** 등록되지 않은 타입을 요청하면 `IllegalStateException`.

### `KeyPredicate.kt`

- **L2-08** `and`/`or`는 `infix` 함수로, `not`은 `operator` 함수로 트리를 만든다.
  (`a and b or c`처럼 결합했을 때 만들어지는 트리 모양을 테스트로 고정한다.)
- **L2-09** `matches(key, stored)`가 모든 종류를 평가한다.
  `Namespace`는 `Key.namespace` 일치, `Prefix`는 키 문자열 접두사, `ValueEquals`는
  저장된 값 일치, `MinVersion`은 `stored.version >= version`.
  **`when`에 `else ->`를 쓰지 않는다**(L2-S1).

### `QueryDsl.kt`

- **L2-10** 한 블록 안에서 여러 조건을 호출하면 **And**로 묶인다. 조건이 하나도 없으면
  `KeyPredicate.All`. `limit` 기본값은 `Int.MAX_VALUE`.
- **L2-11** `any { }` 블록 안의 조건들은 **Or**로, `all { }` 블록 안의 조건들은 **And**로
  묶여 바깥 블록에 하나의 조건으로 참여한다. 중첩은 두 단계 이상 되어야 한다.

### `TypedStore.kt`

- **L2-12** `get`/`set`/`contains`/`plusAssign`을 `operator`로 구현한다.
  `store[key]`, `store[key] = "v"`, `key in store`, `store += key to "v"`가 모두 동작한다.
  만료된 키는 `contains`에서 false.
- **L2-13** `operator fun iterator()`로 `for ((key, stored) in store)` 순회가 되게 한다.
  순서는 키 사전순, 만료 항목 제외.
- **L2-14** `scan(query)`는 조건에 맞는 항목을 사전순으로, `limit`만큼만 돌려준다.
- **L2-15** `scan`은 **지연 평가**다. 결과의 첫 항목만 꺼내면 전체 항목이 평가되지 않는다.
  조건 검사 직전에 `scannedEntryCount`를 1씩 올려 이를 관찰 가능하게 만든다.
  중간에 `toList()`로 모으지 않는다(L2-S2).

### `StoreConfig.kt`

- **L2-16** `readTimeoutMillis`에 값을 대입하면 `changeLog`에 `"readTimeoutMillis: 500 -> 800"`
  형식으로 기록된다(할당이 일어난 뒤에 호출되는 `Delegates.observable` 콜백).
- **L2-17** `summary`는 `"<nodeId> rf=<replicationFactor>"` 형식이고, 여러 번 읽어도
  `summaryComputeCount`는 1이다.
- **L2-18** `CountingDelegate`가 읽기/쓰기 횟수를 정확히 센다.

## 작성해야 할 테스트

`src/test/kotlin/com/kotlin/workbook/answer/level2/Level2Test.kt`, 이름은 `L2-XX`로 시작.

| ID | 테스트가 증명해야 하는 것 | 힌트 |
|----|------------------------|------|
| L2-01 | 빈 값/공백 식별자 거부 | |
| L2-02 | `"user:1"` → `"user"`, `"ping"` → `"_"`, `sorted()`가 사전순 | `List<Key>.sorted()`는 `Comparable` 구현이 있어야 컴파일된다 |
| L2-03 | version 대소 비교, `map` 후 version 유지 | `Versioned("a",2) > Versioned("b",1)` |
| L2-04 | 같은 값·version이면 `assertEquals`와 해시 일치 | `hashSetOf(...).size` |
| L2-05 | 복제본 3개 중 최신 선택, 빈 리스트면 null | |
| L2-06 | 네 타입의 encode→decode 왕복 | `registry.decode<Int>("42")` |
| L2-07 | 미등록 타입 요청 시 예외 | 데이터 클래스 하나를 즉석에서 쓴다 |
| L2-08 | `and`/`or`/`!`가 만드는 트리 구조를 직접 비교 | `assertEquals(And(Prefix("a"), MinVersion(2)), ...)` |
| L2-09 | 중첩 조건 참/거짓 각각 | `!(A and B)` 같은 형태 포함 |
| L2-10 | 조건 두 개 → `And`, 조건 없음 → `All`, `limit` 반영 | |
| L2-11 | `any` 안 두 조건이 `Or`, 바깥과는 `And` | |
| L2-12 | 네 연산자 모두 + 만료 키의 `in` 결과 | 가짜 시계로 만료시킨다 |
| L2-13 | `for` 순회 결과가 사전순 | |
| L2-14 | 조건 일치 항목만, `limit` 개수만큼 | |
| L2-15 | 100개를 넣고 `scan(...).first()` 후 `scannedEntryCount < 100` | 카운터를 0으로 초기화한 뒤 측정 |
| L2-16 | 두 번 대입하면 `changeLog`가 2줄, 형식까지 일치 | |
| L2-17 | `summary`를 3번 읽어도 `summaryComputeCount == 1` | |
| L2-18 | 읽기 2회·쓰기 3회 후 카운터 확인 | 위임 객체를 직접 들여다본다 |

## 체크리스트

`./gradlew grade -Plevel=2`가 30/30이면 통과다.

- [ ] `Versioned`의 `out T`를 지웠을 때 컴파일이 깨지는 테스트가 하나 있다(공변성 확인)
- [ ] `reified` 없이 같은 API를 만들려면 무엇이 필요한지 설명할 수 있다
- [ ] `matches`의 `when`에 `else ->`가 없다
- [ ] `scan`이 `Sequence`를 끝까지 유지한다(`toList()` 없음)
- [ ] `@DslMarker` 덕분에 `any { all { any { ... } } }` 안에서 바깥 리시버 멤버가
      암묵적으로 호출되지 않는다는 것을 확인했다
- [ ] 위임 프로퍼티 4종(맵·lazy·observable·직접 구현)을 모두 써 봤다

## 관련 문서

- [level1.md](./level1.md)
- [level3.md](./level3.md)

## 참고 자료

- [Generics: in, out, where](https://kotlinlang.org/docs/generics.html) — 선언 지점 변성, "Consumer in, Producer out!", 스타 프로젝션
- [Inline functions — reified type parameters](https://kotlinlang.org/docs/inline-functions.html#reified-type-parameters) — "Normal functions (not marked as inline) cannot have reified parameters", public inline 함수의 non-public API 접근 제한
- [Inline value classes](https://kotlinlang.org/docs/inline-classes.html) — 단일 프로퍼티 제약, backing field 불가, 박싱되는 경우
- [Operator overloading](https://kotlinlang.org/docs/operator-overloading.html) — `get`/`set`/`contains`/`plusAssign`/`compareTo`/`not`의 변환 규칙
- [Control flow — for loops](https://kotlinlang.org/docs/control-flow.html#for-loops) — `for`가 요구하는 `iterator()` 규약
- [Type-safe builders](https://kotlinlang.org/docs/type-safe-builders.html) — `@DslMarker`가 "가장 가까운 리시버의 멤버만" 허용하는 이유
- [Delegated properties](https://kotlinlang.org/docs/delegated-properties.html) — `lazy`, `observable`, 맵 위임, `getValue`/`setValue` 규약
- [Sequences](https://kotlinlang.org/docs/sequences.html) — 지연 평가와 연산 순서 차이
- [Infix notation](https://kotlinlang.org/docs/functions.html#infix-notation)
