# Kotlin 워크북 — 직접 구현하며 익히기

읽는 문서가 아니라 **비어 있는 함수를 채우는 문서**다. 저장소의 다른 문서가
"Kotlin은 이렇게 쓴다"를 보여준다면, 워크북은 그것을 실제로 짜서 테스트로
증명하게 한다.

## 무엇을 만드는가

세 레벨이 **하나의 시스템**을 향해 쌓인다. 주제가 매번 바뀌는 연습 문제가 아니라,
단일 노드 키-값 스토어를 정족수 기반 분산 스토어로 키워 나가는 하나의 프로젝트다.

```
Level 1  단일 노드 인메모리 KV 스토어          — Kotlin 기본기
   ↓     (data class / sealed / null 안전 / 컬렉션 / Result)
Level 2  타입과 질의 계층을 얹은 스토어         — Kotlin 심화
   ↓     (제네릭 변성 / reified / value class / 연산자 / DSL / 위임 / Sequence)
Level 3  N개 노드 · 정족수 복제 · 장애 감지     — 코루틴 분산 시스템
         (Mutex / async 팬아웃 / withTimeout / 백오프 / Flow / SupervisorJob)
```

Level 2는 Level 1의 `InMemoryStore`를 그대로 쓰고, Level 3은 Level 2의
`Key` · `NodeId` · `Versioned`를 그대로 쓴다. 앞 레벨을 대충 짜면 뒤 레벨에서
그 대가를 치른다.

## 구조 — skeleton과 answer

워크북은 `com.kotlin.workbook` 한 패키지 아래에 있고, **원본과 작업 공간이 나뉘어 있다.**

```
src/main/kotlin/com/kotlin/workbook/
├── skeleton/          원본. 손대지 않는다. 다시 풀 때 여기서 복사해 온다.
│   ├── level1/   Clock.kt  StoredValue.kt  Command.kt  CommandParser.kt
│   │             InMemoryStore.kt  StoreStats.kt
│   ├── level2/   Ids.kt  Versioned.kt  Codec.kt  KeyPredicate.kt
│   │             QueryDsl.kt  TypedStore.kt  StoreConfig.kt
│   └── level3/   Messages.kt  StoreNode.kt  Network.kt  Retry.kt
│                 Quorum.kt  FailureDetector.kt  Cluster.kt
└── answer/            ← 여기서 푼다. 채점 대상.
    └── level1/  level2/  level3/       (처음에는 skeleton과 같은 내용)

src/test/kotlin/com/kotlin/workbook/
├── skeleton/          테스트 스타터 원본 (실행되지 않는다)
│   └── level1/Level1Test.kt  level2/Level2Test.kt  level3/Level3Test.kt
└── answer/            ← 내 테스트를 여기에 쓴다. 채점 대상.
    └── level1/Level1Test.kt  level2/Level2Test.kt  level3/Level3Test.kt

docs/workbook/
├── README.md      이 문서
├── level1.md      Level 1 요구사항 · 테스트 목록 · 체크리스트
├── level2.md      Level 2 요구사항 · 테스트 목록 · 체크리스트
├── level3.md      Level 3 요구사항 · 테스트 목록 · 체크리스트
├── grading.md     채점 방식과 배점
└── rubric.tsv     채점기가 읽는 루브릭 원본
```

두 벌은 패키지 이름만 다르고(`...workbook.skeleton.level1` ↔ `...workbook.answer.level1`)
내용은 같은 상태에서 시작한다. 그래서 언제든 원본과 내 답안을 나란히 비교할 수 있고,
처음부터 다시 풀고 싶으면 원본을 다시 가져오면 된다.

```bash
./gradlew resetAnswer                     # 아직 없는 파일만 skeleton에서 복사
./gradlew resetAnswer -Plevel=2 -Pforce   # 레벨 2를 원본으로 되돌린다(기존 답안은 백업)
```

`-Pforce`가 없으면 이미 있는 파일은 건드리지 않는다. `-Pforce`로 덮어쓸 때는
덮어쓰기 직전 상태를 `build/workbook-backup/<시각>/`에 남긴다.

`skeleton` 쪽 테스트는 `test` 태스크에서 제외되므로, 원본이 빨간불로 남아 있지 않다.
채점기도 `com.kotlin.workbook.answer` 패키지의 테스트만 센다.

메인 소스의 함수 본문은 전부 `TODO("요구사항ID")`로 비어 있다. **시그니처와 타입
선언은 바꾸지 않는다** — 채점기와 요구사항 문서가 그 시그니처를 전제로 한다.
(시그니처를 바꾸고 싶은 이유가 생겼다면, 그건 대개 요구사항을 잘못 읽은 것이다.)

## 진행 방법

1. `docs/workbook/level1.md`의 요구사항을 읽는다.
2. **`answer` 쪽에서** 테스트를 먼저 쓴다. 요구사항 하나당 테스트 하나 이상.
3. `answer`의 `TODO(...)`를 지우고 구현한다. `skeleton`은 건드리지 않는다.
4. `./gradlew grade`로 점수를 확인한다.
5. 그 레벨 100%가 되면 다음 레벨로 넘어간다.

막혔다가 원본 상태를 다시 보고 싶으면 `skeleton` 쪽 같은 이름의 파일을 열면 되고,
아예 처음부터 다시 풀고 싶으면 `./gradlew resetAnswer -Plevel=N -Pforce`를 쓴다.

테스트 이름은 반드시 **요구사항 ID로 시작**한다. 채점기가 이 이름으로 요구사항과
테스트를 이어붙인다.

```kotlin
@Test
fun `L1-07 TTL이 지난 키는 get에서 보이지 않는다`() { ... }
```

Java에서는 메서드 이름에 공백을 넣을 수 없어 `ttlExpiredKeyIsNotVisibleOnGet` 같은
낙타등 이름을 쓰거나 `@DisplayName`을 따로 붙여야 했다.

```java
@Test
@DisplayName("TTL이 지난 키는 get에서 보이지 않는다")
void ttlExpiredKeyIsNotVisibleOnGet() { ... }
```

Kotlin은 백틱으로 감싼 식별자를 허용하므로 이름 자체가 곧 명세가 된다.

```kotlin
@Test
fun `L1-07 TTL이 지난 키는 get에서 보이지 않는다`() { ... }
```

**핵심 차이**: 별도 애노테이션 없이 함수 이름이 그대로 리포트에 찍히기 때문에,
"요구사항 ID로 시작하는 이름" 규칙만으로 자동 채점이 가능해진다.

## 채점 실행

```bash
./gradlew grade              # 전체 채점 (테스트를 돌린 뒤 점수 계산)
./gradlew grade -Plevel=1    # Level 1만 채점
./gradlew test               # 채점 없이 테스트만
```

`grade`는 테스트가 실패해도 중단하지 않고 끝까지 채점한 뒤,
`build/reports/workbook/grade.txt`에 리포트를 남긴다.

```
[ Level 1 ]  18 / 30 점
--------------------------------------------------------------------------------
  PASS  L1-01  (2점)  StoredValue: version이 1 미만이면 IllegalArgumentException
  FAIL  L1-07  (3점)  InMemoryStore: TTL이 지난 키는 get에서 null이고 ...
        -> 테스트 없음 (테스트 이름이 "L1-07"로 시작해야 한다)
```

채점 규칙과 배점은 [grading.md](./grading.md)에 있다.

## 필요한 라이브러리

`build.gradle.kts`에 이미 들어 있다. 각각이 왜 필요한지는 알고 쓰는 편이 좋다.

| 라이브러리 | 좌표 | 워크북에서 하는 일 |
|-----------|------|------------------|
| kotlin-test (JUnit 5) | `kotlin("test")` | `@Test`, `assertEquals`, `assertFailsWith` 등. `useJUnitPlatform()`을 켜두면 JUnit 5 변형이 선택된다 |
| kotlinx-coroutines-core | `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0` | `launch`/`async`, `Mutex`, `Channel`, `Flow`, `withTimeout` — Level 3의 재료 전부 |
| kotlinx-coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` | `runTest`의 가상 시간. `delay`를 실제로 기다리지 않고 건너뛴다 |
| Turbine (선택) | `app.cash.turbine:turbine:1.2.1` | `Flow`/`StateFlow` 방출을 한 항목씩 검증. `toList()`로도 가능하지만 무한 스트림에는 Turbine이 편하다 |

`suspend` 키워드 자체는 언어 기능이라 의존성이 필요 없지만, `launch`/`async`/`delay`는
`kotlinx.coroutines` 라이브러리의 것이다. 이 경계는 `docs/coroutine/basics.md`에서 다뤘다.

## 하지 말아야 할 것

채점기가 검사하는 항목들이다(위반하면 감점).

- `skeleton` 패키지 수정 — 원본이다. 푸는 곳은 `answer`다.
- 스켈레톤의 **시그니처·타입 선언 변경** — 요구사항 자체가 바뀐다.
- 테스트에서 `Thread.sleep` — 시간은 `Clock` 주입과 가상 시간으로 다룬다.
- Level 3 테스트에서 `runBlocking` — `runTest`를 쓴다.
- `sealed` 계층을 `when`으로 분기할 때 `else ->` 사용 — 완전성 검사를 스스로 꺼버리는 셈이다.
- 지연 평가가 요구된 자리에서 `toList()`로 미리 다 모으기.

## 관련 문서

- [level1.md](./level1.md)
- [level2.md](./level2.md)
- [level3.md](./level3.md)
- [grading.md](./grading.md)

## 참고 자료

- [Set dependencies on test libraries](https://kotlinlang.org/docs/gradle-configure-project.html#set-dependencies-on-test-libraries) — `kotlin("test")`가 `useJUnitPlatform()` 설정에 따라 JUnit 5 변형으로 해석되는 방식
- [kotlinx-coroutines-test 개요](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/) — "The calls to `delay` are automatically skipped, preserving the relative execution order of the tasks", 기본 60초 타임아웃
- [Coroutines overview](https://kotlinlang.org/docs/coroutines-overview.html) — 언어의 `suspend`와 `kotlinx.coroutines` 라이브러리의 경계
- [Turbine 릴리스](https://github.com/cashapp/turbine/releases) — 사용한 버전(1.2.1) 확인
