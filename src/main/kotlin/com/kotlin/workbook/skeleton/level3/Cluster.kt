package com.kotlin.workbook.skeleton.level3

import com.kotlin.workbook.skeleton.level2.NodeId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * 노드 · 네트워크 · 코디네이터 · 장애 감지기를 한 덩어리로 묶은 클러스터.
 *
 * [start]는 백그라운드 작업(장애 감지기 등)을 [kotlinx.coroutines.SupervisorJob] 아래에서 띄운다.
 * 자식 하나가 예외로 죽어도 형제와 클러스터 전체가 함께 죽으면 안 된다.
 */
class Cluster(
    val nodes: Map<NodeId, StoreNode>,
    val network: SimulatedNetwork,
    val config: QuorumConfig,
) {

    val coordinator: QuorumCoordinator = TODO("L3-18")

    val detector: HeartbeatFailureDetector = TODO("L3-18")

    /** 백그라운드 작업을 띄우고, 그것들을 묶은 [Job]을 돌려준다. */
    fun start(scope: CoroutineScope): Job = TODO("L3-19")

    companion object {

        /** `node-0` … `node-{size-1}` 이름의 노드로 클러스터를 만든다. */
        fun of(size: Int, config: QuorumConfig, latencyMillis: Long = 10): Cluster =
            TODO("L3-18")
    }
}
