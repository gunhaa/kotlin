package com.kotlin.stdlib

/**
 * Java Collections Framework와 Kotlin 컬렉션의 사용법 차이를 실행 가능한 코드로 정리한 예제.
 * 각 함수는 Kotlin 방식으로 작성하고, 바로 위 주석에 Java 비교를 붙였다.
 * 문서 버전: docs/stdlib/collections.md
 */
fun main() {
    readOnlyVsMutableExample()
    collectionCreationExample()
    filterMapExample()
    mapOperationsExample()
    sequenceVsEagerExample()
}

// ── 1. 읽기 전용 vs 가변 컬렉션 인터페이스 ────────────────────────
//
// Java: List/Set/Map 인터페이스는 하나뿐이고, "읽기 전용"은 관례일 뿐이다.
// List.of(...)로 만든 리스트에 add()를 호출하면 컴파일은 되지만
// 런타임에 UnsupportedOperationException이 발생한다 — 실수를 컴파일 시점에
// 잡아주지 못한다.
//
// Java:
//   List<String> readonly = List.of("a", "b", "c");
//   readonly.add("d"); // 컴파일 OK, 런타임에 UnsupportedOperationException
//
// Kotlin: Collection/List/Set/Map(읽기 전용)과 MutableCollection/MutableList/...
// (가변)이 애초에 다른 타입이다. 읽기 전용 타입에는 add() 자체가 존재하지 않아
// 컴파일 시점에 막힌다.
fun readOnlyVsMutableExample() {
    println("== 1. 읽기 전용 vs 가변 컬렉션 ==")
    val readonly: List<String> = listOf("a", "b", "c")
    // readonly.add("d") // 컴파일 에러: List<String>에는 add()가 없음

    val mutable: MutableList<String> = mutableListOf("a", "b", "c")
    mutable.add("d") // MutableList에만 있는 함수

    println("readonly=$readonly, mutable=$mutable")
}

// ── 2. 컬렉션 생성 함수 ───────────────────────────────────────────
//
// Java: 컬렉션 종류별로 구현체를 직접 선택해서 생성자를 호출해야 한다.
//
// Java:
//   List<String> list = new ArrayList<>(List.of("a", "b"));
//   Set<String> set = new HashSet<>(Set.of("a", "b"));
//   Map<String, Integer> map = new HashMap<>(Map.of("a", 1, "b", 2));
//
// Kotlin: listOf/setOf/mapOf(읽기 전용)와 mutableListOf/mutableSetOf/mutableMapOf(가변)
// 팩토리 함수로 구현체 선택 없이 바로 만든다 (기본 구현은 ArrayList/LinkedHashSet/LinkedHashMap).
fun collectionCreationExample() {
    println("\n== 2. 컬렉션 생성 함수 ==")
    val list = mutableListOf("a", "b")
    val set = setOf("a", "b", "a") // 중복은 자동으로 하나만 남음
    val map = mapOf("a" to 1, "b" to 2) // Pair의 리스트처럼 to 중위 함수로 표현

    println("list=$list, set=$set, map=$map")
}

// ── 3. filter/map vs Java Stream ─────────────────────────────────
//
// Java: 컬렉션 자체엔 filter/map이 없어서 Stream으로 변환한 뒤 다시
// 컬렉션으로 모아야 한다 (Collectors.toList() 보일러플레이트).
//
// Java:
//   List<String> names = List.of("Gunhaa", "Kim", "Lee", "Park");
//   List<String> result = names.stream()
//           .filter(n -> n.length() > 3)
//           .map(String::toUpperCase)
//           .collect(Collectors.toList());
//
// Kotlin: List 자체에 filter/map이 있어서 Stream 변환이나 collect가 필요 없다.
// 결과 타입도 그대로 List<String>이다.
fun filterMapExample() {
    println("\n== 3. filter/map ==")
    val names = listOf("Gunhaa", "Kim", "Lee", "Park")
    val result = names.filter { it.length > 3 }.map { it.uppercase() }
    println(result)
}

// ── 4. Map 생성/조회/순회 ─────────────────────────────────────────
//
// Java: 키가 없을 때 기본값을 넣으며 조회하려면 computeIfAbsent를 쓴다.
// entrySet()을 순회하며 getKey()/getValue()를 각각 호출해야 한다.
//
// Java:
//   Map<String, List<String>> groups = new HashMap<>();
//   groups.computeIfAbsent("even", k -> new ArrayList<>()).add("2");
//   for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
//       System.out.println(entry.getKey() + "=" + entry.getValue());
//   }
//
// Kotlin: getOrPut이 computeIfAbsent 역할을 하고, 순회는 구조 분해 선언으로
// key/value를 바로 이름 붙여 꺼낸다 (docs/stdlib/conventions.md 12번 참고).
fun mapOperationsExample() {
    println("\n== 4. Map 생성/조회/순회 ==")
    val groups = mutableMapOf<String, MutableList<String>>()
    groups.getOrPut("even") { mutableListOf() }.add("2")
    groups.getOrPut("even") { mutableListOf() }.add("4") // 이미 있으면 새로 안 만들고 기존 리스트에 추가

    for ((key, values) in groups) {
        println("$key=$values")
    }
}

// ── 5. Sequence vs Collection(즉시 실행) — Java Stream과 비교 ────
//
// Java Stream은 원래 지연 평가(lazy)라 중간 연산이 바로 실행되지 않고
// 터미널 연산(collect 등)을 만나야 실행된다.
//
// Java:
//   List<Integer> result = numbers.stream()
//           .filter(n -> { System.out.println("filter: " + n); return n > 3; })
//           .map(n -> { System.out.println("map: " + n); return n * 2; })
//           .limit(2)
//           .collect(Collectors.toList()); // 이 시점에 비로소 위 연산들이 실행됨
//
// Kotlin의 List에 대한 filter/map은 Java Stream과 달리 즉시 실행(eager)된다 —
// 각 단계가 전체 컬렉션에 대해 끝난 뒤 다음 단계로 넘어가며, 매 단계마다 중간
// 컬렉션이 만들어진다. Java Stream처럼 지연 평가가 필요하면 asSequence()로
// 명시적으로 전환해야 한다.
fun sequenceVsEagerExample() {
    println("\n== 5. Sequence vs Collection(즉시 실행) ==")
    val numbers = listOf(1, 2, 3, 4, 5, 6)

    println("-- List(즉시 실행): 매 단계가 전체 리스트를 순회 --")
    val eager = numbers
        .filter { println("filter: $it"); it > 3 }
        .map { println("map: $it"); it * 2 }
        .take(2)
    println("eager 결과: $eager")

    println("-- Sequence(지연 평가): 결과 2개가 채워지면 즉시 중단 --")
    val lazy = numbers.asSequence()
        .filter { println("filter: $it"); it > 3 }
        .map { println("map: $it"); it * 2 }
        .take(2)
        .toList() // 터미널 연산 — 이 시점에 비로소 실행 시작
    println("lazy 결과: $lazy")
}
