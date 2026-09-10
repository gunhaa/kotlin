package com.kotlin.workbook.answer.level3

import com.kotlin.workbook.answer.level2.Key
import com.kotlin.workbook.answer.level2.NodeId
import com.kotlin.workbook.answer.level2.Versioned

/**
 * 정족수 설정.
 *
 * @property writeQuorum        쓰기 성공으로 인정할 최소 ack 수 (W)
 * @property readQuorum         읽기 성공으로 인정할 최소 응답 수 (R)
 * @property requestTimeoutMillis 노드 하나에 대한 요청 타임아웃
 * @property maxAttempts        노드 하나당 최대 시도 횟수(첫 시도 포함)
 * @property backoffBaseMillis  재시도 백오프 기준 시간
 */
data class QuorumConfig(
    val replicationFactor: Int,
    val writeQuorum: Int,
    val readQuorum: Int,
    val requestTimeoutMillis: Long = 100,
    val maxAttempts: Int = 3,
    val backoffBaseMillis: Long = 20,
) {
    init {
        // TODO(L3-08): 아래를 모두 검증한다. 위반 시 IllegalArgumentException.
        //   - replicationFactor >= 1
        //   - 1 <= writeQuorum <= replicationFactor
        //   - 1 <= readQuorum  <= replicationFactor
        //   - writeQuorum + readQuorum > replicationFactor   (읽기·쓰기 정족수가 겹쳐야 최신값을 본다)
    }
}

sealed interface WriteOutcome {

    data class Committed(val version: Long, val acks: List<NodeId>) : WriteOutcome

    data class Rejected(val acks: Int, val required: Int) : WriteOutcome
}

sealed interface ReadOutcome {

    data class Found(val value: Versioned<String>, val from: List<NodeId>) : ReadOutcome

    data object NotFound : ReadOutcome

    data class Rejected(val responses: Int, val required: Int) : ReadOutcome
}

/**
 * 정족수 기반 읽기/쓰기 코디네이터.
 *
 * 구현 요구:
 * 1. 모든 복제본에 **동시에** 요청을 보낸다(`async` 팬아웃). 순차 호출이면 가상 시간이 합으로
 *    늘어나 테스트가 잡아낸다.
 * 2. 노드 하나당 요청은 [QuorumConfig.requestTimeoutMillis]로 감싸고
 *    ([kotlinx.coroutines.withTimeoutOrNull] 또는 `withTimeout`), 실패하면 [retryWithBackoff]로
 *    [QuorumConfig.maxAttempts]까지 재시도한다.
 * 3. 정족수를 채우면 **남은 응답을 기다리지 않고** 즉시 돌려준다. 이때 아직 진행 중인 요청은
 *    구조화된 동시성으로 정리한다(코디네이터가 반환한 뒤에도 코루틴이 살아 있으면 안 된다).
 * 4. 읽기는 모인 응답 중 version이 가장 큰 값을 고른다(Last-Write-Wins). 전부 값이 없으면
 *    [ReadOutcome.NotFound].
 * 5. 정족수를 못 채우면 [WriteOutcome.Rejected] / [ReadOutcome.Rejected].
 */
class QuorumCoordinator(
    private val self: NodeId,
    private val replicas: List<NodeId>,
    private val network: Network,
    private val config: QuorumConfig,
) {

    /** 노드별 총 시도 횟수(계측용). 재시도도 포함해서 센다. */
    val attemptsPerNode: MutableMap<NodeId, Int> = mutableMapOf()

    suspend fun write(key: Key, value: String, version: Long): WriteOutcome =
        TODO("L3-09, L3-10, L3-11, L3-12")

    suspend fun read(key: Key): ReadOutcome = TODO("L3-13, L3-14")
}
