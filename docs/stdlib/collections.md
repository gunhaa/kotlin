# Java Collections Framework vs Kotlin 컬렉션

컬렉션 사용법만 다룬다. 실행 가능한 예제는
`src/main/kotlin/com/kotlin/stdlib/CollectionsConventions.kt`
(`./gradlew compileKotlin` 후 `com.kotlin.stdlib.CollectionsConventionsKt`
직접 실행으로 확인 가능).

## 1. 읽기 전용 vs 가변 컬렉션 인터페이스

Java는 `List`/`Set`/`Map` 인터페이스가 하나뿐이고, "읽기 전용"은 관례일
뿐이다. `List.of(...)`로 만든 리스트에 `add()`를 호출하면 컴파일은 되지만
런타임에 `UnsupportedOperationException`이 발생한다 — 실수를 컴파일 시점에
잡아주지 못한다.

```java
List<String> readonly = List.of("a", "b", "c");
readonly.add("d"); // 컴파일 OK, 런타임에 UnsupportedOperationException
```

Kotlin은 `Collection`/`List`/`Set`/`Map`(읽기 전용)과
`MutableCollection`/`MutableList`/`MutableSet`/`MutableMap`(가변)이 애초에
다른 타입이다. 읽기 전용 타입에는 `add()` 자체가 존재하지 않아 컴파일
시점에 막힌다.

```kotlin
val readonly: List<String> = listOf("a", "b", "c")
// readonly.add("d") // 컴파일 에러: List<String>에는 add()가 없음

val mutable: MutableList<String> = mutableListOf("a", "b", "c")
mutable.add("d") // MutableList에만 있는 함수
```

**핵심 차이**: `val`로 선언해도 그 컬렉션이 가변 타입(`MutableList` 등)이면
내용물은 바뀔 수 있다 — `val`이 막는 건 변수 재할당이지 컬렉션 내용 변경이
아니다. Java의 "읽기 전용"은 런타임 예외로만 강제되지만, Kotlin은 타입
자체가 다르므로 컴파일러가 강제한다.

## 2. 컬렉션 생성 함수

Java는 컬렉션 종류별로 구현체를 직접 선택해서 생성자를 호출해야 한다.

```java
List<String> list = new ArrayList<>(List.of("a", "b"));
Set<String> set = new HashSet<>(Set.of("a", "b"));
Map<String, Integer> map = new HashMap<>(Map.of("a", 1, "b", 2));
```

Kotlin은 `listOf`/`setOf`/`mapOf`(읽기 전용)와
`mutableListOf`/`mutableSetOf`/`mutableMapOf`(가변) 팩토리 함수로 구현체
선택 없이 바로 만든다.

```kotlin
val list = mutableListOf("a", "b")
val set = setOf("a", "b", "a")               // 중복은 자동으로 하나만 남음
val map = mapOf("a" to 1, "b" to 2)          // to 중위 함수로 키-값 쌍 표현
```

기본 구현체는 `listOf`/`mutableListOf` → `ArrayList`, `setOf`/`mutableSetOf`
→ `LinkedHashSet`(삽입 순서 보존), `mapOf`/`mutableMapOf` → `LinkedHashMap`이다.

## 3. filter / map vs Java Stream

Java는 컬렉션 자체엔 `filter`/`map`이 없어서 `Stream`으로 변환한 뒤 다시
컬렉션으로 모아야 한다.

```java
List<String> names = List.of("Gunhaa", "Kim", "Lee", "Park");
List<String> result = names.stream()
        .filter(n -> n.length() > 3)
        .map(String::toUpperCase)
        .collect(Collectors.toList());
```

Kotlin은 `List` 자체에 `filter`/`map`이 있어서 스트림 변환이나 `collect`가
필요 없다. 결과 타입도 그대로 `List<String>`이다.

```kotlin
val names = listOf("Gunhaa", "Kim", "Lee", "Park")
val result = names.filter { it.length > 3 }.map { it.uppercase() }
```

**핵심 차이**: Java Stream은 한 번 소비하면 재사용할 수 없는 일회용
파이프라인이지만, Kotlin의 `filter`/`map`은 매번 새 `List`를 반환하는
평범한 함수라 그 결과를 다시 재사용할 수 있다.

## 4. Map 생성 / 조회 / 순회

Java에서 키가 없을 때 기본값을 넣으며 조회하려면 `computeIfAbsent`를 쓰고,
순회는 `entrySet()`을 거쳐 `getKey()`/`getValue()`를 각각 호출해야 한다.

```java
Map<String, List<String>> groups = new HashMap<>();
groups.computeIfAbsent("even", k -> new ArrayList<>()).add("2");

for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

Kotlin은 `getOrPut`이 `computeIfAbsent` 역할을 하고, 순회는 구조 분해
선언으로 key/value를 바로 이름 붙여 꺼낸다.

```kotlin
val groups = mutableMapOf<String, MutableList<String>>()
groups.getOrPut("even") { mutableListOf() }.add("2")

for ((key, values) in groups) {
    println("$key=$values")
}
```

## 5. Sequence vs Collection — Java Stream과 비교

Java Stream은 원래 지연 평가(lazy)라 중간 연산이 바로 실행되지 않고
터미널 연산(`collect` 등)을 만나야 실행된다.

```java
List<Integer> result = numbers.stream()
        .filter(n -> { System.out.println("filter: " + n); return n > 3; })
        .map(n -> { System.out.println("map: " + n); return n * 2; })
        .limit(2)
        .collect(Collectors.toList()); // 이 시점에 비로소 위 연산들이 실행됨
```

반면 Kotlin의 `List`에 대한 `filter`/`map`은 **즉시 실행(eager)**된다 — 각
단계가 전체 컬렉션에 대해 끝난 뒤 다음 단계로 넘어가며, 매 단계마다 중간
`List`가 새로 만들어진다. Java Stream과 같은 지연 평가가 필요하면
`asSequence()`로 명시적으로 전환해야 한다.

```kotlin
val numbers = listOf(1, 2, 3, 4, 5, 6)

// 즉시 실행: filter가 6개 전부를 먼저 처리한 뒤, map이 그 결과(3개)를 처리한다
val eager = numbers
    .filter { it > 3 }
    .map { it * 2 }
    .take(2)

// 지연 평가: take(2)를 채우는 순간 나머지 원소는 건드리지 않는다
val lazy = numbers.asSequence()
    .filter { it > 3 }
    .map { it * 2 }
    .take(2)
    .toList() // 터미널 연산 — 이 시점에 비로소 실행 시작
```

실제로 실행해보면 `eager`는 filter 6회 + map 3회(총 9번 연산)를 수행하고,
`lazy`는 filter 5회 + map 2회(총 7번 연산)만 수행하고 멈춘다 — 결과값은
`[8, 10]`으로 동일하지만 연산 횟수가 다르다.

**핵심 차이**: 이름만 다를 뿐 Kotlin의 `Sequence`는 Java의 `Stream`과 사실상
같은 역할(지연 평가, 중간 연산/터미널 연산 구분)을 한다. 차이는 Kotlin의
기본값이 반대라는 점이다 — Java는 `Stream`으로 바꾸는 순간 항상 지연 평가,
Kotlin은 `List` 자체는 즉시 실행이고 `asSequence()`로 바꿔야 지연 평가가
된다. 대규모 컬렉션에 여러 단계 연산을 체이닝할 때만 `Sequence`를 쓰는 게
관용적이다 (작은 컬렉션에 단순 연산이면 즉시 실행 쪽이 더 직관적이고
오버헤드도 적다).

## 관련 문서

- [conventions.md](conventions.md) — 언어 컨벤션 전반 (구조 분해 선언 등)

## 참고 자료

- [Collections overview](https://kotlinlang.org/docs/collections-overview.html) — 읽기 전용/가변 인터페이스, 생성 함수
- [Filtering collections](https://kotlinlang.org/docs/collection-filtering.html) — filter, partition 등
- [Sequences](https://kotlinlang.org/docs/sequences.html) — lazy evaluation, Java Stream과의 비교
