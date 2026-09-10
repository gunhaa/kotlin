package com.kotlin.workbook.skeleton.level3

import com.kotlin.workbook.skeleton.level2.NodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NodeStatus { UP, SUSPECT, DOWN }

/**
 * 하트비트 기반 장애 감지기.
 *
 * 동작:
 * - [intervalMillis]마다 모든 [peers]에 [NodeMessage.Ping]을 **동시에** 보낸다.
 * - 응답이 [timeoutMillis] 안에 오면 그 노드의 연속 실패 수를 0으로 되돌리고 [NodeStatus.UP].
 * - 연속 실패가 1 이상 [failureThreshold] 미만이면 [NodeStatus.SUSPECT].
 * - 연속 실패가 [failureThreshold] 이상이면 [NodeStatus.DOWN].
 * - 초기 상태는 모든 노드가 [NodeStatus.UP].
 *
 * [start]로 띄운 코루틴은 스코프가 취소되면 함께 끝나야 한다(구조화된 동시성).
 * 테스트에서는 `runTest`의 `backgroundScope`에 띄우고 `advanceTimeBy`로 시간을 밀며 관찰한다.
 */
class HeartbeatFailureDetector(
    private val self: NodeId,
    private val peers: List<NodeId>,
    private val network: Network,
    private val intervalMillis: Long = 100,
    private val timeoutMillis: Long = 50,
    private val failureThreshold: Int = 3,
) {

    private val _status = MutableStateFlow(peers.associateWith { NodeStatus.UP })

    val status: StateFlow<Map<NodeId, NodeStatus>> = _status.asStateFlow()

    /** 감지 루프를 [scope]에 띄우고 그 [Job]을 돌려준다. */
    fun start(scope: CoroutineScope): Job = TODO("L3-15, L3-16, L3-17")
}
