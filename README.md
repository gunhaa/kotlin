# Kotlin 학습 저장소

Java 개발자가 Kotlin을 학습하기 위한 저장소다. 모든 기술 설명은 Kotlin
공식 문서를 실제로 조회해 확인한 내용만 담고, 각 개념은 Java 예시를 먼저
보여준 뒤 대응하는 Kotlin 예시를 보여주는 순서로 작성한다.

## 학습 범위

1. **Kotlin 기본 라이브러리** — 언어 문법 컨벤션, 표준 라이브러리(컬렉션,
   scope function, 시퀀스 등)
2. **Kotlin Coroutine** — 코루틴 빌더, 구조화된 동시성, 디스패처, 예외 처리
3. **Spring MVC / WebFlux** — 위 1·2를 Spring 위에서 어떻게 쓰는지
   (MVC와 WebFlux 각각)
4. **워크북** — 위 내용을 직접 구현해 보는 과제. 문서를 읽는 대신 비어 있는 함수를
   채우고, 테스트로 증명하고, `./gradlew grade`로 채점받는다.

## 패키지 구조

학습 주제별로 코드 패키지와 문서 폴더를 1:1로 대응시킨다.

```
src/main/kotlin/com/kotlin/
├── stdlib/      — 1. Kotlin 기본 라이브러리 예제
├── coroutine/   — 2. Kotlin Coroutine 예제
├── spring/
│   ├── mvc/     — 3-1. Spring MVC 예제
│   └── webflux/ — 3-2. Spring WebFlux 예제
└── workbook/
    ├── skeleton/ — 4. 워크북 과제 원본 (level1 / level2 / level3, 본문은 TODO)
    └── answer/   — 4. 워크북 답안 작업 공간 (채점 대상)

docs/
├── stdlib/      — 1. Kotlin 기본 라이브러리 문서
├── coroutine/   — 2. Kotlin Coroutine 문서
├── spring/
│   ├── mvc/     — 3-1. Spring MVC 문서
│   └── webflux/ — 3-2. Spring WebFlux 문서
└── workbook/    — 4. 워크북 요구사항·채점 루브릭
```

- 패키지 이름은 학습 주제를 그대로 따른다 (`stdlib`, `coroutine`, `spring.mvc`, `spring.webflux`).
- `spring.mvc` / `spring.webflux`를 실제로 실행하려면 `build.gradle.kts`에
  `spring-boot-starter-webmvc` / `spring-boot-starter-webflux` 의존성을 각
  주제를 시작하는 시점에 추가한다 (현재는 순수 Kotlin/JVM 프로젝트라
  Spring 의존성이 없음, `src/main/kotlin/com/kotlin/spring/mvc`·`webflux`는
  코드가 추가되기 전까지 빈 디렉터리로 남아 있다).
- Spring 문서는 **Spring Boot 4.x / Spring Framework 7.x** 기준으로 쓴다. Boot 3에서
  이름이 바뀐 것들(`spring-boot-starter-web` → `spring-boot-starter-webmvc`,
  `com.fasterxml.jackson` → `tools.jackson`, 테스트 스타터·패키지 분리)은 그 기준을
  따른다.

## 워크북 — 직접 구현하며 익히기

`docs/`의 문서가 "Kotlin은 이렇게 쓴다"를 보여준다면, 워크북은 그것을 직접 짜서
테스트로 증명하게 한다. 세 레벨이 **하나의 시스템**을 향해 쌓인다 — 단일 노드
키-값 스토어에서 시작해 정족수 복제 분산 스토어까지 간다.

| 레벨 | 만드는 것 | 다루는 것 | 배점 |
|------|----------|----------|------|
| 1 | 단일 노드 인메모리 KV 스토어 (TTL·명령 파서·통계) | data class, sealed + when, null 안전, `Result`, 컬렉션 연산, `fun interface` | 30 |
| 2 | 타입·질의 계층 (`Key`/`Versioned`/코덱/질의 DSL) | 선언 지점 변성, `reified`, value class, 연산자 오버로딩, `@DslMarker`, 위임 프로퍼티, `Sequence` | 30 |
| 3 | N개 노드 · 정족수 복제 · 장애 감지 | `Mutex`, `async` 팬아웃, `withTimeout`, 지수 백오프, 취소, `SharedFlow`/`StateFlow`, `SupervisorJob`, `runTest` 가상 시간 | 40 |

메인 소스의 함수 본문은 `TODO("요구사항ID")`로 비어 있다. 시그니처는 고정이고,
채점은 **테스트 이름이 요구사항 ID로 시작한다**는 규칙 위에서 자동으로 이뤄진다.

원본(`skeleton`)과 답안(`answer`)이 나뉘어 있다. 원본은 손대지 않고 `answer`에서 풀며,
`skeleton` 쪽 테스트는 실행되지 않고 채점도 `answer` 패키지만 대상으로 한다.

```bash
./gradlew grade                           # 테스트를 돌리고 100점 만점으로 채점
./gradlew grade -Plevel=1                 # 레벨 하나만
./gradlew resetAnswer -Plevel=1 -Pforce   # 레벨 1을 원본 상태로 되돌린다(기존 답안은 백업)
```

채점기는 테스트 통과 여부뿐 아니라 구현 방식도 본다 — 테스트의 `Thread.sleep`,
`sealed` 분기의 `else ->`, 지연 평가 자리의 `toList()` 같은 것을 정적 검사로 걸러낸다.
요구사항과 배점은 `docs/workbook/`에 있다.


## 현재 상태

| 위치 | 다룬 내용 |
|------|-----------|
| `src/main/kotlin/com/kotlin/coroutine/Main.kt` | 코루틴 기본기 실행 예제 (launch, async/await, withContext, 예외 처리) |
| `src/main/kotlin/com/kotlin/stdlib/KotlinVsJavaConventions.kt` | Kotlin ↔ Java 언어 컨벤션 비교 실행 예제 (null 안전성, data class, 스코프 함수, object/companion object 등) |
| `src/main/kotlin/com/kotlin/stdlib/CollectionsConventions.kt` | Java Collections Framework ↔ Kotlin 컬렉션 비교 실행 예제 |
| `docs/coroutine/basics.md` | 코루틴 기본기 문서 (Spring 무관) |
| `docs/stdlib/conventions.md` | Kotlin ↔ Java 언어 컨벤션 비교 문서 |
| `docs/stdlib/collections.md` | Java Collections Framework ↔ Kotlin 컬렉션 비교 문서 |
| `docs/spring/webflux/coroutines.md` | WebFlux에서 코루틴 사용법 |
| `docs/spring/mvc/coroutines.md` | MVC에서 코루틴 사용법 |
| `docs/spring/mvc/basics.md` | Spring MVC 뼈대를 Kotlin으로 (부트스트랩/빈 등록/컨트롤러/라우팅 DSL/예외 처리/RestClient/설정 바인딩) |
| `docs/spring/mvc/jpa.md` | Spring MVC + JPA 계층 구조에서 Kotlin 쓰는 법과 주의사항 (엔티티/리포지토리/서비스/DTO) |
| `docs/spring/webflux/r2dbc.md` | Spring WebFlux + R2DBC 구조에서 Kotlin 쓰는 법과 주의사항 (JPA와의 대비 포함) |
| `docs/spring/testing.md` | Spring + Kotlin 테스트 방법과 테스트 라이브러리 (MockK, SpringMockK, coroutines-test, MockMvc DSL, WebTestClient/RestTestClient, Testcontainers, Boot 4 테스트 스타터·패키지 변경) |
| `docs/spring/configuration.md` | Spring 설정을 Kotlin으로 (빌드/컴파일러 설정, `@ConfigurationProperties` 생성자 바인딩, `@Value` 이스케이프, 애노테이션 배열 인자, 설정값 검증, `BeanRegistrarDsl`, kapt 메타데이터) |
| `docs/spring/overview.md` | MVC/WebFlux를 가로지르는 Java 대응표 + 선택 기준 |
| `src/main/kotlin/com/kotlin/workbook/skeleton/` | 워크북 과제 원본 (level1/level2/level3, 본문은 TODO) |
| `src/main/kotlin/com/kotlin/workbook/answer/` | 워크북 답안 작업 공간 (채점 대상) |
| `docs/workbook/README.md` | 워크북 개요 — 구조, 진행 방법, 필요한 라이브러리 |
| `docs/workbook/level1.md` | Level 1 요구사항 (Kotlin 기본기 — 단일 노드 KV 스토어) |
| `docs/workbook/level2.md` | Level 2 요구사항 (Kotlin 심화 — 타입·질의 계층) |
| `docs/workbook/level3.md` | Level 3 요구사항 (코루틴 — 정족수 복제 분산 스토어) |
| `docs/workbook/grading.md` | 채점 방식과 배점표 (루브릭 원본은 `rubric.tsv`) |
