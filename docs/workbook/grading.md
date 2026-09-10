# 채점 방식

워크북은 "읽고 이해했다"가 아니라 **동작하는 코드와 그것을 증명하는 테스트**로 채점한다.
채점기는 Gradle 태스크이고, 루브릭 원본은 [rubric.tsv](./rubric.tsv)다.

채점 대상은 **`com.kotlin.workbook.answer` 패키지 한 곳**이다.
`com.kotlin.workbook.skeleton`은 원본 보관용이라 테스트가 실행되지도, 채점되지도 않는다.

## 실행

```bash
./gradlew grade              # 전체 채점
./gradlew grade -Plevel=2    # 특정 레벨만
```

원본으로 되돌리려면:

```bash
./gradlew resetAnswer                     # 아직 없는 파일만 skeleton에서 복사
./gradlew resetAnswer -Plevel=2 -Pforce   # 레벨 2를 덮어쓰며 되돌린다
```

`-Pforce`로 덮어쓸 때는 직전 상태를 `build/workbook-backup/<시각>/`에 백업한다.

`grade`는 먼저 `test`를 돌린다. 테스트가 실패해도 중단하지 않고(`ignoreFailures`)
끝까지 채점한 뒤 결과를 콘솔과 `build/reports/workbook/grade.txt`에 남긴다.

> 테스트 **컴파일**이 깨지면 채점 자체가 진행되지 않는다. 아직 안 쓴 요구사항은
> 테스트를 비워 두면 되고, 반쯤 쓰다 만 코드를 남겨 두면 안 된다.

## 채점 항목의 세 종류

| 종류 | 통과 조건 |
|------|----------|
| `TEST` | 이름이 **요구사항 ID로 시작하는** 테스트가 하나 이상 있고, 그 테스트가 전부 통과한다 |
| `SOURCE` | 지정한 파일이 지정한 정규식을 포함한다 (예: `withLock {`) |
| `SOURCE_ABSENT` | 지정한 파일에 지정한 정규식이 **없다** (예: `Thread.sleep`) |
| `SOURCE_BOTH` | 필수 패턴은 있고 금지 패턴은 없다 |

`SOURCE*` 항목은 "결과는 맞지만 방법이 틀린" 풀이를 걸러낸다.
`Thread.sleep`으로 TTL을 검증하거나, `sealed` 계층을 `else ->`로 처리하거나,
`Sequence`를 `toList()`로 미리 펼치면 테스트는 통과해도 점수는 깎인다.

## 테스트 이름 규칙

채점기는 JUnit 결과 XML의 `name` 속성이 요구사항 ID로 시작하는지 본다.

```kotlin
@Test
fun `L1-07 TTL이 지난 키는 get에서 보이지 않는다`() { ... }   // O
fun `TTL 만료 (L1-07)`() { ... }                             // X — ID가 앞에 없다
```

한 요구사항에 테스트를 여러 개 써도 된다. 모두 같은 ID로 시작하면 되고,
그중 하나라도 실패하면 그 항목은 0점이다(부분 점수 없음).

## 배점

| 레벨 | 주제 | 배점 |
|------|------|------|
| Level 1 | Kotlin 기본기 — 단일 노드 KV 스토어 | 30 |
| Level 2 | Kotlin 심화 — 타입·질의 계층 | 30 |
| Level 3 | 코루틴 — 정족수 복제 분산 스토어 | 40 |
| | **합계** | **100** |

### Level 1 (30점)

| ID | 배점 | 종류 | 확인 내용 |
|----|-----|------|----------|
| `L1-01` | 2 | 테스트 | StoredValue: version이 1 미만이면 IllegalArgumentException |
| `L1-02` | 2 | 테스트 | StoredValue.isExpired: 만료 전 / 만료 시각 정각 / 만료 후 |
| `L1-03` | 2 | 테스트 | StoredValue.remainingTtlMillis: 무기한 null, 만료 후 0 |
| `L1-04` | 2 | 테스트 | CommandParser: PUT/GET/DEL/KEYS 정상 파싱(대소문자 무시, TTL 선택) |
| `L1-05` | 2 | 테스트 | CommandParser: 잘못된 입력은 Result.failure로 돌아온다(예외를 던지지 않는다) |
| `L1-06` | 2 | 테스트 | InMemoryStore.put: version이 키별로 1부터 증가하고 size에 반영된다 |
| `L1-07` | 3 | 테스트 | InMemoryStore: TTL이 지난 키는 get에서 null이고 내부에서도 제거된다(가짜 Clock 사용) |
| `L1-08` | 1 | 테스트 | InMemoryStore.delete: 있으면 true, 없거나 만료면 false |
| `L1-09` | 2 | 테스트 | InMemoryStore.keys/snapshot: 만료 제외 + 사전순 |
| `L1-10` | 3 | 테스트 | InMemoryStore.execute: 네 가지 Command를 모두 올바른 CommandResult로 매핑 |
| `L1-11` | 2 | 테스트 | toStats: totalKeys/totalValueLength/keysByNamespace 집계 |
| `L1-12` | 1 | 테스트 | toStats: hottestKey는 version 최대, 동률이면 사전순 앞선 키 |
| `L1-S1` | 2 | 정적 검사 | InMemoryStore가 Clock을 주입받아 시간에 의존하지 않는다 |
| `L1-S2` | 2 | 정적 검사 | toStats를 for 루프 없이 컬렉션 연산(groupBy/sumOf 등)으로 구현했다 |
| `L1-S3` | 2 | 정적 검사 | 시간 의존 테스트를 가짜 시계로 밀어서 검증했다(Thread.sleep 금지) |

### Level 2 (30점)

| ID | 배점 | 종류 | 확인 내용 |
|----|-----|------|----------|
| `L2-01` | 1 | 테스트 | value class: 빈 문자열 NodeId/Key는 IllegalArgumentException |
| `L2-02` | 1 | 테스트 | Key: namespace 계산과 compareTo 기반 정렬 |
| `L2-03` | 2 | 테스트 | Versioned: compareTo는 version만 비교, map은 version 유지 |
| `L2-04` | 1 | 테스트 | Versioned: 값과 version이 같으면 동등하고 해시도 같다 |
| `L2-05` | 1 | 테스트 | latest(): 여러 복제본 중 최신 version 선택, 비어 있으면 null |
| `L2-06` | 2 | 테스트 | CodecRegistry: reified encode/decode 왕복(String/Int/Long/Boolean) |
| `L2-07` | 1 | 테스트 | CodecRegistry: 미등록 타입은 IllegalStateException |
| `L2-08` | 1 | 테스트 | KeyPredicate: and/or 중위 함수와 not 연산자가 트리를 만든다 |
| `L2-09` | 2 | 테스트 | KeyPredicate.matches: 중첩 조건(And/Or/Not/MinVersion) 평가 |
| `L2-10` | 2 | 테스트 | query DSL: 같은 블록의 조건들이 And로 묶이고 limit이 반영된다 |
| `L2-11` | 2 | 테스트 | query DSL: any/all 중첩 블록이 Or/And로 묶인다 |
| `L2-12` | 2 | 테스트 | TypedStore 연산자: get/set/contains/plusAssign |
| `L2-13` | 1 | 테스트 | TypedStore: for ((key, stored) in store) 순회가 사전순으로 동작 |
| `L2-14` | 1 | 테스트 | TypedStore.scan: 질의 결과와 limit 적용 |
| `L2-15` | 2 | 테스트 | TypedStore.scan: 지연 평가 증명(first() 호출로 전부 평가되지 않는다) |
| `L2-16` | 1 | 테스트 | observable 위임: readTimeoutMillis 변경이 changeLog에 남는다 |
| `L2-17` | 1 | 테스트 | lazy 위임: summary는 여러 번 읽어도 한 번만 계산된다 |
| `L2-18` | 1 | 테스트 | CountingDelegate: 읽기/쓰기 횟수를 정확히 센다 |
| `L2-S1` | 2 | 정적 검사 | matches를 else 가지 없는 when으로 구현했다(sealed 완전성 활용) |
| `L2-S2` | 2 | 정적 검사 | scan이 중간에 List로 모으지 않고 Sequence를 유지한다 |
| `L2-S3` | 1 | 정적 검사 | 지연 평가를 계측 카운터로 실제로 관찰했다(Thread.sleep 금지) |

### Level 3 (40점)

| ID | 배점 | 종류 | 확인 내용 |
|----|-----|------|----------|
| `L3-01` | 2 | 테스트 | StoreNode: Write/Read/snapshot 기본 동작 |
| `L3-02` | 2 | 테스트 | StoreNode: 더 낮거나 같은 version은 무시하고 applied=false |
| `L3-03` | 2 | 테스트 | StoreNode.changes: 실제로 반영된 변경만 흘러나온다 |
| `L3-04` | 2 | 테스트 | SimulatedNetwork: 왕복 지연이 가상 시간(currentTime)에 그대로 나타난다 |
| `L3-05` | 2 | 테스트 | SimulatedNetwork: UNREACHABLE은 즉시 예외, BLACK_HOLE은 타임아웃으로만 빠져나온다 |
| `L3-06` | 2 | 테스트 | retryWithBackoff: 시도 횟수와 지수 백오프 누적 대기 시간 |
| `L3-07` | 2 | 테스트 | retryWithBackoff: CancellationException은 재시도하지 않고 즉시 전파한다 |
| `L3-08` | 1 | 테스트 | QuorumConfig: W + R > N 위반은 IllegalArgumentException |
| `L3-09` | 2 | 테스트 | write: W개 ack를 모으면 Committed(version, acks) |
| `L3-10` | 2 | 테스트 | write: 팬아웃이 병렬이라 총 소요 시간이 지연의 합이 아니라 최댓값에 가깝다 |
| `L3-11` | 2 | 테스트 | write: 정족수를 채우면 느린 복제본을 기다리지 않고 반환하고, 남은 코루틴을 남기지 않는다 |
| `L3-12` | 2 | 테스트 | write: 노드 실패 시 재시도·백오프를 거치고도 W를 못 채우면 Rejected |
| `L3-13` | 2 | 테스트 | read: R개 응답 중 version이 가장 큰 값을 돌려준다 |
| `L3-14` | 2 | 테스트 | read: 모든 응답에 값이 없으면 NotFound |
| `L3-15` | 2 | 테스트 | FailureDetector: 하트비트가 정상이면 UP을 유지한다 |
| `L3-16` | 2 | 테스트 | FailureDetector: 연속 실패가 쌓이면 SUSPECT를 거쳐 DOWN이 되고, 복구되면 UP으로 돌아온다 |
| `L3-17` | 2 | 테스트 | FailureDetector: 스코프를 취소하면 감지 루프도 끝난다(구조화된 동시성) |
| `L3-18` | 1 | 테스트 | Cluster.of: 노드/네트워크/코디네이터/감지기 조립 |
| `L3-19` | 2 | 테스트 | Cluster.start: 백그라운드 자식 하나가 실패해도 형제와 클러스터가 살아남는다(SupervisorJob) |
| `L3-S1` | 1 | 정적 검사 | StoreNode가 Mutex.withLock으로 상태를 보호한다 |
| `L3-S2` | 1 | 정적 검사 | 코디네이터가 async로 팬아웃한다 |
| `L3-S3` | 2 | 정적 검사 | Level 3 테스트가 runTest 가상 시간(currentTime)으로 검증하고 runBlocking을 쓰지 않는다 |

## 스스로 채점할 때의 흐름

1. `./gradlew grade -Plevel=1` — 0점에서 시작한다(정상이다).
2. FAIL 줄의 `->` 사유를 읽는다. "테스트 없음"이면 테스트부터, "실패"면 구현부터.
3. 요구사항 하나를 골라 **테스트를 먼저** 쓰고, 빨간 것을 확인한 뒤 구현한다.
4. 그 레벨이 만점이 되면 다음 레벨로 넘어간다.

## 루브릭을 고치고 싶다면

`rubric.tsv`는 탭으로 구분된 6열이다.

```
id	level	points	kind	target	description
```

- `target`은 `TEST`에서는 비워 두고, `SOURCE*`에서는 `경로::정규식`
  (`SOURCE_BOTH`는 `경로::필수정규식::금지정규식`) 형식이다.
  경로는 저장소 루트 기준이며, `answer` 쪽을 가리킨다.
- 정규식은 `MULTILINE`으로 컴파일된다.
- 항목을 추가하면 배점 합계도 함께 조정한다(채점기는 합계를 검증하지 않는다).

## 관련 문서

- [README.md](./README.md)
- [level1.md](./level1.md)
- [level2.md](./level2.md)
- [level3.md](./level3.md)
