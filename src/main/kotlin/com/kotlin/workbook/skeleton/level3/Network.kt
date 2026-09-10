package com.kotlin.workbook.skeleton.level3

import com.kotlin.workbook.skeleton.level2.NodeId

/** 노드 사이의 통신. 실제 소켓이든 시뮬레이터든 코디네이터는 이것만 안다. */
interface Network {

    /** 상대에 닿지 못하면 [NodeUnreachableException]을 던진다. */
    suspend fun send(from: NodeId, to: NodeId, message: NodeMessage): NodeResponse
}

/** 노드에 걸어둘 장애 모드. */
enum class FaultMode {

    /** 즉시 [NodeUnreachableException]. (연결 거부처럼) */
    UNREACHABLE,

    /** 응답이 영원히 오지 않는다. 호출자의 타임아웃 처리를 시험한다. */
    BLACK_HOLE,
}

/**
 * 테스트용 네트워크 시뮬레이터.
 *
 * 지연은 [kotlinx.coroutines.delay]로 표현한다. `runTest`의 가상 시간이 이 지연을 건너뛰므로,
 * "1초 지연 노드"를 두고도 테스트는 즉시 끝나면서 [kotlinx.coroutines.test.TestScope.currentTime]으로
 * 논리적 경과 시간을 검증할 수 있다.
 *
 * @param defaultLatencyMillis 노드별로 따로 지정하지 않았을 때의 편도 지연
 */
class SimulatedNetwork(
    private val nodes: Map<NodeId, StoreNode>,
    private val defaultLatencyMillis: Long = 10,
) : Network {

    private val faults: MutableMap<NodeId, FaultMode> = mutableMapOf()

    private val latencies: MutableMap<NodeId, Long> = mutableMapOf()

    /** 실제로 노드까지 배달된 메시지 수(계측용). */
    var deliveredCount: Int = 0
        private set

    fun latencyOf(node: NodeId): Long = latencies[node] ?: defaultLatencyMillis

    fun setLatency(node: NodeId, millis: Long) {
        latencies[node] = millis
    }

    fun fail(node: NodeId, mode: FaultMode = FaultMode.UNREACHABLE) {
        faults[node] = mode
    }

    fun heal(node: NodeId) {
        faults.remove(node)
    }

    /**
     * 규칙:
     * 1. [FaultMode.UNREACHABLE]이면 지연 없이 [NodeUnreachableException].
     * 2. [FaultMode.BLACK_HOLE]이면 영원히 중단한다([kotlinx.coroutines.awaitCancellation]).
     * 3. 정상이면 편도 지연만큼 `delay` → 노드 호출 → 다시 편도 지연만큼 `delay`.
     * 4. 모르는 노드로 보내면 [NodeUnreachableException].
     */
    override suspend fun send(from: NodeId, to: NodeId, message: NodeMessage): NodeResponse =
        TODO("L3-04, L3-05")
}
