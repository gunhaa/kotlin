package com.kotlin.workbook.answer.level3

import com.kotlin.workbook.answer.level2.Key
import com.kotlin.workbook.answer.level2.NodeId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Level 3 테스트. 이름은 반드시 `L3-XX`로 시작한다.
 *
 * 모든 테스트는 `runTest` 위에서 돈다. `runTest`는 `delay`를 실제로 기다리지 않고 건너뛰면서
 * (가상 시간) 상대적 실행 순서를 보존하므로, "1초 지연되는 느린 노드"를 두고도 테스트는
 * 즉시 끝나고 `currentTime`으로 논리적 경과 시간을 검증할 수 있다.
 *
 * 요구사항 목록: `docs/workbook/level3.md`
 */
class Level3Test {

    @Test
    fun `L3-01 노드는 쓴 값을 그대로 돌려준다`() = runTest {
        val node = StoreNode(NodeId("node-0"))
        val key = Key("user:1")

        val ack = node.handle(NodeMessage.Write(key, "alice", version = 1))
        assertEquals(NodeResponse.WriteAck(node.id, key, 1, applied = true), ack)

        val read = node.handle(NodeMessage.Read(key)) as NodeResponse.ReadResult
        assertEquals("alice", read.value?.value)
        assertEquals(1L, read.value?.version)
    }

    // TODO: L3-02 ~ L3-19를 여기에 작성한다.
}
