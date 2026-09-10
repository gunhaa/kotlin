package com.kotlin.workbook.answer.level1

/**
 * 스토어 통계.
 *
 * @property totalKeys        전체 키 수
 * @property totalValueLength 모든 값 문자열 길이의 합
 * @property keysByNamespace  네임스페이스별 키 목록. 키 `"user:1"`의 네임스페이스는 `"user"`,
 *                            `:`가 없는 키(`"ping"`)의 네임스페이스는 `"_"`. 각 목록은 사전순.
 * @property hottestKey       version이 가장 큰 키. 동률이면 사전순으로 앞선 키. 비어 있으면 null.
 */
data class StoreStats(
    val totalKeys: Int,
    val totalValueLength: Int,
    val keysByNamespace: Map<String, List<String>>,
    val hottestKey: String?,
)

/**
 * 스냅숏에서 통계를 계산한다.
 *
 * 반복문 대신 컬렉션 연산(`sumOf`, `groupBy`, `mapValues`, `maxWithOrNull` 등)으로 푼다.
 * 채점기가 `for (` 루프가 없는지 검사한다.
 */
fun Map<String, StoredValue>.toStats(): StoreStats = TODO("L1-11, L1-12")
