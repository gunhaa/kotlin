package com.kotlin.workbook.answer.level3

import com.kotlin.workbook.answer.level2.Key
import com.kotlin.workbook.answer.level2.NodeId
import com.kotlin.workbook.answer.level2.Versioned
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex

/**
 * 클러스터의 노드 하나. 자기 몫의 데이터를 들고 요청에 답한다.
 *
 * 상태는 여러 코루틴이 동시에 건드린다. `synchronized`는 스레드를 **블로킹**하므로 코루틴에서는
 * 쓰지 않고, 중단 가능한 [Mutex]로 임계 구역을 보호한다.
 *
 * 쓰기 규칙(Last-Write-Wins): 들어온 version이 **기존 version보다 클 때만** 반영한다.
 * 반영됐을 때만 [changes]로 [ChangeEvent]를 흘린다.
 */
class StoreNode(val id: NodeId) {

    private val mutex = Mutex()

    private val data: MutableMap<Key, Versioned<String>> = mutableMapOf()

    private val _changes = MutableSharedFlow<ChangeEvent>(extraBufferCapacity = 64)

    /** 이 노드에 반영된 변경 스트림. 구독자가 없어도 노드는 멈추지 않아야 한다. */
    val changes: SharedFlow<ChangeEvent> = _changes.asSharedFlow()

    /** 이 노드가 처리한 요청 수(계측용). */
    var handledCount: Int = 0
        private set

    suspend fun handle(message: NodeMessage): NodeResponse = TODO("L3-01, L3-02, L3-03")

    /** 현재 상태의 복사본. */
    suspend fun snapshot(): Map<Key, Versioned<String>> = TODO("L3-01")
}
